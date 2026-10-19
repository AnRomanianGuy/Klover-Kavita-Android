package com.romanianguy.klover.ui.reader

import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.chapters.InnerChapter
import com.romanianguy.klover.data.chapters.detectInnerChapters
import com.romanianguy.klover.data.chapters.numberedTitle
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.downloads.LocalResource
import com.romanianguy.klover.data.garden.wasReadProperly
import com.romanianguy.klover.data.history.Bookmark
import com.romanianguy.klover.data.history.Bookmarks
import com.romanianguy.klover.data.history.HistoryEntry
import com.romanianguy.klover.data.history.LastRead
import com.romanianguy.klover.data.history.LastReadStore
import com.romanianguy.klover.data.history.ReadingHistory
import com.romanianguy.klover.data.lookup.ChapterLookup
import com.romanianguy.klover.data.lookup.estimateInnerChapters
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.model.LibraryKind
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.DoubleTapZoom
import com.romanianguy.klover.data.prefs.PageMode
import com.romanianguy.klover.data.prefs.PrefKeys
import com.romanianguy.klover.data.prefs.ReadingDirection
import com.romanianguy.klover.data.prefs.ReadingStyle
import com.romanianguy.klover.data.prefs.SeriesReaderPrefs
import com.romanianguy.klover.data.prefs.SeriesReaderStore
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.prefs.effectiveReaderSettings
import com.romanianguy.klover.data.repo.ReaderRepository
import com.romanianguy.klover.data.repo.TocEntry
import com.romanianguy.klover.data.stats.CHARS_PER_WORD
import com.romanianguy.klover.data.stats.CHECK_SPOTS
import com.romanianguy.klover.data.stats.CHECK_SPOT_SLACK
import com.romanianguy.klover.data.stats.Calibration
import com.romanianguy.klover.data.stats.LeftSmoother
import com.romanianguy.klover.data.stats.PaceKind
import com.romanianguy.klover.data.stats.PaceSample
import com.romanianguy.klover.data.stats.ReadingAccuracy
import com.romanianguy.klover.data.stats.ReadingPace
import com.romanianguy.klover.data.stats.ReadingStats
import com.romanianguy.klover.data.stats.StreakStatus
import com.romanianguy.klover.data.stats.addPace
import com.romanianguy.klover.data.stats.calibrate
import com.romanianguy.klover.data.stats.chapterPace
import com.romanianguy.klover.data.stats.estimatePace
import com.romanianguy.klover.data.stats.observedWordFactor
import com.romanianguy.klover.data.stats.paceKey
import com.romanianguy.klover.data.stats.streakStatus
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.loadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

data class ReaderArgs(val chapterId: Int, val startPage: Int = -1)

data class ReaderData(
    val chapterId: Int,
    val info: ChapterInfoDto,
    val format: ContentFormat,
    val startPage: Int,
    val nextChapterId: Int?,
    val previousChapterId: Int?,
    val chapters: List<InnerChapter> = emptyList(),
)

