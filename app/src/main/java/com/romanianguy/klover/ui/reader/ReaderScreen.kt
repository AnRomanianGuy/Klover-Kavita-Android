    package com.romanianguy.klover.ui.reader

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.chapters.innerChapterAt
import com.romanianguy.klover.data.files.PdfCache
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.data.prefs.DoubleTapZoom
import com.romanianguy.klover.data.prefs.PageMode
import com.romanianguy.klover.data.prefs.PageTurn
import com.romanianguy.klover.data.prefs.PrefKeys
import com.romanianguy.klover.data.prefs.ReaderBackground
import com.romanianguy.klover.data.prefs.ReaderOrientation
import com.romanianguy.klover.data.prefs.ReadingDirection
import com.romanianguy.klover.data.prefs.ReadingStyle
import com.romanianguy.klover.ui.common.ConnectedChoiceGroup
import com.romanianguy.klover.ui.common.ErrorState
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.PageSheet
import com.romanianguy.klover.ui.common.loadable
import com.romanianguy.klover.ui.common.rememberBookInset
import com.romanianguy.klover.ui.reader.book.BookReaderContent
import com.romanianguy.klover.ui.reader.book.palette
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    chapterId: Int,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startPage: Int = -1,
    viewModel: ReaderViewModel = koinViewModel(key = "reader-$chapterId-$startPage") { parametersOf(ReaderArgs(chapterId, startPage)) },
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    // reading time for the stats, while the reader is on screen and the app in front
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        fun start() = viewModel.sessionStarted()
        fun stop() = viewModel.sessionStopped()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    val isBook = (data as? Loadable.Ready)?.value?.format == ContentFormat.Epub
    val background = if (isBook) prefs.bookTheme.palette().background else prefs.readerBackground.color()

    Box(
        modifier
            .fillMaxSize()
            .background(background)
            // watches touches without consuming them: slow reading isn't a pause
            .pointerInput(viewModel) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        viewModel.noteActivity()
                    }
                }
            },
    ) {
        when (val state = data) {
            Loadable.Loading -> LoadingState()
            is Loadable.Failed -> ErrorState(
                message = stringResource(R.string.error_generic),
                onRetry = viewModel::reload,
            )

            is Loadable.Ready -> {
                val ready = state.value
                if (ready.format.isPaged) {
                    val urls = koinInject<ServerUrls>()
                    val source = remember(ready) { ImagePageSource(ready.chapterId, ready.info, urls, viewModel.localPageDir()) }
                    PagedReaderContent(ready, viewModel, source, onBack, onOpenChapter)
                } else if (ready.format == ContentFormat.Pdf) {
                    PdfReader(ready, viewModel, onBack, onOpenChapter)
                } else if (ready.format == ContentFormat.Epub) {
                    BookReaderContent(ready, viewModel, onBack, onOpenChapter)
                } else {
                    ErrorState(message = stringResource(R.string.reader_unsupported), onRetry = null)
                    BackHandler(onBack = onBack)
                }
            }
        }
        StreakCelebrationHost(viewModel, Modifier.align(Alignment.BottomCenter))
        SleepTimerHost(viewModel, onBack)
    }
}

@Composable
private fun PdfReader(
    ready: ReaderData,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val cache = koinInject<PdfCache>()
    val urls = koinInject<ServerUrls>()
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(ready.chapterId, attempt) { mutableStateOf<Loadable<PdfPageSource>>(Loadable.Loading) }

    LaunchedEffect(ready.chapterId, attempt) {
        state = loadable {
            // a downloaded PDF opens from its file, otherwise it's fetched into the cache
            val local = viewModel.localPdf()
            if (local != null) {
                PdfPageSource.open(local)
            } else {
                val url = urls.pdf(ready.chapterId) ?: error("Not signed in")
                PdfPageSource.open(cache.fetch(ready.chapterId, url))
            }
        }
    }
    val current = state
    DisposableEffect(current) { onDispose { (current as? Loadable.Ready)?.value?.close() } }

    when (current) {
        Loadable.Loading -> {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoadingState(Modifier.height(96.dp))
                Text(stringResource(R.string.reader_preparing), color = Color.White.copy(alpha = 0.7f))
            }
            BackHandler(onBack = onBack)
        }

        is Loadable.Failed -> {
            ErrorState(stringResource(R.string.error_generic), onRetry = { attempt++ })
            BackHandler(onBack = onBack)
        }

        is Loadable.Ready -> PagedReaderContent(ready, viewModel, current.value, onBack, onOpenChapter)
    }
}

