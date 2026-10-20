package com.romanianguy.klover.ui.reader.book

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

interface BookPageListener {
    fun onReady(index: Int, total: Int)

    fun onPage(index: Int, total: Int)

    fun onSwipe(direction: Int)

    fun onTap(zone: Int)

    fun onSpeechScreen(index: Int)

    fun onWordLong(offset: Int)

    fun onLink(section: Int)

    fun onExternalLink(url: String)
}

internal class Highlight(val sentenceFrom: Int, val sentenceTo: Int, val wordFrom: Int, val wordTo: Int) {
    val active get() = sentenceFrom >= 0

    companion object {
        val None = Highlight(-1, -1, -1, -1)
    }
}

@Stable
class BookPageController {
    internal var speech: String = ""
    internal val requests = Channel<Int>(Channel.UNLIMITED)
    internal var marked by mutableStateOf(Highlight.None)

    var textLeft by mutableIntStateOf(0)
        internal set

    fun goTo(screen: Int) {
        requests.trySend(screen.coerceAtLeast(0))
    }

    fun speechText(): String = speech

    fun highlight(sentenceStart: Int, sentenceEnd: Int, wordStart: Int, wordEnd: Int) {
        marked = if (sentenceStart < 0) Highlight.None else Highlight(sentenceStart, sentenceEnd, wordStart, wordEnd)
    }
}

