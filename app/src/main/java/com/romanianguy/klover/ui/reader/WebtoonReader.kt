package com.romanianguy.klover.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.chapters.innerChapterAt
import com.romanianguy.klover.data.prefs.PrefKeys
import com.romanianguy.klover.data.stats.PaceKind
import com.romanianguy.klover.ui.common.PageSheet
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// a readable column on wide screens
private val STRIP_MAX_WIDTH = 840.dp

private const val PRELOAD_PAGES = 4

private const val VOLUME_SCROLL = 0.85f

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun WebtoonReaderContent(
    ready: ReaderData,
    viewModel: ReaderViewModel,
    source: PageSource,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    key(ready.chapterId) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val count = source.pageCount
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ready.startPage.coerceIn(0, (count - 1).coerceAtLeast(0)))

    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex.coerceAtMost((count - 1).coerceAtLeast(0)) } }
    // null until laid out, so the first empty frame isn't read as "read nothing"
    val lastVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index < count }?.index }
    }

    LaunchedEffect(listState) {
        snapshotFlow { lastVisible }.filterNotNull().debounce(600).collect { viewModel.saveProgress(it) }
    }
    // leaving within the 600 ms mustn't lose the page
    val pendingLast by rememberUpdatedState(lastVisible)
    DisposableEffect(viewModel) { onDispose { pendingLast?.let(viewModel::saveProgress) } }
    LaunchedEffect(listState, source) {
        snapshotFlow { lastVisible }.filterNotNull().collect { last ->
            for (offset in 1..PRELOAD_PAGES) source.prefetch(context, last + offset)
        }
    }

    var overlayVisible by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    // auto-scroll; a finger or the end of the strip stops it
    var autoScrolling by rememberSaveable { mutableStateOf(false) }
    val autoSpeed = prefs.autoScrollSpeed
    val autoDensity = LocalDensity.current.density
    LaunchedEffect(autoScrolling, autoSpeed, listState) {
        if (!autoScrolling) return@LaunchedEffect
        val pxPerSecond = autoDensity * when (autoSpeed) { 1 -> 40f; 2 -> 90f; else -> 160f }
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            listState.scrollBy(pxPerSecond * seconds)
            if (!listState.canScrollForward) {
                autoScrolling = false
                break
            }
        }
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) autoScrolling = false }
    }
    if (showTools) {
        ReaderToolsSheet(
            viewModel,
            AutoScrollControls(
                on = autoScrolling,
                speed = autoSpeed,
                onToggle = { autoScrolling = it },
                onSpeed = viewModel::setAutoScrollSpeed,
            ),
        ) { showTools = false }
    }
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    SideEffect { viewModel.controlsShown = overlayVisible }
    ImmersiveMode(
        visible = overlayVisible,
        immersive = prefs.immersive,
        keepScreenOn = prefs.keepScreenOn,
        brightness = prefs.readerBrightness,
        orientation = prefs.readerOrientation,
    )

    val zoom = remember { StripZoom() }
    // a multi-chapter file counts per chapter
    val innerFirst = innerChapterAt(chapters, firstVisible)
    val innerLast = lastVisible?.let { innerChapterAt(chapters, it) }
    val chapterEnd = innerLast?.endExclusive ?: count
    val chapterStart = innerLast?.start ?: 0
    val label = innerFirst?.let { "${it.title} \u00B7 ${firstVisible + 1} / $count" } ?: "${firstVisible + 1} / $count"
    fun scrollScreen(direction: Int) {
        val height = listState.layoutInfo.viewportSize.height
        scope.launch { listState.animateScrollBy(direction * height * VOLUME_SCROLL, spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .volumeKeyPaging(
                enabled = prefs.volumeKeys,
                refocusOn = overlayVisible,
                onPrevious = { scrollScreen(-1) },
                onNext = { scrollScreen(+1) },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .stripZoom(
                    zoom,
                    doubleTapScale = prefs.doubleTapZoom.scale,
                    isScrolling = { listState.isScrollInProgress },
                ) { overlayVisible = !overlayVisible },
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = STRIP_MAX_WIDTH),
            ) {
                items(count, key = { it }) { index -> source.StripPage(index, Modifier.fillMaxWidth()) }
                item(key = "end") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EndOfChapter(nextChapterId = ready.nextChapterId, onOpenNext = onOpenChapter)
                    }
                }
            }
        }

        PageNumberBadge(
            text = label,
            visible = prefs.showPageNumber && !overlayVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = overlayVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                title = { ReaderTitle(ready.info) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    BookmarkButton(viewModel, firstVisible)
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

        ReaderForecast(
            viewModel,
            PaceKind.Strips,
            lastVisible?.let { chapterEnd - 1 - it + 0.5f },
            lastVisible?.let { (it + 1f - chapterStart) / (chapterEnd - chapterStart).coerceAtLeast(1) } ?: 0f,
        )
        AnimatedVisibility(
            visible = overlayVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderInsightsLine(
                viewModel = viewModel,
                kind = PaceKind.Strips,
                unitsLeft = lastVisible?.let { chapterEnd - 1 - it + 0.5f },
                scope = LeftScope.Chapter,
                progress = lastVisible?.let { (it + 1f - chapterStart) / (chapterEnd - chapterStart).coerceAtLeast(1) } ?: 0f,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp),
            )
        }

        WebtoonProgress(
            listState = listState,
            source = source,
            count = count,
            overlayVisible = overlayVisible,
            label = label,
            previousChapterId = ready.previousChapterId,
            nextChapterId = ready.nextChapterId,
            onOpenChapter = onOpenChapter,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showChapters) {
        PageSheet(onDismissRequest = { showChapters = false }) {
            ChapterList(chapters, firstVisible) { chapter ->
                showChapters = false
                scope.launch { listState.scrollToItem(chapter.start) }
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
                dualActive = false,
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
}

private const val MAX_STRIP_ZOOM = 3.5f
private const val DOUBLE_TAP_STRIP_ZOOM = 2f

@Stable
internal class StripZoom {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var width by mutableIntStateOf(0)

    val isZoomed: Boolean get() = scale > 1.01f

    fun clampX(x: Float, forScale: Float = scale): Float {
        val max = width * (forScale - 1f) / 2f
        return x.coerceIn(-max, max)
    }

    suspend fun toggle(zoomTo: Float) {
        val from = scale
        val to = if (isZoomed) 1f else zoomTo
        animate(
            from,
            to,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
            scale = value
            offsetX = clampX(offsetX, value)
        }
    }
}

// pinch or double-tap zooms; one-finger drags are left to the list
@Composable
internal fun Modifier.stripZoom(
    state: StripZoom,
    doubleTapScale: Float = DOUBLE_TAP_STRIP_ZOOM,
    isScrolling: () -> Boolean = { false },
    onTap: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .onSizeChanged {
            state.width = it.width
            // size changed while zoomed, the old shift can show empty space
            state.offsetX = state.clampX(state.offsetX)
        }
        .pointerInput(state) {
            // touching to stop a fling isn't a tap
            var stoppedAFling = false
            detectTapGestures(
                onPress = { stoppedAFling = isScrolling() },
                onTap = { if (!stoppedAFling) onTap() },
                onDoubleTap = { scope.launch { state.toggle(doubleTapScale) } },
            )
        }
        .pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointers = event.changes.count { it.pressed }
                    if (pointers >= 2) {
                        val newScale = (state.scale * event.calculateZoom()).coerceIn(1f, MAX_STRIP_ZOOM)
                        state.scale = newScale
                        state.offsetX = state.clampX(state.offsetX + event.calculatePan().x, newScale)
                        event.changes.forEach { if (it.positionChanged() && !it.changedToUp()) it.consume() }
                    } else if (state.isZoomed) {
                        state.offsetX = state.clampX(state.offsetX + event.calculatePan().x)
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offsetX
        }
}

private val THUMB_HEIGHT = 64.dp
private const val IDLE_RAIL_ALPHA = 0.4f

private val RAIL_TOP = 72.dp
private val RAIL_BOTTOM = 24.dp
private val PANEL_WIDTH = 76.dp
private val PANEL_MARGIN = 8.dp
private val PANEL_PAD = 12.dp
private val PANEL_LABEL = 28.dp
private val PANEL_BUTTON = 48.dp
private val PANEL_TRACK_GAP = 8.dp

private class ProgressGeometry(val card: Rect, val sliderTrack: Rect, val rail: Rect)

private fun progressGeometry(size: Size, density: Float, insetTop: Float, insetBottom: Float): ProgressGeometry {
    val top = insetTop + RAIL_TOP.value * density
    val bottom = size.height - insetBottom - RAIL_BOTTOM.value * density
    val card = Rect(
        size.width - (PANEL_MARGIN + PANEL_WIDTH).value * density,
        top,
        size.width - PANEL_MARGIN.value * density,
        bottom,
    )
    val trackTop = card.top + (PANEL_PAD + PANEL_LABEL + PANEL_BUTTON + PANEL_TRACK_GAP).value * density
    val trackBottom = card.bottom - (PANEL_PAD + PANEL_BUTTON + PANEL_TRACK_GAP).value * density
    val x = card.center.x
    return ProgressGeometry(
        card = card,
        sliderTrack = Rect(x - 4 * density, trackTop, x + 4 * density, trackBottom),
        rail = Rect(size.width - 11 * density, top, size.width - 7 * density, bottom),
    )
}

// controls on the right edge, shrinking into the slim scroll rail when hidden.
// only the touchable parts take touches so the strip keeps scrolling
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun WebtoonProgress(
    listState: LazyListState,
    source: PageSource,
    count: Int,
    overlayVisible: Boolean,
    label: String,
    previousChapterId: Int?,
    nextChapterId: Int?,
    onOpenChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val density = LocalDensity.current
    val dp = density.density
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val trackColor = colors.secondaryContainer
    val fillColor = colors.primary
    val cardColor = colors.surfaceContainer
    val cardEdge = colors.outlineVariant
    val insets = WindowInsets.systemBarsIgnoringVisibility
    val insetTop = insets.getTop(density).toFloat()
    val insetBottom = insets.getBottom(density).toFloat()

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragging = dragFraction != null
    LaunchedEffect(overlayVisible) { dragFraction = null }

    // 0 = panel, 1 = rail
    val morph = remember { Animatable(if (overlayVisible) 0f else 1f) }
    LaunchedEffect(overlayVisible) { morph.animateTo(if (overlayVisible) 0f else 1f, motion.slowSpatialSpec()) }

    // dims after a moment of stillness
    var active by remember { mutableStateOf(false) }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging, overlayVisible) {
        if (scrolling || dragging || overlayVisible) {
            active = true
        } else {
            delay(1200)
            active = false
        }
    }
    val railAlpha by animateFloatAsState(
        if (active) 1f else IDLE_RAIL_ALPHA,
        motion.defaultEffectsSpec(),
        label = "progress-alpha",
    )
    val heldExtra by animateDpAsState(if (dragging) 6.dp else 0.dp, motion.fastSpatialSpec(), label = "progress-held")

    val fraction by remember { derivedStateOf { dragFraction ?: scrollFraction(listState, count) } }
    val page by remember { derivedStateOf { (fraction * count).toInt().coerceIn(0, count - 1) + 1 } }
    val geometry = progressGeometry(Size(boxSize.width.toFloat(), boxSize.height.toFloat()), dp, insetTop, insetBottom)

    fun scrollToFraction(f: Float) {
        if (f >= 0.995f) {
            listState.requestScrollToItem(count, 0)
            return
        }
        val position = f * count
        val index = position.toInt().coerceIn(0, count - 1)
        val stripWidth = listState.layoutInfo.viewportSize.width.coerceAtMost(with(density) { STRIP_MAX_WIDTH.roundToPx() })
        val pageHeight = stripWidth / (source.aspectRatio(index) ?: DEFAULT_PAGE_RATIO)
        listState.requestScrollToItem(index, ((position - index) * pageHeight).toInt())
    }

    val thumbHeightPx = THUMB_HEIGHT.value * dp
    Box(modifier.onSizeChanged { boxSize = it }) {
        Canvas(Modifier.fillMaxSize()) {
            val m = morph.value
            val mc = m.coerceIn(0f, 1f)
            val f = fraction
            val strength = androidx.compose.ui.util.lerp(1f, railAlpha, mc)

            val cardAlpha = (1f - m * 1.5f).coerceIn(0f, 1f)
            if (cardAlpha > 0f) {
                val card = lerp(geometry.card, geometry.rail, m)
                val cardCorner = CornerRadius(
                    minOf(androidx.compose.ui.util.lerp(28 * dp, 2 * dp, m), card.width / 2f, card.height / 2f).coerceAtLeast(0f),
                )
                drawRoundRect(cardColor.copy(alpha = 0.96f * cardAlpha), card.topLeft, card.size, cardCorner)
                drawRoundRect(
                    cardEdge.copy(alpha = 0.5f * cardAlpha),
                    card.topLeft,
                    card.size,
                    cardCorner,
                    style = Stroke(1 * dp),
                )
            }

            val track = lerp(geometry.sliderTrack, geometry.rail, m)
            val trackCorner = CornerRadius(track.width / 2f)
            val sliderThumb = Offset(
                geometry.sliderTrack.center.x,
                geometry.sliderTrack.top + f * geometry.sliderTrack.height,
            )
            val railThumb = Offset(
                geometry.rail.center.x,
                geometry.rail.top + thumbHeightPx / 2f + f * (geometry.rail.height - thumbHeightPx).coerceAtLeast(0f),
            )
            val center = lerp(sliderThumb, railThumb, m)
            val thumbW = androidx.compose.ui.util.lerp(28 * dp, 6 * dp, m) + heldExtra.toPx() * mc
            val thumbH = androidx.compose.ui.util.lerp(6 * dp, thumbHeightPx, m)

            drawRoundRect(
                trackColor.copy(alpha = trackColor.alpha * androidx.compose.ui.util.lerp(1f, 0.6f, mc) * strength),
                topLeft = track.topLeft,
                size = track.size,
                cornerRadius = trackCorner,
            )
            val fillAlpha = (1f - m * 1.6f).coerceIn(0f, 1f) * strength
            if (fillAlpha > 0f) {
                drawRoundRect(
                    fillColor.copy(alpha = fillAlpha),
                    topLeft = track.topLeft,
                    size = Size(track.width, (center.y - track.top).coerceAtLeast(track.width)),
                    cornerRadius = trackCorner,
                )
            }
            drawRoundRect(
                fillColor.copy(alpha = strength),
                topLeft = Offset(center.x - thumbW / 2f, center.y - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(minOf(thumbW, thumbH) / 2f),
            )
        }

        if (boxSize.width > 0) {
            val card = geometry.card
            AnimatedVisibility(
                visible = overlayVisible,
                modifier = Modifier
                    .offset { IntOffset(card.left.roundToInt(), card.top.roundToInt()) }
                    .size(with(density) { card.width.toDp() }, with(density) { card.height.toDp() }),
                enter = fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = PANEL_PAD),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(PANEL_LABEL), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    IconButton(
                        onClick = { previousChapterId?.let(onOpenChapter) },
                        enabled = previousChapterId != null,
                        modifier = Modifier.size(PANEL_BUTTON),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.reader_previous_chapter),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { nextChapterId?.let(onOpenChapter) },
                        enabled = nextChapterId != null,
                        modifier = Modifier.size(PANEL_BUTTON),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.reader_next_chapter),
                        )
                    }
                }
            }

            if (overlayVisible) {
                val strip = geometry.sliderTrack
                Box(
                    Modifier
                        .offset { IntOffset((strip.center.x - 24 * dp).roundToInt(), strip.top.roundToInt()) }
                        .size(48.dp, with(density) { strip.height.toDp() })
                        .pointerInput(strip.height) {
                            detectTapGestures { position -> scrollToFraction((position.y / size.height).coerceIn(0f, 1f)) }
                        }
                        .pointerInput(strip.height) {
                            detectVerticalDragGestures(
                                onDragStart = { position ->
                                    val f = (position.y / size.height).coerceIn(0f, 1f)
                                    dragFraction = f
                                    scrollToFraction(f)
                                },
                                onDragEnd = { dragFraction = null },
                                onDragCancel = { dragFraction = null },
                            ) { change, _ ->
                                change.consume()
                                val f = (change.position.y / size.height).coerceIn(0f, 1f)
                                dragFraction = f
                                scrollToFraction(f)
                            }
                        },
                )
            }
        }

        if (!overlayVisible) {
            val rail = geometry.rail
            val grab = 44.dp
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (boxSize.width - grab.toPx()).roundToInt(),
                            (rail.top + fraction * (rail.height - thumbHeightPx).coerceAtLeast(0f)).roundToInt(),
                        )
                    }
                    .size(grab, THUMB_HEIGHT)
                    .systemGestureExclusion()
                    .pointerInput(rail.height) {
                        var accumulated = 0f
                        var start = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                start = scrollFraction(listState, count)
                                accumulated = 0f
                                dragFraction = start
                            },
                            onDragEnd = { dragFraction = null },
                            onDragCancel = { dragFraction = null },
                        ) { change, amount ->
                            change.consume()
                            accumulated += amount
                            val travel = (rail.height - thumbHeightPx).coerceAtLeast(1f)
                            val f = (start + accumulated / travel).coerceIn(0f, 1f)
                            dragFraction = f
                            scrollToFraction(f)
                        }
                    },
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            0,
                            (rail.top + fraction * (rail.height - thumbHeightPx).coerceAtLeast(0f)).roundToInt(),
                        )
                    }
                    .height(THUMB_HEIGHT)
                    .padding(end = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = dragging,
                    enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f),
                    exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.inverseSurface,
                        contentColor = colors.inverseOnSurface,
                    ) {
                        Text(
                            "$page / $count",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private fun scrollFraction(listState: LazyListState, count: Int): Float {
    if (!listState.canScrollBackward) return 0f
    if (!listState.canScrollForward) return 1f
    val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val within = if (first.size > 0) (-first.offset).coerceAtLeast(0).toFloat() / first.size else 0f
    return ((first.index + within) / count).coerceIn(0f, 1f)
}
