package com.romanianguy.klover.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set
    internal var size by mutableStateOf(IntSize.Zero)

    // the part being zoomed (null = all of it): zooms around its centre and is clipped to it, so one page of a spread
    // zooms without spilling over
    internal var region by mutableStateOf<Rect?>(null)

    val isZoomed: Boolean get() = scale > 1.01f

    internal val bounds: Rect get() = region ?: Rect(Offset.Zero, Size(size.width.toFloat(), size.height.toFloat()))

    internal fun clamp(offset: Offset, scale: Float): Offset {
        val maxX = bounds.width * (scale - 1f) / 2f
        val maxY = bounds.height * (scale - 1f) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    internal suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) { t, _ ->
            val s = startScale + (targetScale - startScale) * t
            scale = s
            offset = clamp(
                Offset(
                    startOffset.x + (targetOffset.x - startOffset.x) * t,
                    startOffset.y + (targetOffset.y - startOffset.y) * t,
                ),
                s,
            )
        }
    }

    suspend fun reset() {
        animateTo(MIN_SCALE, Offset.Zero)
        region = null
    }

    fun resetInstantly() {
        scale = 1f
        offset = Offset.Zero
        region = null
    }
}

@Composable
fun rememberZoomState(vararg keys: Any?): ZoomState = remember(*keys) { ZoomState() }

// gestures are consumed only while zooming or zoomed, so a horizontal drag still reaches the pager.
// with [enabled] off nothing listens, so stacked pages (book flip) don't take touches meant for the visible one
@Composable
fun Modifier.zoomable(
    state: ZoomState,
    doubleTapScale: Float = DOUBLE_TAP_SCALE,
    regionAt: ((position: Offset, size: IntSize) -> Rect?)? = null,
    enabled: Boolean = true,
    onTap: (position: Offset, size: IntSize) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val gestures = if (!enabled) Modifier else Modifier
        .pointerInput(state) {
            detectTapGestures(
                onTap = { onTap(it, size) },
                onDoubleTap = { tap ->
                    if (!state.isZoomed) state.region = regionAt?.invoke(tap, size)
                    scope.toggleZoom(state, tap, doubleTapScale)
                },
            )
        }
        .pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var picked = state.isZoomed
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointers = event.changes.count { it.pressed }
                    // the centroid is NaN on the event where the last finger lifts
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if ((pointers >= 2 || state.isZoomed) && centroid.isSpecified) {
                        if (!picked) {
                            state.region = regionAt?.invoke(centroid, size)
                            picked = true
                        }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val newScale = (state.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val ratio = if (state.scale == 0f) 1f else newScale / state.scale
                        val center = state.bounds.center
                        // Keep the point under the fingers fixed while scaling, then follow the drag.
                        val anchored = state.offset + (centroid - center - state.offset) * (1f - ratio)
                        state.scale = newScale
                        state.offset = state.clamp(anchored + pan, newScale)
                        event.changes.forEach { if (it.positionChanged() && !it.changedToUp()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (picked && !state.isZoomed) state.region = null
            }
        }
    return this
        .then(gestures)
        .onSizeChanged {
            // A new window size (folding, rotating, resizing) leaves the old zoom and its region pointing at the
            // wrong place.
            if (state.size != IntSize.Zero && state.size != it) state.resetInstantly()
            state.size = it
        }
        .drawWithContent {
            val region = state.region
            if (region != null && state.isZoomed) {
                clipRect(region.left, region.top, region.right, region.bottom) { this@drawWithContent.drawContent() }
            } else {
                drawContent()
            }
        }
        .graphicsLayer {
            val center = state.bounds.center
            transformOrigin = TransformOrigin(center.x / size.width, center.y / size.height)
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offset.x
            translationY = state.offset.y
        }
}

private fun CoroutineScope.toggleZoom(state: ZoomState, tap: Offset, zoomTo: Float) {
    launch {
        if (state.isZoomed) {
            state.reset()
        } else {
            val target = (state.bounds.center - tap) * (zoomTo - 1f)
            state.animateTo(zoomTo, state.clamp(target, zoomTo))
        }
    }
}