@Composable
internal fun PagedReaderContent(
    ready: ReaderData,
    viewModel: ReaderViewModel,
    source: PageSource,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    if (prefs.readingStyle == ReadingStyle.Webtoon) {
        WebtoonReaderContent(ready, viewModel, source, onBack, onOpenChapter)
    } else {
        PagesReaderContent(ready, viewModel, source, onBack, onOpenChapter)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)
@Composable
private fun PagesReaderContent(
    ready: ReaderData,
    viewModel: ReaderViewModel,
    source: PageSource,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val layout = rememberReaderLayout(prefs.pageMode, prefs.tabletopMode)
    val tabletop = rememberTabletopMetrics(layout.tabletop)
    val rtl = prefs.readingDirection == ReadingDirection.RightToLeft
    val info = ready.info
    val scope = rememberCoroutineScope()

    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val spreads = remember(source, layout.dual, prefs.coverAlone, chapters) {
        buildSpreads(
            source.pageCount,
            layout.dual,
            prefs.coverAlone,
            isWide = source::isWide,
            breaks = chapters.map { it.start }.toSet(),
        )
    }

    // first page of the spread: the one stable "where am I" across fold and unfold
    var currentPage by rememberSaveable(ready.chapterId) { mutableIntStateOf(ready.startPage) }
    val pagerState = rememberPagerState(initialPage = spreads.indexOfPage(currentPage)) { spreads.size }

    // folding regroups the pages, stay on the one being shown
    LaunchedEffect(spreads) {
        val target = spreads.indexOfPage(currentPage)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState, spreads) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            spreads.getOrNull(index)?.let { currentPage = it.first }
        }
    }
    // leaving before the save below fires (last page, then Back) would lose the page, so save what's pending on the
    // way out
    val pendingLast by rememberUpdatedState(spreads.getOrNull(pagerState.settledPage)?.last)
    DisposableEffect(viewModel) { onDispose { pendingLast?.let(viewModel::saveProgress) } }
    LaunchedEffect(pagerState, spreads) {
        snapshotFlow { pagerState.settledPage }
            .debounce(600)
            .collect { index -> spreads.getOrNull(index)?.let { viewModel.saveProgress(it.last) } }
    }

    var overlayVisible by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    if (showTools) ReaderToolsSheet(viewModel, null) { showTools = false }
    ImmersiveMode(
        visible = overlayVisible,
        immersive = prefs.immersive,
        keepScreenOn = prefs.keepScreenOn,
        brightness = prefs.readerBrightness,
        orientation = prefs.readerOrientation,
    )

    val atEnd = pagerState.settledPage >= spreads.lastIndex
    SideEffect { viewModel.controlsShown = overlayVisible }

    val paceKind = viewModel.paceKindFor(ready.format)
    val lastPage = spreads.getOrNull(pagerState.currentPage)?.last
    // a multi-chapter file counts per chapter
    val inner = lastPage?.let { innerChapterAt(chapters, it) }
    val chapterStart = inner?.start ?: 0
    val chapterEnd = inner?.endExclusive ?: source.pageCount
    val pagesLeft = lastPage?.let { chapterEnd - 1 - it + 0.5f }
    val chapterProgress = lastPage?.let { (it + 1f - chapterStart) / (chapterEnd - chapterStart).coerceAtLeast(1) } ?: 0f
    val pageText = spreads.getOrNull(pagerState.currentPage)?.let { pageLabel(it, source.pageCount) }.orEmpty()
    val labelText = inner?.title?.let { "$it \u00B7 $pageText" } ?: pageText
    ReaderForecast(viewModel, paceKind, pagesLeft, chapterProgress)
    val pageInsights: @Composable () -> Unit = {
        ReaderInsightsLine(
            viewModel = viewModel,
            kind = paceKind,
            unitsLeft = pagesLeft,
            scope = LeftScope.Chapter,
            progress = chapterProgress,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .volumeKeyPaging(
                enabled = prefs.volumeKeys,
                refocusOn = pagerState.settledPage,
                onPrevious = { scope.launch { turnPage(pagerState, spreads.size, -1, prefs.pageTurn) } },
                onNext = { scope.launch { turnPage(pagerState, spreads.size, +1, prefs.pageTurn) } },
            ),
    ) {
        Box(if (tabletop != null) Modifier.fillMaxWidth().height(tabletop.pageHeight) else Modifier.fillMaxSize()) {
        SpreadPager(
            pagerState = pagerState,
            spreads = spreads,
            source = source,
            layout = layout,
            direction = prefs.readingDirection,
            onCenterTap = { overlayVisible = !overlayVisible },
            tapZones = prefs.tapZones,
            invertTapZones = prefs.invertTapZones,
            pageTurn = prefs.pageTurn,
            tapEdge = prefs.tapZoneSize.edge,
            preloadScreens = prefs.preloadScreens,
            doubleTapScale = prefs.doubleTapZoom.scale,
        )
        }

        if (tabletop != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tabletop.panelHeight)) {
                TabletopPanel(
                    title = { ReaderTitle(info) },
                    label = labelText,
                    index = pagerState.currentPage,
                    lastIndex = spreads.lastIndex,
                    rtl = rtl,
                    onSeek = { index -> scope.launch { pagerState.scrollToPage(index) } },
                    onPrevious = { scope.launch { turnPage(pagerState, spreads.size, -1, prefs.pageTurn) } },
                    onNext = { scope.launch { turnPage(pagerState, spreads.size, +1, prefs.pageTurn) } },
                    previousChapterId = ready.previousChapterId,
                    nextChapterId = ready.nextChapterId,
                    onOpenChapter = onOpenChapter,
                    onBack = onBack,
                    onSettings = { showSettings = true },
                    actions = {
                        BookmarkButton(viewModel, currentPage)
                        ReaderToolsButton(viewModel) { showTools = true }
                        ChaptersButton(chapters) { showChapters = true }
                    },
                    insights = pageInsights,
                )
            }
        }

        PageNumberBadge(
            text = spreads.getOrNull(pagerState.currentPage)?.let { pageLabel(it, source.pageCount) }.orEmpty(),
            visible = prefs.showPageNumber && !overlayVisible && tabletop == null,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = overlayVisible && tabletop == null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                title = { ReaderTitle(info) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    BookmarkButton(viewModel, currentPage)
                    ReaderToolsButton(viewModel) { showTools = true }
                    ChaptersButton(chapters) { showChapters = true }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.reader_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                ),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = atEnd && tabletop == null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                val pillGap by animateDpAsState(
                    if (overlayVisible) 8.dp else 24.dp,
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "pill-gap",
                )
                val endPill: @Composable () -> Unit = {
                    EndOfChapter(
                        nextChapterId = ready.nextChapterId,
                        onOpenNext = onOpenChapter,
                        modifier = Modifier.padding(bottom = pillGap),
                    )
                }
                val bookHinge = if (layout.dual) layout.hinge else null
                // book posture: centred on the page the book ends on, never across the fold
                val pillInset = rememberBookInset(bookHinge)
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(pillInset))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { endPill() }
                }
            }
            // the controls grow and shrink, so the pill rides with them instead of jumping
            AnimatedVisibility(
                visible = overlayVisible && tabletop == null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Bottom),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.Bottom),
            ) {
                BottomControls(
                    label = labelText,
                    currentIndex = pagerState.currentPage,
                    lastIndex = spreads.lastIndex,
                    rtl = rtl,
                    previousChapterId = ready.previousChapterId,
                    nextChapterId = ready.nextChapterId,
                    onSeek = { index -> scope.launch { pagerState.scrollToPage(index) } },
                    onOpenChapter = onOpenChapter,
                    insights = pageInsights,
                    hinge = if (layout.dual) layout.hinge else null,
                )
            }
        }
    }

    if (showChapters) {
        PageSheet(onDismissRequest = { showChapters = false }) {
            ChapterList(chapters, lastPage ?: 0) { chapter ->
                showChapters = false
                scope.launch { pagerState.scrollToPage(spreads.indexOfFirst { chapter.start in it.pages }.coerceAtLeast(0)) }
            }
        }
    }
    if (showSettings) {
        PageSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettingsSheet(
                style = prefs.readingStyle,
                onStyle = { viewModel.setPrefEnum(PrefKeys.READING_STYLE, it) },
                direction = prefs.readingDirection,
                pageMode = prefs.pageMode,
                dualActive = layout.dual,
                pageTurn = prefs.pageTurn,
                coverAlone = prefs.coverAlone,
                onDirection = viewModel::setReadingDirection,
                onPageMode = viewModel::setPageMode,
                onPageTurn = { viewModel.setPrefEnum(PrefKeys.PAGE_TURN, it) },
                onCoverAlone = { viewModel.setPref(PrefKeys.COVER_ALONE, it) },
                zoom = prefs.doubleTapZoom,
                onZoom = { viewModel.setPrefEnum(PrefKeys.DOUBLE_TAP_ZOOM, it) },
                remembered = !viewModel.seriesPrefs.collectAsStateWithLifecycle().value.isEmpty,
                onForget = viewModel::forgetSeriesPrefs,
            )
        }
    }
}

