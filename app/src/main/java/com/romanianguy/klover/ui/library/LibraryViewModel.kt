package com.romanianguy.klover.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.marks.MarkKind
import com.romanianguy.klover.data.marks.MarkQueue
import com.romanianguy.klover.data.marks.MarkResult
import com.romanianguy.klover.data.marks.PendingMark
import com.romanianguy.klover.data.model.LibraryDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.model.TagItem
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.repo.FilterRef
import com.romanianguy.klover.data.repo.LibraryFilters
import com.romanianguy.klover.data.repo.LibraryRepository
import com.romanianguy.klover.data.repo.LibrarySort
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.loadable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val libraries: Loadable<List<LibraryDto>> = Loadable.Loading,
    // `null` selects "all libraries"
    val libraryId: Int? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Name,
    val filters: LibraryFilters = LibraryFilters(),
    val genres: List<TagItem> = emptyList(),
    val suggestions: List<FilterRef> = emptyList(),
    val items: List<SeriesDto> = emptyList(),
    val loadingFirstPage: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: Throwable? = null,
)

@OptIn(FlowPreview::class)
class LibraryViewModel(
    private val repo: LibraryRepository,
    private val wantToRead: Boolean = false,
    private val settings: SettingsStore? = null,
    private val marks: MarkQueue? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var page = 1
    private var pageJob: Job? = null
    private var suggestJob: Job? = null

    private var loadedQuery = ""

    init {
        viewModelScope.launch {
            val result = loadable { repo.libraries() }
            _state.update { it.copy(libraries = result) }
        }
        viewModelScope.launch {
            val genres = runCatching { repo.genres() }.getOrNull().orEmpty()
            _state.update { it.copy(genres = genres.sortedBy { g -> g.title.lowercase() }) }
        }
        // Typing is debounced; library / sort changes go through reload() directly.
        viewModelScope.launch {
            _state
                .map { it.query }
                .distinctUntilChanged()
                .drop(1)
                .debounce(350)
                .collect { query ->
                    if (query != loadedQuery) reload()
                    suggest(query)
                }
        }
        viewModelScope.launch {
            val sort = settings?.settings?.first()?.librarySort ?: LibrarySort.Name
            _state.update { it.copy(sort = sort) }
            reload()
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun selectLibrary(id: Int?) {
        if (_state.value.libraryId == id) return
        _state.update { it.copy(libraryId = id) }
        reload()
    }

    fun selectSort(sort: LibrarySort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun retry() = reload()

    fun toggleRef(ref: FilterRef) = changeFilters { it.toggle(ref) }

    fun toggleStatus(status: Int) = changeFilters { it.toggleStatus(status) }

    fun setUnreadOnly(on: Boolean) = changeFilters { it.copy(unreadOnly = on) }

    fun setMinRating(stars: Int) = changeFilters { it.copy(minRating = stars) }

    fun clearFilters() = changeFilters { LibraryFilters() }

    fun applySuggestion(ref: FilterRef) {
        suggestJob?.cancel()
        _state.update { it.copy(query = "", suggestions = emptyList(), filters = it.filters.copy(refs = it.filters.refs + ref)) }
        reload()
    }

    private fun changeFilters(change: (LibraryFilters) -> LibraryFilters) {
        val next = change(_state.value.filters)
        if (next == _state.value.filters) return
        _state.update { it.copy(filters = next) }
        reload()
    }

    // asks the server what else the text matches - people, genres, tags - without waiting for it to show the grid
    private fun suggest(query: String) {
        suggestJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            val found = runCatching { repo.search(query) }.getOrNull() ?: return@launch
            val refs = buildList {
                found.genres.take(PER_KIND).forEach { add(FilterRef.genre(it.id, it.title)) }
                found.tags.take(PER_KIND).forEach { add(FilterRef.tag(it.id, it.title)) }
                found.persons.distinctBy { it.id to it.role }.take(PER_KIND * 2).forEach { add(FilterRef.person(it.role, it.id, it.name)) }
            }
            _state.update { it.copy(suggestions = refs) }
        }
    }

    private var shown = false

    // back from the reader: re-fetch the loaded pages and swap them in, so progress updates without the grid jumping
    // to the top
    fun onShown() {
        if (!shown) {
            shown = true
            return
        }
        val s = _state.value
        if (s.loadingFirstPage || s.error != null) return
        val pages = page
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            val refreshed = loadable {
                (1..pages).flatMap { repo.series(s.libraryId, s.query, s.sort, it, wantToRead = wantToRead, filters = s.filters).items }
            }
            if (refreshed is Loadable.Ready) _state.update { it.copy(items = refreshed.value) }
        }
    }

    fun markSeries(seriesId: Int, read: Boolean) {
        viewModelScope.launch {
            val before = _state.value.items
            _state.update { s ->
                s.copy(items = s.items.map { if (it.id == seriesId) it.copy(pagesRead = if (read) it.pages else 0) else it })
            }
            val result = marks?.mark(PendingMark(MarkKind.Series, seriesId, 0, read))
                ?: if (runCatching { repo.markSeries(seriesId, read) }.isFailure) MarkResult.Failed else MarkResult.Done
            // Queued keeps the card as marked: it reaches the server when it can.
            if (result == MarkResult.Failed) _state.update { it.copy(items = before) }
        }
    }

    fun onWantToReadChanged(seriesId: Int, added: Boolean) {
        if (wantToRead && !added) _state.update { s -> s.copy(items = s.items.filterNot { it.id == seriesId }) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingFirstPage || s.loadingMore || s.endReached || s.error != null) return
        fetch(page + 1)
    }

    private fun reload() {
        page = 1
        loadedQuery = _state.value.query
        _state.update { it.copy(items = emptyList(), loadingFirstPage = true, loadingMore = false, endReached = false, error = null) }
        fetch(1)
    }

    private fun fetch(pageNumber: Int) {
        pageJob?.cancel()
        val s = _state.value
        if (pageNumber > 1) _state.update { it.copy(loadingMore = true) }
        pageJob = viewModelScope.launch {
            val result = loadable { repo.series(s.libraryId, s.query, s.sort, pageNumber, wantToRead = wantToRead, filters = s.filters) }
            when (result) {
                is Loadable.Ready -> {
                    page = pageNumber
                    _state.update {
                        it.copy(
                            items = if (pageNumber == 1) result.value.items else (it.items + result.value.items).distinctBy { series -> series.id },
                            loadingFirstPage = false,
                            loadingMore = false,
                            endReached = !result.value.hasMore || result.value.items.isEmpty(),
                        )
                    }
                }

                is Loadable.Failed -> _state.update {
                    it.copy(loadingFirstPage = false, loadingMore = false, error = result.error)
                }

                Loadable.Loading -> Unit
            }
        }
    }
}

private const val PER_KIND = 4
