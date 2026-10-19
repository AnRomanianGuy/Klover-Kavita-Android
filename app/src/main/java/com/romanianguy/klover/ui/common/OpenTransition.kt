package com.romanianguy.klover.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.romanianguy.klover.data.prefs.MotionStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Immutable
class OpenRequest(
    val key: Any,
    val origin: Rect,
    val coverUrl: String?,
    val cornerRadius: Dp,
    val navigate: () -> Unit,
    val title: String? = null,
) {
    internal var startRect: Rect = origin
    internal var startCorner: Dp = cornerRadius
}

@Immutable
private class OpenedFrom(
    val seriesId: Int,
    val key: Any,
    val origin: Rect,
    val coverUrl: String?,
    val cornerRadius: Dp,
    val title: String? = null,
)

@Immutable
internal class CloseRequest(
    val from: Rect,
    val key: Any,
    val origin: Rect,
    val coverUrl: String?,
    val cornerRadius: Dp,
    val start: Float = 0f,
    val emerges: Boolean = false,
    val title: String? = null,
)

@Stable
class OpenTransitionController {
    internal var request by mutableStateOf<OpenRequest?>(null)
        private set

    internal var closing by mutableStateOf<CloseRequest?>(null)
        private set

    var targetBounds by mutableStateOf<Rect?>(null)
        private set

    internal var windowSize: IntSize = IntSize.Zero

    internal fun isOutOfSight(reported: Rect): Boolean {
        val w = windowSize.width.toFloat()
        val h = windowSize.height.toFloat()
        if (w <= 0f || h <= 0f) return false
        val seen = reported.intersect(Rect(0f, 0f, w, h))
        return !(seen.width > 0f && seen.height > 0f && seen.width * seen.height >= 0.5f * reported.width * reported.height)
    }

    internal fun visibleStart(reported: Rect): Rect {
        val w = windowSize.width.toFloat()
        val h = windowSize.height.toFloat()
        if (w <= 0f || h <= 0f) return reported
        val width = reported.width.coerceAtMost(w)
        val height = reported.height.coerceAtMost(h)
        if (!isOutOfSight(reported)) return reported
        val left = reported.left.coerceIn(0f, w - width)
        val top = reported.top.coerceIn(0f, h - height)
        return Rect(left, top, left + width, top + height)
    }

    private var opened by mutableStateOf<OpenedFrom?>(null)

    // plain map: it changes as cards come and go and nothing is drawn from it
    private val cards = HashMap<Any, LiveBounds>()

    fun registerCard(key: Any, bounds: LiveBounds) {
        cards[key] = bounds
    }

    fun unregisterCard(key: Any, bounds: LiveBounds) {
        if (cards[key] === bounds) cards.remove(key)
    }

    internal fun originOf(key: Any, fallback: Rect): Rect =
        cards[key]?.current()?.takeIf { it.width > 1f && it.height > 1f } ?: fallback

    var gesture by mutableStateOf<Float?>(null)
        private set
    private var pendingStart = 0f

    internal fun gestureFraction(progress: Float): Float = 0.5f * progress.coerceIn(0f, 1f)

    fun startGesture() {
        val from = opened ?: return
        if (isBusy) return
        hiddenKey = from.key
        restoreSoftly = false
        gesture = 0f
    }

    fun updateGesture(progress: Float) {
        if (gesture != null) gesture = progress.coerceIn(0f, 1f)
    }

    fun commitGesture() {
        pendingStart = gestureFraction(gesture ?: 0f)
        gesture = null
    }

    fun cancelGesture() {
        gesture = null
        hiddenKey = null
    }

    internal fun gestureRequest(): CloseRequest? {
        val from = opened ?: return null
        val reported = targetBounds ?: return null
        return CloseRequest(
            visibleStart(reported),
            from.key,
            from.origin,
            from.coverUrl,
            from.cornerRadius,
            emerges = isOutOfSight(reported),
            title = from.title,
        )
    }

    var hiddenKey by mutableStateOf<Any?>(null)
        private set

    val isOpening: Boolean get() = request != null

    var revealed by mutableStateOf(false)
        private set

    var restoreSoftly by mutableStateOf(false)
        private set
    private var softUntil = 0L

    internal fun isSoftNow(): Boolean = restoreSoftly && System.currentTimeMillis() < softUntil

    val isClosing: Boolean get() = closing != null || gesture != null

    val isBusy: Boolean get() = request != null || closing != null

    fun open(request: OpenRequest) {
        if (isBusy) return
        targetBounds = null
        revealed = false
        hiddenKey = request.key
        restoreSoftly = false
        this.request = request
    }