@Composable
internal fun ReaderTitle(info: ChapterInfoDto) {
    Column {
        if (LocalAppSettings.current.incognito) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    stringResource(R.string.incognito_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = info.seriesName.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = info.title?.takeIf { it.isNotBlank() } ?: info.chapterTitle.orEmpty()
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BottomControls(
    label: String,
    currentIndex: Int,
    lastIndex: Int,
    rtl: Boolean,
    previousChapterId: Int?,
    nextChapterId: Int?,
    onSeek: (Int) -> Unit,
    onOpenChapter: (Int) -> Unit,
    insights: @Composable () -> Unit = {},
    hinge: Rect? = null,
) {
    // one bar for both postures: flat it spans the window, as a book it sits on the right page and its left edge
    // slides
    BookBottomControls(
        label,
        currentIndex,
        lastIndex,
        rtl,
        previousChapterId,
        nextChapterId,
        onSeek,
        onOpenChapter,
        insights,
        hinge,
    )
}

@Composable
private fun BookBottomControls(
    label: String,
    currentIndex: Int,
    lastIndex: Int,
    rtl: Boolean,
    previousChapterId: Int?,
    nextChapterId: Int?,
    onSeek: (Int) -> Unit,
    onOpenChapter: (Int) -> Unit,
    insights: @Composable () -> Unit,
    hinge: Rect?,
) {
    val inset = rememberBookInset(hinge)
    val colour = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
    val shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(inset))
        Surface(Modifier.weight(1f), color = colour, shape = shape) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                insights()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previous = if (rtl) nextChapterId else previousChapterId
                    val next = if (rtl) previousChapterId else nextChapterId
                    ChapterButton(previous, forward = rtl, onOpenChapter)
                    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        Slider(
                            value = currentIndex.toFloat(),
                            onValueChange = { onSeek(it.roundToInt()) },
                            valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ChapterButton(next, forward = !rtl, onOpenChapter)
                }
            }
        }
    }
}