class ReaderViewModel(
    private val args: ReaderArgs,
    private val repo: ReaderRepository,
    private val settings: SettingsStore,
    private val appScope: CoroutineScope,
    private val downloads: DownloadManager,
    private val stats: ReadingStats,
    private val pace: ReadingPace,
    private val accuracy: ReadingAccuracy,
    private val seriesStore: SeriesReaderStore,
    private val lookup: ChapterLookup,
    private val lastRead: LastReadStore,
    private val history: ReadingHistory,
    private val bookmarks: Bookmarks,
) : ViewModel() {
    val bookmarksHere: StateFlow<List<Bookmark>> = bookmarks.items
        .map { list -> list.filter { it.chapterId == args.chapterId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleBookmark(page: Int): Boolean {
        val info = (_data.value as? Loadable.Ready)?.value?.info ?: return false
        return bookmarks.toggle(
            Bookmark(
                args.chapterId, info.seriesId, info.seriesName.orEmpty(), titleOf(info), page, "", System.currentTimeMillis(),
            ),
        )
    }

    fun setAutoScrollSpeed(speed: Int) {
        viewModelScope.launch { settings.set(PrefKeys.AUTO_SCROLL_SPEED, speed) }
    }

    fun setBookmarkNote(page: Int, note: String) = bookmarks.setNote(args.chapterId, page, note)

    private fun titleOf(info: ChapterInfoDto): String =
        info.chapterTitle?.takeIf { it.isNotBlank() } ?: info.title?.takeIf { it.isNotBlank() }
            ?: info.volumeNumber?.takeIf { it.isNotBlank() && it != "0" }?.let { numberedTitle("Volume", it) }
            ?: info.chapterNumber?.takeIf { it.isNotBlank() && it != "0" }?.let { numberedTitle("Chapter", it) }.orEmpty()

    private val _chapterFinished = MutableStateFlow(false)
    private val _sleepLeft = MutableStateFlow<Long?>(null)

    // milliseconds left on the sleep timer; null if none is set
    val sleepLeft: StateFlow<Long?> = _sleepLeft
    private val _sleepAtChapterEnd = MutableStateFlow(false)
    val sleepAtChapterEnd: StateFlow<Boolean> = _sleepAtChapterEnd
    private val _sleepDone = MutableStateFlow(false)

    val sleepDone: StateFlow<Boolean> = _sleepDone
    private var sleepJob: Job? = null

    fun startSleep(minutes: Int) {
        cancelSleep()
        val until = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            while (true) {
                val left = until - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    _sleepLeft.value = null
                    _sleepDone.value = true
                    break
                }
                _sleepLeft.value = left
                delay(1000)
            }
        }
    }

    fun sleepAtEndOfChapter() {
        cancelSleep()
        _sleepAtChapterEnd.value = true
        sleepJob = viewModelScope.launch {
            _chapterFinished.first { it }
            _sleepAtChapterEnd.value = false
            _sleepDone.value = true
        }
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepLeft.value = null
        _sleepAtChapterEnd.value = false
        _sleepDone.value = false
    }

    private val chapterId: Int = args.chapterId

    private val _chapters = MutableStateFlow<List<InnerChapter>>(emptyList())

    val chapters: StateFlow<List<InnerChapter>> = _chapters.asStateFlow()

    private var lastNoted = -1

    private val TOUCHES_TO_CONFIRM = 3
    private var lastStepAt = 0L
    private var finishedNoted = false

    private var openedAtPage = 0
    private var spentAtOpen = 0

    private var spentThisOpen = 0

    private fun noteReadingTime(seconds: Long) {
        spentThisOpen += seconds.toInt()
        if (!prefs.value.incognito) {
            stats.addSeconds(seconds)
            stats.flush()
            pace.flush()
            accuracy.addSpent(chapterId, seconds.toInt())
            accuracy.flush()
        }
    }

    private var sessionSince = 0L

    fun sessionStarted() {
        if (sessionSince == 0L) sessionSince = SystemClock.elapsedRealtime()
    }

    fun sessionStopped() {
        if (sessionSince != 0L) {
            noteReadingTime(((SystemClock.elapsedRealtime() - sessionSince) / 1000).coerceAtMost(3600))
            sessionSince = 0L
            leftTheApp = true
        }
    }

    private fun liveMillis(): Long =
        if (sessionSince == 0L) 0 else SystemClock.elapsedRealtime() - sessionSince

    private fun liveSeconds(): Long = liveMillis() / 1000

    private val _bookWords = MutableStateFlow(0L)

    val bookWords: StateFlow<Long> = _bookWords.asStateFlow()

    private var serverWords = 0L

    private fun publishWords() {
        _bookWords.value = (serverWords * accuracy.wordFactor()).toLong()
    }

    fun loadBookWords() {
        if (serverWords > 0) return
        accuracy.words(chapterId)?.let {
            serverWords = it.toLong()
            publishWords()
        }
        viewModelScope.launch {
            val fetched = repo.chapterWordCount(chapterId)
            if (fetched > 0) {
                serverWords = fetched
                if (!prefs.value.incognito) accuracy.setWords(
                    chapterId,
                    fetched.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
                publishWords()
            }
        }
    }

    // section lengths of a downloaded book, null if it isn't downloaded
    suspend fun downloadedBookSizes(sections: Int): List<Int>? =
        downloads.bookSizes(chapterId, sections, remember = !prefs.value.incognito)

    fun noteBookSections(sizes: Collection<Int>, sections: Int) {
        if (prefs.value.incognito) return
        observedWordFactor(sizes, sections, serverWords)?.let { accuracy.learnWordFactor(chapterId, it) }
    }

    private val sessionPace = MutableStateFlow<Map<String, PaceSample>>(emptyMap())
    private val paceFlows = HashMap<PaceKind, StateFlow<Float>>()

    private fun estimate(
        kind: PaceKind,
        all: Map<String, PaceSample>,
        session: Map<String, PaceSample>,
        seriesId: Int?,
    ) =
        estimatePace(kind, all[kind.key], seriesId?.let { all[paceKey(kind, it)] }, session[kind.key])

    fun secondsPerUnit(kind: PaceKind): StateFlow<Float> = paceFlows.getOrPut(kind) {
        combine(pace.samples, sessionPace, series) { all, session, key -> estimate(kind, all, session, key?.first) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(3_000),
                estimate(kind, pace.samples.value, sessionPace.value, series.value?.first),
            )
    }

    fun paceKindFor(format: ContentFormat): PaceKind {
        val library = series.value?.second?.let { LibraryKind.from(it) }
        return when {
            format == ContentFormat.Epub -> if (library == LibraryKind.LightNovel) PaceKind.Novel else PaceKind.Text
            prefs.value.readingStyle == ReadingStyle.Webtoon -> PaceKind.Strips
            format == ContentFormat.Pdf -> when (library) {
                LibraryKind.Manga -> PaceKind.Manga
                LibraryKind.Comic, LibraryKind.ComicVine -> PaceKind.Pages
                else -> PaceKind.Pdf
            }
            else -> if (library == LibraryKind.Manga) PaceKind.Manga else PaceKind.Pages
        }
    }

    private var lastUnitsLeft: Float? = null

    private var segmentSpent = 0
    private var segmentUnits = -1f
    private var segmentProgress = 0f

    private fun noteChapterPace(kind: PaceKind, seconds: Float, pages: Float) {
        addPace(kind, sessionPace.value[kind.key], seconds, pages)?.let { next -> sessionPace.update { it + (kind.key to next) } }
        pace.record(kind, seconds, pages, series.value?.first)
    }

    private val smoother = LeftSmoother()
    private var smoothKind: PaceKind? = null
    private val _smoothedLeft = MutableStateFlow<Long?>(null)

    val smoothedLeft: StateFlow<Long?> = _smoothedLeft.asStateFlow()

    private val calibrations = HashMap<PaceKind, StateFlow<Calibration>>()

    fun calibration(kind: PaceKind): StateFlow<Calibration> = calibrations.getOrPut(kind) {
        accuracy.data.map { calibrate(it.outcomes[kind.key]) }.distinctUntilChanged().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(3_000),
            calibrate(accuracy.data.value.outcomes[kind.key]),
        )
    }

    fun noteForecast(kind: PaceKind, rawSeconds: Long, unitsLeft: Float, progress: Float) {
        // a jump (slider, contents, going far back) invalidates waiting predictions
        val previous = lastUnitsLeft
        lastUnitsLeft = unitsLeft
        var jumped = false
        if (previous != null) {
            val text = kind == PaceKind.Text || kind == PaceKind.Novel
            val jump = if (text) maxOf(6_000f, previous * 0.2f) else 5f
            jumped = abs(unitsLeft - previous) > jump
        }
        if (smoothKind != kind || jumped) {
            smoother.reset()
            smoothKind = kind
        }
        _smoothedLeft.value = if (unitsLeft > 0f) smoother.next(unitsLeft, rawSeconds.toFloat()).toLong() else null
        if (prefs.value.incognito) return
        if (jumped) accuracy.dropChecks(chapterId)
        val spentNow = accuracy.spent(chapterId) + liveSeconds().toInt()
        // comics and manga read start to end teach whole-chapter pace
        if (kind != PaceKind.Text && kind != PaceKind.Novel) {
            if (jumped || segmentUnits < 0f) {
                segmentSpent = spentNow
                segmentUnits = unitsLeft
                segmentProgress = progress
            } else if (unitsLeft <= kind.finishUnits) {
                chapterPace(
                    segmentUnits,
                    unitsLeft,
                    segmentProgress,
                    (spentNow - segmentSpent).toFloat(),
                    secondsPerUnit(kind).value,
                )
                    ?.let { (seconds, pages) -> noteChapterPace(kind, seconds, pages) }
                segmentUnits = -1f
            }
        }
        if (unitsLeft <= kind.finishUnits) {
            accuracy.finish(chapterId, spentNow)
            return
        }
        val taken = accuracy.taken(chapterId)
        for ((spot, at) in CHECK_SPOTS.withIndex()) {
            if (taken and (1 shl spot) == 0 && progress >= at && progress < at + CHECK_SPOT_SLACK) {
                accuracy.check(chapterId, kind.key, rawSeconds.toInt(), spentNow, spot, unitsLeft.toInt())
            }
        }
    }

    fun notePace(kind: PaceKind, seconds: Float, units: Float) {
        // leaving the app mid-page is a pause, not slow reading
        val left = leftTheApp
        val busy = touches >= TOUCHES_TO_CONFIRM
        leftTheApp = false
        touches = 0
        if (left) return
        addPace(kind, sessionPace.value[kind.key], seconds, units, confirmed = busy)?.let { next ->
            sessionPace.update { it + (kind.key to next) }
        }
        if (!prefs.value.incognito) pace.record(kind, seconds, units, series.value?.first, confirmed = busy)
    }

    // touches mean reading, leaving the app means a pause
    private var touches = 0
    private var lastTouchAt = 0L
    private var leftTheApp = false

    fun noteActivity() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTouchAt > 1_000) {
            touches++
            lastTouchAt = now
        }
    }

    fun notePages(count: Int) {
        if (!prefs.value.incognito) stats.addPages(count)
    }

    private val _data = MutableStateFlow<Loadable<ReaderData>>(Loadable.Loading)
    val data: StateFlow<Loadable<ReaderData>> = _data.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = (data.first { it is Loadable.Ready } as Loadable.Ready).value
            _chapters.value = ready.chapters
            if (ready.chapters.isNotEmpty() || !ready.format.isPaged) return@launch
            val volume = ready.info.volumeNumber?.toFloatOrNull()
                ?.takeIf { it >= 1f && it == it.toInt().toFloat() }
                ?.toInt()
                ?: return@launch
            val s = settings.settings.first()
            if (!s.chapterLookup) return@launch
            val titles = listOfNotNull(ready.info.seriesName)
            val found = lookup.volumeChapters(titles, volume, network = !s.incognito) ?: return@launch
            estimateInnerChapters(found, ready.info.pages).takeIf { it.isNotEmpty() }?.let { _chapters.value = it }
        }
    }

    private val series = MutableStateFlow<Pair<Int, Int>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val seriesPrefs: StateFlow<SeriesReaderPrefs> = series
        .flatMapLatest { key -> if (key == null) flowOf(SeriesReaderPrefs()) else seriesStore.prefs(key.first) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SeriesReaderPrefs())

    val prefs: StateFlow<AppSettings> = combine(settings.settings, seriesPrefs, series) { general, remembered, key ->
        effectiveReaderSettings(general, remembered, key?.second)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _streakReached = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    val streakReached: SharedFlow<Int> = _streakReached.asSharedFlow()

    @Volatile
    var controlsShown = false

    private var wasDone: Boolean? = null

    val streak: StateFlow<StreakStatus?> = combine(
        stats.days,
        prefs.map { it.incognito }.distinctUntilChanged(),
        // ticks just after each whole second so the countdown doesn't skip
        flow { while (true) { emit(Unit); delay(1_000 - liveMillis() % 1_000 + 15) } },
    ) { days, incognito, _ ->
        if (incognito) {
            null
        } else {
            streakStatus(days, LocalDate.now(), liveSeconds()).let { if (it.days == 0) it.copy(secondsToKeep = 0) else it }
        }
    }.onEach { status ->
        if (status == null) {
            wasDone = null
        } else {
            if (wasDone == false && status.todayDone) _streakReached.tryEmit(status.days)
            wasDone = status.todayDone
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), null)

    private fun remember(change: (SeriesReaderPrefs) -> SeriesReaderPrefs) {
        val seriesId = series.value?.first ?: return
        viewModelScope.launch { seriesStore.update(seriesId, change) }
    }

    fun forgetSeriesPrefs() {
        val seriesId = series.value?.first ?: return
        viewModelScope.launch { seriesStore.clear(seriesId) }
    }

    private var lastSavedPage = -1

    init {
        load()
        viewModelScope.launch {
            val ready = (_data.first { it is Loadable.Ready } as? Loadable.Ready)?.value ?: return@launch
            if (settings.settings.first().incognito) return@launch
            val info = ready.info
            lastRead.set(
                LastRead(
                    chapterId,
                    info.seriesId,
                    info.seriesName.orEmpty(),
                    titleOf(info),
                    System.currentTimeMillis(),
                ),
            )
        }
        viewModelScope.launch {
            (_data.first { it is Loadable.Ready } as? Loadable.Ready)?.value?.let { downloads.planAhead(it.info, chapterId) }
        }
    }

    fun reload() {
        _data.value = Loadable.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _data.value = loadable {
                coroutineScope {
                    val local = downloads.completed(chapterId)
                    val info = downloads.info(chapterId) ?: repo.chapterInfo(chapterId)
                    series.value = info.seriesId to info.libraryType

                    suspend fun <T> reachable(block: suspend () -> T?): T? =
                        if (local == null) block() else withTimeoutOrNull(SERVER_GRACE_MS) { block() }

                    val progress = async { reachable { repo.progress(chapterId) } }
                    val next = async {
                        reachable { repo.nextChapter(info, chapterId) } ?: local?.let { downloads.neighbors(chapterId).second }
                    }
                    val prev = async {
                        reachable { repo.previousChapter(info, chapterId) } ?: local?.let { downloads.neighbors(chapterId).first }
                    }

                    // finished chapters restart; a position saved offline wins until it is synced
                    val serverRead = progress.await()?.pageNum
                    val savedPagesRead = when {
                        local != null && local.localPagesRead >= 0 && (local.pendingSync || serverRead == null) -> local.localPagesRead
                        else -> serverRead ?: 0
                    }
                    val start = args.startPage.takeIf { it in 0 until info.pages } ?: resumeIndex(savedPagesRead, info.pages)
                    lastSavedPage = savedPagesRead
                    lastNoted = savedPagesRead
                    openedAtPage = if (savedPagesRead >= info.pages) 0 else savedPagesRead
                    spentAtOpen = accuracy.spent(chapterId)
                    ReaderData(
                        chapterId = chapterId,
                        info = info,
                        format = ContentFormat.from(info.seriesFormat),
                        startPage = start,
                        nextChapterId = next.await(),
                        previousChapterId = prev.await(),
                        chapters = detectInnerChapters(info),
                    )
                }
            }
        }
    }

    // saves the last visible page (zero based). Kavita stores pages read, so index 5 saves as 6
    fun saveProgress(lastVisibleIndex: Int) {
        if (prefs.value.incognito) return
        val ready = (_data.value as? Loadable.Ready)?.value ?: return
        val pages = pagesRead(lastVisibleIndex, ready.info.pages)
        if (pages == lastSavedPage) return
        // a few pages forward is reading, a bigger jump is the slider
        val now = SystemClock.elapsedRealtime()
        if (ready.format != ContentFormat.Epub && lastNoted >= 0 && pages - lastNoted in 1..4) {
            stats.addPages(pages - lastNoted)
            if (lastStepAt > 0) notePace(
                paceKindFor(ready.format),
                (now - lastStepAt) / 1000f,
                (pages - lastNoted).toFloat(),
            )
        }
        lastStepAt = now
        lastNoted = pages
        if (!finishedNoted && ready.info.pages > 0 && pages >= ready.info.pages) {
            finishedNoted = true
            _chapterFinished.value = true
            // reaching the last page isn't reading: skimmed chapters are listed in history but grow no clover
            val read = wasReadProperly(
                spentSeconds = (spentAtOpen + spentThisOpen + liveSeconds().toInt()).toFloat(),
                expectedSeconds = expectedReadSeconds(ready),
            )
            if (read) stats.addChapter()
            history.add(
                HistoryEntry(
                    chapterId,
                    ready.info.seriesId,
                    ready.info.seriesName.orEmpty(),
                    titleOf(ready.info),
                    System.currentTimeMillis(),
                    ready.info.pages,
                    read = read,
                ),
            )
        }
        lastSavedPage = pages
        val onDevice = downloads.completed(ready.chapterId) != null
        // save locally first so the position survives an unreachable server
        if (onDevice) downloads.recordProgress(ready.chapterId, pages, pendingSync = true)
        appScope.launch {
            val saved = runCatching { repo.saveProgress(ready.info, ready.chapterId, pages) }.isSuccess
            if (onDevice && saved) downloads.recordProgress(ready.chapterId, pages, pendingSync = false)
        }
    }

    private fun expectedReadSeconds(ready: ReaderData): Float? {
        val kind = paceKindFor(ready.format)
        // a habitual flicker drags their own pace down, so floor it at half the default
        val perUnit = maxOf(secondsPerUnit(kind).value, kind.defaultSecondsPerUnit * 0.5f)
        if (kind == PaceKind.Text || kind == PaceKind.Novel) {
            val words = bookWords.value
            if (words <= 0L || (openedAtPage > 0 && spentAtOpen == 0)) return null
            return words * CHARS_PER_WORD * perUnit
        }
        val alreadyRead = if (spentAtOpen == 0) openedAtPage else 0
        return (ready.info.pages - alreadyRead).coerceAtLeast(0) * perUnit
    }

    override fun onCleared() {
        downloads.sweepRead()
    }

    fun setReadingDirection(direction: ReadingDirection) = remember { it.copy(direction = direction) }

    suspend fun bookSection(section: Int): String =
        downloads.bookSection(chapterId, section) ?: repo.bookPage(chapterId, section)

    suspend fun bookToc(): List<TocEntry> = downloads.bookToc(chapterId) ?: repo.bookToc(chapterId)

    fun localPageDir(): File? = downloads.pageDir(chapterId)

    fun localPdf(): File? = downloads.pdfFile(chapterId)

    fun localBookResource(rawQuery: String?): LocalResource? = downloads.bookResource(chapterId, rawQuery)

    fun setBookFontScale(scale: Float) {
        viewModelScope.launch { settings.setBookFontScale(scale) }
    }

    fun <T> setPref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { settings.set(key, value) }
    }

    fun <E : Enum<E>> setPrefEnum(key: Preferences.Key<String>, value: E) {
        when {
            key == PrefKeys.READING_STYLE && value is ReadingStyle -> remember { it.copy(style = value) }
            key == PrefKeys.DOUBLE_TAP_ZOOM && value is DoubleTapZoom -> remember { it.copy(zoom = value) }
            else -> viewModelScope.launch { settings.setEnum(key, value) }
        }
    }

    fun setPageMode(mode: PageMode) = remember { it.copy(pageMode = mode) }

    private companion object {
        // how long a downloaded chapter waits for the server before opening without it
        const val SERVER_GRACE_MS = 3_000L
    }
}