    fun reportCoverBounds(bounds: Rect) {
        targetBounds = bounds
    }

    fun markOpened(seriesId: Int) {
        opened = request?.let { OpenedFrom(seriesId, it.key, it.origin, it.coverUrl, it.cornerRadius, it.title) }
    }

    fun canClose(seriesId: Int): Boolean = opened?.seriesId == seriesId && !isBusy && targetBounds != null

    fun close(pop: () -> Unit) {
        val from = opened
        val reported = targetBounds
        val at = reported?.let(::visibleStart)
        if (from == null || at == null || isBusy) {
            pop()
            return
        }
        pop()
        hiddenKey = from.key
        restoreSoftly = false
        closing = CloseRequest(
            at,
            from.key,
            from.origin,
            from.coverUrl,
            from.cornerRadius,
            pendingStart,
            emerges = isOutOfSight(reported),
            title = from.title,
        )
        pendingStart = 0f
    }

    internal suspend fun awaitTarget(timeoutMillis: Long): Rect? =
        withTimeoutOrNull(timeoutMillis) { snapshotFlow { targetBounds }.first { it != null } }

    internal fun landed() {
        revealed = true
        restoreSoftly = true
        softUntil = System.currentTimeMillis() + 400
        hiddenKey = null
        request = null
    }

    internal fun abandon() {
        revealed = true
        hiddenKey = null
        request = null
    }

    internal fun finishClose() {
        restoreSoftly = false
        hiddenKey = null
        closing = null
        opened = null
    }
}

val LocalOpenTransition = staticCompositionLocalOf<OpenTransitionController?> { null }

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun OpenTransitionHost(content: @Composable () -> Unit) {
    val controller = remember { OpenTransitionController() }
    CompositionLocalProvider(LocalOpenTransition provides controller) {
        Box(Modifier.fillMaxSize().onSizeChanged { controller.windowSize = it }) {
            content()
            TransitionOverlays(controller)
        }
    }
}

// separate composable so per-frame state doesn't recompose the whole app
@Composable
private fun TransitionOverlays(controller: OpenTransitionController) {
    controller.request?.let { OpenOverlay(it, controller) }
    controller.closing?.let { CloseOverlay(it, controller) }
    // only whether a gesture exists matters here; its progress is read when drawing
    val gestureActive by remember { derivedStateOf { controller.gesture != null } }
    if (gestureActive) {
        controller.gestureRequest()?.let { request ->
            GestureOverlay(request, controller) { controller.gestureFraction(controller.gesture ?: 0f) }
        }
    }
}