@Composable
internal fun ChapterButton(chapterId: Int?, forward: Boolean, onOpen: (Int) -> Unit) {
    IconButton(onClick = { chapterId?.let(onOpen) }, enabled = chapterId != null) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Filled.NavigateNext else Icons.AutoMirrored.Filled.NavigateBefore,
            contentDescription = stringResource(if (forward) R.string.reader_next_chapter else R.string.reader_previous_chapter),
        )
    }
}

@Composable
internal fun EndOfChapter(
    nextChapterId: Int?,
    onOpenNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
    ) {
        // with a button the text gets the roomy side; alone it's centred
        val hasButton = nextChapterId != null
        Row(
            Modifier.padding(
                start = 20.dp,
                end = if (hasButton) 8.dp else 20.dp,
                top = if (hasButton) 8.dp else 14.dp,
                bottom = if (hasButton) 8.dp else 14.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (nextChapterId != null) R.string.reader_chapter_finished else R.string.reader_series_finished),
                style = MaterialTheme.typography.titleSmall,
            )
            if (nextChapterId != null) {
                Button(onClick = { onOpenNext(nextChapterId) }) {
                    Text(stringResource(R.string.reader_next_chapter))
                }
            }
        }
    }
}

@Composable
internal fun ReaderSettingsSheet(
    style: ReadingStyle,
    onStyle: (ReadingStyle) -> Unit,
    direction: ReadingDirection,
    pageMode: PageMode,
    dualActive: Boolean,
    pageTurn: PageTurn,
    coverAlone: Boolean,
    onDirection: (ReadingDirection) -> Unit,
    onPageMode: (PageMode) -> Unit,
    onPageTurn: (PageTurn) -> Unit,
    onCoverAlone: (Boolean) -> Unit,
    zoom: DoubleTapZoom,
    onZoom: (DoubleTapZoom) -> Unit,
    remembered: Boolean,
    onForget: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_remembered_hint),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.animation.AnimatedVisibility(visible = remembered) {
                TextButton(onClick = onForget) { Text(stringResource(R.string.reader_forget_series)) }
            }
        }
        Text(stringResource(R.string.reader_style), style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup(ReadingStyle.entries, style, onStyle) {
            Text(stringResource(if (it == ReadingStyle.Pages) R.string.reader_style_pages else R.string.reader_style_webtoon), maxLines = 1)
        }
        if (style == ReadingStyle.Webtoon) {
            Text(
                stringResource(R.string.reader_style_webtoon_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DoubleTapZoomChoice(zoom, onZoom)
            return@Column
        }

        Text(stringResource(R.string.reader_direction), style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup(ReadingDirection.entries, direction, onDirection) { option ->
            Text(
                stringResource(if (option == ReadingDirection.LeftToRight) R.string.reader_ltr else R.string.reader_rtl),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(4.dp))
        PageModeSelector(pageMode, dualActive, onPageMode)

        Text(stringResource(R.string.reader_page_turn), style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup(PageTurn.entries, pageTurn, onPageTurn) {
            Text(
                stringResource(
                    when (it) {
                        PageTurn.Slide -> R.string.reader_turn_slide
                        PageTurn.Fade -> R.string.reader_turn_fade
                        PageTurn.Instant -> R.string.reader_turn_instant
                        PageTurn.Flip -> R.string.reader_turn_flip
                    },
                ),
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_cover_alone),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = coverAlone, onCheckedChange = onCoverAlone)
        }
        DoubleTapZoomChoice(zoom, onZoom)
    }
}

@Composable
private fun DoubleTapZoomChoice(zoom: DoubleTapZoom, onZoom: (DoubleTapZoom) -> Unit) {
    Text(stringResource(R.string.reader_double_tap), style = MaterialTheme.typography.titleSmall)
    ConnectedChoiceGroup(DoubleTapZoom.entries, zoom, onZoom) {
        Text(
            stringResource(
                when (it) {
                    DoubleTapZoom.Gentle -> R.string.zoom_gentle
                    DoubleTapZoom.Normal -> R.string.zoom_normal
                    DoubleTapZoom.Close -> R.string.zoom_close
                },
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun PageNumberBadge(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible && text.isNotBlank(),
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun ReaderBackground.color(): Color = when (this) {
    ReaderBackground.Black -> Color.Black
    ReaderBackground.Dark -> Color(0xFF1B1B1F)
    ReaderBackground.Paper -> Color(0xFFEFEBE0)
    ReaderBackground.Theme -> MaterialTheme.colorScheme.background
}

internal fun pageLabel(spread: Spread, pageCount: Int): String =
    if (spread.isDouble) "${spread.first + 1}–${spread.last + 1} / $pageCount" else "${spread.first + 1} / $pageCount"

@Composable
internal fun ImmersiveMode(
    visible: Boolean,
    immersive: Boolean = true,
    keepScreenOn: Boolean = true,
    brightness: Float = 0f,
    orientation: ReaderOrientation = ReaderOrientation.Auto,
) {
    val view = LocalView.current
    val activity = LocalActivity.current
    val window = activity?.window

    // brightness and orientation are put back when the reader is left
    DisposableEffect(brightness, window) {
        if (window != null && brightness >= 0.05f) {
            window.attributes = window.attributes.also { it.screenBrightness = brightness }
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
    DisposableEffect(orientation, activity) {
        activity?.requestedOrientation = when (orientation) {
            ReaderOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            ReaderOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(visible, immersive, window) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible || !immersive) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(window) {
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

@Composable
internal fun PageModeSelector(
    pageMode: PageMode,
    dualActive: Boolean,
    onPageMode: (PageMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reader_page_mode), style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup(PageMode.entries, pageMode, onPageMode) { option ->
            Text(
                stringResource(
                    when (option) {
                        PageMode.Auto -> R.string.reader_mode_auto
                        PageMode.Single -> R.string.reader_mode_single
                        PageMode.Double -> R.string.reader_mode_double
                    },
                ),
                maxLines = 1,
            )
        }
        Text(
            text = stringResource(if (dualActive) R.string.reader_showing_two else R.string.reader_showing_one),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
