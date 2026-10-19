package com.romanianguy.klover.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.chapters.InnerChapter
import com.romanianguy.klover.data.chapters.InnerReadMarks
import com.romanianguy.klover.data.chapters.Whole
import com.romanianguy.klover.data.chapters.detectInnerChapters
import com.romanianguy.klover.data.lookup.ChapterLookup
import com.romanianguy.klover.data.lookup.estimateInnerChapters
import com.romanianguy.klover.data.marks.MarkKind
import com.romanianguy.klover.data.marks.MarkQueue
import com.romanianguy.klover.data.marks.MarkResult
import com.romanianguy.klover.data.marks.PendingMark
import com.romanianguy.klover.data.marks.applyPending
import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.model.LibraryKind
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.model.SeriesMetadataDto
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.repo.LibraryRepository
import com.romanianguy.klover.data.repo.ReaderRepository
import com.romanianguy.klover.data.stats.ReadingPace
import com.romanianguy.klover.data.stats.estimatePace
import com.romanianguy.klover.data.stats.paceKey
import com.romanianguy.klover.data.stats.paceKindForSeries
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesUiState(
    val series: Loadable<SeriesDto> = Loadable.Loading,
    val detail: Loadable<SeriesDetailDto> = Loadable.Loading,
    val resume: ChapterDto? = null,
    val metadata: SeriesMetadataDto? = null,
    val wantToRead: Boolean? = null,
)

class SeriesViewModel(
    private val seriesId: Int,
    private val repo: LibraryRepository,
    private val reader: ReaderRepository,
    private val lookup: ChapterLookup,
    private val settings: SettingsStore,
    private val marks: InnerReadMarks,
    private val pace: ReadingPace,
    private val marksQueue: MarkQueue,
) : ViewModel() {
    val waitingToSync: StateFlow<Boolean> = marksQueue.pending
        .map { list -> list.any { it.seriesId == seriesId } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val libraryKind = MutableStateFlow<LibraryKind?>(null)

    val innerMarks: StateFlow<Map<String, Boolean>> = marks.marks

    internal fun markInner(
        file: ChapterDto,
        chapters: List<InnerChapter>,
        chapter: InnerChapter,
        read: Boolean,
        pagesRead: Int,
        // the server didn't take it, put the marks back
        onFailed: () -> Unit,
    ): Int {
        val before = marks.snapshot()
        val whole = marks.mark(file.id, chapters, chapter, read, pagesRead)
        val failed = { marks.restore(before); onFailed() }
        return when {
            whole == Whole.Read && pagesRead < file.pages -> { markChapter(file.id, true, failed); file.pages }
            whole == Whole.Unread && pagesRead > 0 -> { markChapter(file.id, false, failed); 0 }
            whole == Whole.Read -> file.pages
            whole == Whole.Unread -> 0
            else -> pagesRead
        }
    }

    private val inner = HashMap<Int, List<InnerChapter>>()

    internal suspend fun innerChaptersOf(tile: Tile): List<InnerChapter> {
        val file = tile.chapters.singleOrNull() ?: return emptyList()
        val detected = innerChapters(file.id)
        if (detected.isNotEmpty()) return detected
        val volume = tile.volumeOrder.takeIf { it >= 1f && it == it.toInt().toFloat() }?.toInt() ?: return emptyList()
        val s = settings.settings.first()
        if (!s.chapterLookup) return emptyList()
        val series = (_state.value.series as? Loadable.Ready)?.value ?: return emptyList()
        val titles = listOfNotNull(series.name, series.originalName, series.localizedName)
        val found = lookup.volumeChapters(titles, volume, network = !s.incognito) ?: return emptyList()
        return estimateInnerChapters(found, file.pages)
    }

    suspend fun innerChapters(chapterId: Int): List<InnerChapter> {
        inner[chapterId]?.let { return it }
        val found = runCatching { detectInnerChapters(reader.chapterInfo(chapterId)) }.getOrDefault(emptyList())
        inner[chapterId] = found
        return found
    }

    private val _state = MutableStateFlow(SeriesUiState())
    val state: StateFlow<SeriesUiState> = _state.asStateFlow()

    val secondsPerPage: StateFlow<Float?> = combine(
        pace.samples,
        state.map { (it.series as? Loadable.Ready)?.value }.distinctUntilChanged(),
        libraryKind,
    ) { samples, series, library ->
        val kind = series?.let { paceKindForSeries(ContentFormat.from(it.format), library) } ?: return@combine null
        estimatePace(kind, samples[kind.key], samples[paceKey(kind, seriesId)], null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
        // asked once, manga and comics are read at different speeds
        viewModelScope.launch {
            val series = (state.first { it.series is Loadable.Ready }.series as Loadable.Ready).value
            libraryKind.value = runCatching { repo.libraries() }.getOrNull()
                ?.firstOrNull { it.id == series.libraryId }?.let { LibraryKind.from(it.type) }
        }
    }

    fun reload() {
        _state.value = SeriesUiState()
        load()
    }

    private var shown = false

    // the first time the initial load runs, later (back from the reader) data refreshes in place without a loading
    // flash
    fun onShown() {
        if (shown) load() else shown = true
    }

    fun setWantToRead(add: Boolean) {
        viewModelScope.launch {
            val before = _state.value.wantToRead
            _state.update { it.copy(wantToRead = add) }
            if (runCatching { repo.setWantToRead(seriesId, add) }.isFailure) _state.update { it.copy(wantToRead = before) }
        }
    }

    fun markSeries(read: Boolean) = mark(PendingMark(MarkKind.Series, seriesId, 0, read))

    fun markVolume(volumeId: Int, read: Boolean) = mark(PendingMark(MarkKind.Volume, seriesId, volumeId, read))

    fun markChapter(chapterId: Int, read: Boolean, onFailed: () -> Unit = {}) =
        mark(PendingMark(MarkKind.Chapter, seriesId, chapterId, read), onFailed)

    private fun mark(mark: PendingMark, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            // Queued: the page shows it marked meanwhile
            if (marksQueue.mark(mark) == MarkResult.Failed) onFailed()
            load()
        }
    }

    private fun <T> keepIfFailed(old: Loadable<T>, new: Loadable<T>): Loadable<T> =
        if (new is Loadable.Failed && old is Loadable.Ready) old else new

    private var loadJob: Job? = null

    // a load still running when another starts is dropped, an older answer arriving last must not put old progress
    // back
    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val series = async {
                loadable { repo.series(seriesId) }.also { r -> _state.update { it.copy(series = keepIfFailed(it.series, r)) } }
            }
            val detail = async {
                loadable { applyPending(repo.seriesDetail(seriesId), seriesId, marksQueue.pending.value) }
                    .also { r -> _state.update { it.copy(detail = keepIfFailed(it.detail, r)) } }
            }
            val resume = async { runCatching { repo.continuePoint(seriesId) }.getOrNull() }
            val metadata = async { runCatching { repo.seriesMetadata(seriesId) }.getOrNull() }
            val shelf = async { runCatching { repo.isWantToRead(seriesId) }.getOrNull() }
            series.await(); detail.await()
            _state.update { it.copy(resume = resume.await(), metadata = metadata.await(), wantToRead = shelf.await()) }
        }
    }
}