@Composable
internal fun BookView(
    model: BookModel,
    style: BookStyle,
    start: BookStart,
    controller: BookPageController,
    listener: BookPageListener,
    images: BookImages,
    modifier: Modifier = Modifier,
    // where the book's fold is (x, in view pixels) when the columns are two pages of a book, or null to slide screens
    // instead
    fold: Float? = null,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val resolver = LocalFontFamilyResolver.current
    val scope = rememberCoroutineScope()
    val currentListener = rememberUpdatedState(listener)
    val currentStyle = rememberUpdatedState(style)
    val flips = fold != null && style.columns == 2
    // a page turning over the fold settles slower than a slide
    val spring = spring<Float>(dampingRatio = 1f, stiffness = if (flips) 260f else 600f)

    var size by remember { mutableStateOf(IntSize.Zero) }
    val sizes = remember(model) { mutableStateMapOf<String, IntSize>() }
    var measured by remember(model) { mutableStateOf(model.images.isEmpty()) }
    var layout by remember(model) { mutableStateOf<BookLayout?>(null) }
    var current by remember(model) { mutableIntStateOf(0) }
    var offset by remember(model) { mutableFloatStateOf(0f) }
    val place = remember(model) { intArrayOf(-1, 0) } // [anchor, 1 once reported ready]
    val animation = remember(model) { arrayOfNulls<Job>(1) }

    LaunchedEffect(model) {
        controller.speech = model.speech
        if (model.images.isEmpty()) return@LaunchedEffect
        val gate = Semaphore(6)
        coroutineScope {
            val jobs = model.images.map { src ->
                launch { gate.withPermit { images.size(src)?.let { sizes[src] = it } } }
            }
            withTimeoutOrNull(IMAGE_SIZE_WAIT_MS) { jobs.joinAll() }
            measured = true
        }
    }

    fun publish(l: BookLayout) {
        controller.textLeft = if (l.hasText) (model.speech.length - l.firstSpeechOn(current)).coerceAtLeast(0) else 0
    }

    LaunchedEffect(model, style, size, measured) {
        if (size == IntSize.Zero || !measured) return@LaunchedEffect
        snapshotFlow { sizes.toMap() }.collectLatest { known ->
            val result = withContext(Dispatchers.Default) {
                layoutBook(
                    model,
                    style,
                    size.width,
                    size.height,
                    density,
                    direction,
                    resolver,
                    known,
                ) { ensureActive() }
            }
            layout = result
            current = if (place[0] < 0) {
                (start.fraction * (result.count - 1)).roundToInt()
            } else {
                result.screenOfSpeech(place[0])
            }.coerceIn(0, result.count - 1)
            offset = 0f
            place[0] = if (result.hasText) result.firstSpeechOn(current) else -1
            publish(result)
            if (place[1] == 0) {
                place[1] = 1
                currentListener.value.onReady(current, result.count)
            } else {
                currentListener.value.onPage(current, result.count)
            }
        }
    }

    fun settled(l: BookLayout) {
        place[0] = if (l.hasText) l.firstSpeechOn(current) else -1
        publish(l)
        currentListener.value.onPage(current, l.count)
    }

    suspend fun moveTo(target: Int) {
        val l = layout ?: return
        val to = target.coerceIn(0, l.count - 1)
        val width = size.width.toFloat()
        if (to == current) {
            if (offset != 0f) animate(offset, 0f, animationSpec = spring) { v, _ -> offset = v }
            return
        }
        if (abs(to - current) == 1 && width > 0f) {
            animate(offset, -(to - current) * width, animationSpec = spring) { v, _ -> offset = v }
        }
        current = to
        offset = 0f
        settled(l)
    }

    LaunchedEffect(controller, model) {
        for (target in controller.requests) {
            val job = scope.launch { moveTo(target) }
            animation[0] = job
            job.join()
        }
    }

    LaunchedEffect(controller, model) {
        snapshotFlow { controller.marked }.collect { mark ->
            val l = layout ?: return@collect
            if (!mark.active) return@collect
            val at = if (mark.wordFrom >= 0 && mark.wordTo > mark.wordFrom) mark.wordFrom else mark.sentenceFrom
            val screen = l.screenOfSpeech(at)
            if (screen != current) currentListener.value.onSpeechScreen(screen)
        }
    }

    val slop = with(density) { 8.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        val l = layout ?: return@rememberDraggableState
        val width = size.width.toFloat()
        // past the first/last screen the page gives way reluctantly
        val resisted = (offset > 0f && current == 0) || (offset < 0f && current == l.count - 1) ||
            (offset == 0f && ((delta > 0f && current == 0) || (delta < 0f && current == l.count - 1)))
        offset = (offset + if (resisted) delta * 0.35f else delta).coerceIn(-width, width)
    }

    Box(
        modifier
            .background(style.background)
            .onSizeChanged { size = it }
            .pointerInput(model) {
                detectTapGestures(
                    onLongPress = { at ->
                        val l = layout ?: return@detectTapGestures
                        if (offset == 0f) hit(l.screens.getOrNull(current), at, slop)?.let { currentListener.value.onWordLong(it.speech) }
                    },
                    onTap = { at ->
                        val l = layout ?: return@detectTapGestures
                        if (offset != 0f) return@detectTapGestures
                        val link = hit(l.screens.getOrNull(current), at, slop)?.link
                        when {
                            link?.section != null -> currentListener.value.onLink(link.section)
                            link?.url != null -> currentListener.value.onExternalLink(link.url)
                            else -> {
                                val f = at.x / size.width
                                val edge = currentStyle.value.tapEdge
                                currentListener.value.onTap(if (f < edge) -1 else if (f > 1f - edge) 1 else 0)
                            }
                        }
                    },
                )
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = { animation[0]?.cancel() },
                onDragStopped = { velocity ->
                    val width = size.width.toFloat()
                    val dir = when {
                        offset < -width * 0.2f || (velocity < -FLING && offset < 0f) -> 1
                        offset > width * 0.2f || (velocity > FLING && offset > 0f) -> -1
                        else -> 0
                    }
                    if (dir != 0) {
                        // the reader decides, and asks for the slide or for the neighbouring section
                        currentListener.value.onSwipe(dir)
                    }
                    val l = layout
                    val moves = dir != 0 && l != null && (current + dir) in 0 until l.count
                    if (!moves) {
                        animation[0] = scope.launch { animate(offset, 0f, animationSpec = spring) { v, _ -> offset = v } }
                    }
                },
            ),
    ) {
        val l = layout
        if (l != null && size.width > 0) {
            if (flips && fold != null) {
                FlippingScreens(
                    l,
                    current,
                    { offset },
                    size,
                    fold,
                    direction == LayoutDirection.Rtl,
                    style,
                    controller,
                    images,
                )
            } else {
                for (i in current - 1..current + 1) {
                    if (i !in 0 until l.count) continue
                    key(i) {
                        BookScreen(
                            pieces = l.screens.getOrElse(i) { emptyList() },
                            style = style,
                            controller = controller,
                            images = images,
                            modifier = Modifier.graphicsLayer { translationX = offset + (i - current) * size.width },
                        )
                    }
                }
            }
        }
    }
}