// position, size, corner and lift are read in layout and draw, so a frame doesn't recompose
@Composable
private fun FlyingCover(
    rect: () -> Rect,
    cornerPx: () -> Float,
    lift: () -> Float,
    coverUrl: String?,
    title: String? = null,
    presence: () -> Float = { 1f },
) {
    Box(
        Modifier
            .offset {
                val r = rect()
                IntOffset(r.left.roundToInt(), r.top.roundToInt())
            }
            .layout { measurable, _ ->
                val r = rect()
                val w = r.width.roundToInt().coerceAtLeast(1)
                val h = r.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                shape = RoundedCornerShape(cornerPx())
                clip = true
                val here = presence().coerceIn(0f, 1f)
                alpha = here
                val grow = 0.88f + 0.12f * here
                scaleX = grow
                scaleY = grow
                shadowElevation = 18.dp.toPx() * lift() * here
            },
    ) {
        CoverImage(
            url = coverUrl,
            contentDescription = null,
            placeholderTitle = title,
            shape = RectangleShape,
            aspectRatio = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun lift(progress: Float): Float {
    val x = progress.coerceIn(0f, 1f)
    return 4f * x * (1f - x)
}

@Composable
private fun OpenOverlay(request: OpenRequest, controller: OpenTransitionController) {
    val flight = remember(request) { Animatable(0f) }
    var target by remember(request) { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val pageCornerPx = with(density) { themeCornerDp().toPx() } // the series page's cover has the theme's extra-large shape
    val startCornerPx = with(density) { request.startCorner.toPx() }

    LaunchedEffect(request) {
        request.navigate()
        val bounds = controller.awaitTarget(READY_TIMEOUT_MS)
        if (bounds == null) {
            controller.abandon()
            return@LaunchedEffect
        }
        target = bounds
        // the page is still being built, flying now would stutter
        delay(SETTLE_OPEN_MS)
        flight.animateTo(1f, tween(durationMillis = 520, easing = Emphasized))
        controller.landed()
    }
    BackHandler(enabled = true) { }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        FlyingCover(
            rect = { lerp(request.startRect, controller.targetBounds ?: target ?: request.startRect, flight.value) },
            cornerPx = { androidx.compose.ui.util.lerp(startCornerPx, pageCornerPx, flight.value.coerceIn(0f, 1f)) },
            lift = { lift(flight.value) },
            coverUrl = request.coverUrl,
            title = request.title,
        )
    }
}

@Composable
private fun CloseOverlay(request: CloseRequest, controller: OpenTransitionController) {
    val flight = remember(request) { Animatable(request.start) }
    val density = LocalDensity.current
    val pageCornerPx = with(density) { themeCornerDp().toPx() }
    val homeCornerPx = with(density) { request.cornerRadius.toPx() }

    LaunchedEffect(request) {
        if (request.start == 0f) delay(SETTLE_CLOSE_MS)
        // a cover from out of sight comes in slower so it can be seen arriving
        if (request.emerges) {
            flight.animateTo(1f, tween(durationMillis = 640, easing = FastOutSlowInEasing))
        } else {
            flight.animateTo(1f, tween(durationMillis = 480, easing = Emphasized))
        }
        controller.finishClose()
    }
    BackHandler(enabled = true) { }

    Box(Modifier.fillMaxSize()) {
        FlyingCover(
            rect = { lerp(request.from, controller.originOf(request.key, request.origin), flight.value) },
            cornerPx = { androidx.compose.ui.util.lerp(pageCornerPx, homeCornerPx, flight.value.coerceIn(0f, 1f)) },
            lift = { lift(flight.value) },
            coverUrl = request.coverUrl,
            title = request.title,
            presence = { if (request.emerges) emerge(flight.value) else 1f },
        )
    }
}

private fun emerge(progress: Float): Float {
    val x = (progress / 0.35f).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

@Composable
private fun GestureOverlay(request: CloseRequest, controller: OpenTransitionController, fraction: () -> Float) {
    val density = LocalDensity.current
    val pageCornerPx = with(density) { themeCornerDp().toPx() }
    val homeCornerPx = with(density) { request.cornerRadius.toPx() }
    Box(Modifier.fillMaxSize()) {
        FlyingCover(
            rect = { lerp(request.from, controller.originOf(request.key, request.origin), fraction()) },
            cornerPx = { androidx.compose.ui.util.lerp(pageCornerPx, homeCornerPx, fraction().coerceIn(0f, 1f)) },
            lift = { (fraction() * 4f).coerceAtMost(1f) },
            coverUrl = request.coverUrl,
            title = request.title,
            // the gesture only takes it half way
            presence = { if (request.emerges) emerge(fraction() * 2f) else 1f },
        )
    }
}

private const val READY_TIMEOUT_MS = 1500L

private const val SETTLE_OPEN_MS = 70L

private const val SETTLE_CLOSE_MS = 50L

@Composable
fun Modifier.revealOnOpen(index: Int): Modifier {
    val controller = LocalOpenTransition.current
    val reduced = LocalAppSettings.current.motionStyle == MotionStyle.Reduced
    val waiting = controller != null && controller.isOpening && !controller.revealed
    val shown by animateFloatAsState(
        targetValue = if (waiting) 0f else 1f,
        animationSpec = if (waiting || reduced) tween(0) else tween(
            durationMillis = 420,
            delayMillis = index.coerceAtMost(10) * 55,
            easing = FastOutSlowInEasing,
        ),
        label = "reveal-on-open",
    )
    return graphicsLayer {
        alpha = shown
        translationY = (1f - shown) * 18.dp.toPx()
    }
}

@Composable
fun Modifier.openTargetCover(): Modifier {
    val controller = LocalOpenTransition.current ?: return this
    return this
        // not boundsInRoot: it's clipped to what is visible
        .onGloballyPositioned {
            if (it.isAttached && it.size.width > 0 && it.size.height > 0) {
                val at = it.positionInRoot()
                controller.reportCoverBounds(Rect(at.x, at.y, at.x + it.size.width, at.y + it.size.height))
            }
        }
        .graphicsLayer { alpha = if (controller.isOpening || controller.isClosing) 0f else 1f }
}

// must vanish on the exact frame the cover leaves and return on the exact frame it lands; an animation lags a frame
// and flashes
@Composable
fun originCoverAlpha(hidden: Boolean, controller: OpenTransitionController?): () -> Float {
    val soft = !hidden && controller?.isSoftNow() == true
    val eased = animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = if (soft) tween(durationMillis = 360) else tween(durationMillis = 0),
        label = "origin-cover",
    )
    // read in the graphics layer so the card doesn't recompose during the fade
    return {
        when {
            hidden -> 0f
            soft -> eased.value
            else -> 1f
        }
    }
}
