package com.romanianguy.klover.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.marks.MarkKind
import com.romanianguy.klover.data.marks.MarkQueue
import com.romanianguy.klover.data.marks.PendingMark
import com.romanianguy.klover.data.model.GroupedSeriesDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.repo.LibraryRepository
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val onDeck: Loadable<List<SeriesDto>> = Loadable.Loading,
    val recentlyUpdated: Loadable<List<GroupedSeriesDto>> = Loadable.Loading,
    val recentlyAdded: Loadable<List<SeriesDto>> = Loadable.Loading,
    val refreshing: Boolean = false,
)

class HomeViewModel(
    private val repo: LibraryRepository,
    private val marks: MarkQueue,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private var shown = false

    // coming back to Home (e.g. from the reader): refresh the rails in place so progress bars are current
    fun onShown() {
        if (shown) load(initial = true) else shown = true
    }

    // marks a series read / unread from a card's menu, then reloads the rails so progress bars match
    fun markSeries(seriesId: Int, read: Boolean) {
        viewModelScope.launch {
            marks.mark(PendingMark(MarkKind.Series, seriesId, 0, read))
            load(initial = false)
        }
    }

    private var loadJob: Job? = null

    // starting a load drops one still running: an older answer arriving last must not replace the newer, nor end
    // "refreshing" early
    private fun load(initial: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!initial) _state.update { it.copy(refreshing = true) }
            // Each rail loads and fails on its own, so one slow endpoint never blanks the whole screen.
            val deck = async { loadable { repo.onDeck() }.also { r -> _state.update { it.copy(onDeck = r) } } }
            val updated = async { loadable { repo.recentlyUpdated() }.also { r -> _state.update { it.copy(recentlyUpdated = r) } } }
            val added = async { loadable { repo.recentlyAdded() }.also { r -> _state.update { it.copy(recentlyAdded = r) } } }
            deck.await(); updated.await(); added.await()
            _state.update { it.copy(refreshing = false) }
        }
    }
}