private const val SETTLED = 0.002f

@Composable
private fun FlippingScreens(
    layout: BookLayout,
    current: Int,
    offset: () -> Float,
    size: IntSize,
    fold: Float,
    rtl: Boolean,
    style: BookStyle,
    controller: BookPageController,
    images: BookImages,
) {
    val density = LocalDensity.current
    val width = size.width.toFloat()
    val camera = 22f * density.density
    fun position() = current - offset() / width

    for (i in current - 1..current + 1) {
        if (i !in 0 until layout.count) continue
        key(i) {
            fun partnerMissing(r: Float) = (r > 0f && i - 1 !in 0 until layout.count) || (r < 0f && i + 1 !in 0 until layout.count)
            BookScreen(
                pieces = layout.screens.getOrElse(i) { emptyList() },
                style = style,
                controller = controller,
                images = images,
                modifier = Modifier
                    .graphicsLayer {
                        if (i == current && partnerMissing(i - position())) translationX = offset()
                    }
                    .drawWithContent {
                        val r = i - position()
                        val whole = abs(r) <= SETTLED || partnerMissing(r)
                        when {
                            // a screen a whole turn away must be gone at the same closeness the landing one counts as
                            // at rest
                            abs(r) >= 1f - SETTLED && !whole -> Unit
                            whole -> this.drawContent()
                            else -> {
                                val leftShows = if (rtl) r >= 0f else r <= 0f
                                if (leftShows) {
                                    clipRect(0f, 0f, fold, this.size.height) { this@drawWithContent.drawContent() }
                                } else {
                                    clipRect(fold, 0f, this.size.width, this.size.height) { this@drawWithContent.drawContent() }
                                }
                            }
                        }
                    },
            )
        }
    }

    for (lower in current - 1..current) {
        if (lower < 0 || lower + 1 >= layout.count) continue
        key(lower) {
            fun t() = (position() - lower).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = position() - lower
                        alpha = if (t > SETTLED && t < 1f - SETTLED) 1f else 0f
                    },
            ) {
                // forward, left-to-right books turn the right column to the left, right-to-left the opposite
                for (out in listOf(true, false)) {
                    val screen = if (out) lower else lower + 1
                    val onRight = if (out) !rtl else rtl
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = t()
                                cameraDistance = camera
                                transformOrigin = TransformOrigin(fold / this.size.width, 0.5f)
                                val angle = if (out) t * 180f else (1f - t) * 180f
                                rotationY = if (onRight) -angle else angle
                                alpha = if (out) (if (t < 0.5f) 1f else 0f) else (if (t >= 0.5f) 1f else 0f)
                            }
                            .drawWithContent {
                                if (onRight) {
                                    clipRect(fold, 0f, this.size.width, this.size.height) { this@drawWithContent.drawContent() }
                                } else {
                                    clipRect(0f, 0f, fold, this.size.height) { this@drawWithContent.drawContent() }
                                }
                            },
                    ) {
                        // own paper, otherwise the page beneath shows through the text
                        BookScreen(
                            pieces = layout.screens.getOrElse(screen) { emptyList() },
                            style = style,
                            controller = controller,
                            images = images,
                            modifier = Modifier.background(style.background),
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.38f * sin(t() * Math.PI.toFloat()) }
                                .background(Color.Black),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookScreen(
    pieces: List<Piece>,
    style: BookStyle,
    controller: BookPageController,
    images: BookImages,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val mark = controller.marked
            val pad = 6.dp.toPx()
            for (p in pieces) {
                when (p) {
                    is TextPiece -> clipRect(p.x - pad, p.y, p.x + p.w + pad, p.y + p.h) {
                        val origin = Offset(p.x, p.y - p.top)
                        if (mark.active) {
                            paint(p, mark.sentenceFrom, mark.sentenceTo, style.selection, origin)
                            paint(p, mark.wordFrom, mark.wordTo, style.link.copy(alpha = 0.4f), origin)
                        }
                        drawText(p.layout, topLeft = origin)
                    }

                    is RulePiece -> drawLine(
                        style.text.copy(alpha = 0.3f),
                        Offset(p.x, p.y),
                        Offset(p.x + p.w, p.y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    is ImagePiece -> Unit
                }
            }
        }
        for (p in pieces) {
            if (p !is ImagePiece) continue
            val source = remember(p) { p.image.svg?.encodeToByteArray() ?: images.model(p.image.src) } ?: continue
            AsyncImage(
                model = source,
                contentDescription = p.image.alt.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                    .size(with(density) { p.w.toDp() }, with(density) { p.h.toDp() }),
            )
        }
    }
}

private fun DrawScope.paint(
    p: TextPiece,
    from: Int,
    to: Int,
    color: Color,
    origin: Offset,
) {
    if (from < 0) return
    val a = max(from, p.speechFrom) - p.block.speechStart
    val b = min(to, p.speechTo) - p.block.speechStart
    if (a >= b) return
    val text = p.layout
    if (text.getParagraphDirection(a) == ResolvedTextDirection.Rtl) {
        translate(origin.x, origin.y) { drawPath(text.getPathForRange(a, b), color) }
        return
    }
    // line by line; each line runs to its end including a hyphen the layout added, which the range path leaves out
    val firstLine = text.getLineForOffset(a)
    val lastLine = text.getLineForOffset(b - 1)
    translate(origin.x, origin.y) {
        for (line in firstLine..lastLine) {
            val left = if (line == firstLine) text.getHorizontalPosition(a, true) else text.getLineLeft(line)
            val right = if (line == lastLine && b < text.getLineEnd(line, visibleEnd = false)) {
                text.getHorizontalPosition(b, true)
            } else {
                text.getLineRight(line)
            }
            if (right <= left) continue
            drawRect(
                color,
                topLeft = Offset(left, text.getLineTop(line)),
                size = Size(right - left, text.getLineBottom(line) - text.getLineTop(line) + 1f),
            )
        }
    }
}

private class Hit(val speech: Int, val link: BookLink?)

private fun hit(pieces: List<Piece>?, at: Offset, slop: Float): Hit? {
    if (pieces == null) return null
    for (p in pieces) {
        if (p !is TextPiece) continue
        if (at.x < p.x - slop || at.x > p.x + p.w + slop || at.y < p.y || at.y > p.y + p.h) continue
        val local = Offset(at.x - p.x, at.y - p.y + p.top)
        val char = p.layout.getOffsetForPosition(local).coerceIn(p.charFrom, max(p.charFrom, p.charTo - 1))
        // only counts when the touch is on its text, not the empty end of the line
        val link = p.block.spans.firstOrNull { it.fmt.link != null && char >= it.start && char < it.end }?.fmt?.link
            ?.takeIf { p.layout.getBoundingBox(char).inflate(slop).contains(local) }
        return Hit(p.block.speechStart + char, link)
    }
    return null
}

private const val IMAGE_SIZE_WAIT_MS = 2_000L
private const val FLING = 900f
