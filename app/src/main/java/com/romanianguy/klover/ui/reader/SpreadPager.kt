package com.romanianguy.klover.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.romanianguy.klover.data.prefs.PageTurn
import com.romanianguy.klover.data.prefs.ReadingDirection
import com.romanianguy.klover.ui.common.BookSpine
import com.romanianguy.klover.ui.common.foldSettle
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sin

// horizontally paged reader; each pager page is a Spread. Half open like a book, pages turn over the spine instead of
// sliding
@Composable
fun SpreadPager(
    pagerState: PagerState,
    spreads: List<Spread>,
    source: PageSource,
    layout: ReaderLayout,
    direction: ReadingDirection,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
    tapZones: Boolean = true,
    invertTapZones: Boolean = false,
    pageTurn: PageTurn = PageTurn.Slide,
    tapEdge: Float = 0.3f,
    preloadScreens: Int = 2,
    doubleTapScale: Float = 2.5f,
) {
    val rtl = direction == ReadingDirection.RightToLeft
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentZoomed by remember { mutableStateOf(false) }
    val turn = if (pageTurn == PageTurn.Slide && layout.hinge != null && layout.dual) PageTurn.Flip else pageTurn
    val flipping = turn == PageTurn.Flip && layout.dual
    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    LaunchedEffect(pagerState.settledPage, spreads, source, preloadScreens) {
        for (offset in 1..preloadScreens) {
            spreads.getOrNull(pagerState.settledPage + offset)?.pages?.forEach { source.prefetch(context, it) }
        }
    }

    Box(modifier.fillMaxSize().foldSettle(layout.hinge != null && layout.dual)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = rtl,
            beyondViewportPageCount = 1,
            userScrollEnabled = !currentZoomed,
            key = { spreads[it].first },
        ) { index ->
            val spread = spreads[index]
            val zoom = rememberZoomState(layout)
            // held like a book, a zoom stays on the page it started on
            val pageRegion: ((Offset, IntSize) -> Rect?)? = layout.hinge?.takeIf { layout.dual }?.let { hinge ->
                { position, size -> pageRegion(halvesOf(spreads, index, source, rtl), hinge, position, size) }
            }
            val isCurrent = pagerState.settledPage == index

            LaunchedEffect(zoom.isZoomed, isCurrent) { if (isCurrent) currentZoomed = zoom.isZoomed }
            LaunchedEffect(isCurrent) { if (!isCurrent) zoom.resetInstantly() }

            fun place() = index - position()
            // the flip needs two spreads of two pages; otherwise the pager slides
            fun pinned(): Boolean {
                if (!flipping || halvesOf(spreads, index, source, rtl) == null) return false
                return halvesOf(spreads, index + if (place() < 0f) 1 else -1, source, rtl) != null
            }
            // which half of a waiting page shows while the other turns
            fun halfShows(left: Boolean): Boolean {
                val r = place()
                if (!pinned() || abs(r) <= SETTLED) return true
                val leftShows = if (rtl) r >= 0f else r <= 0f
                return if (left) leftShows else !leftShows
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (turn == PageTurn.Fade) {
                            val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                            translationX = (if (rtl) -offset else offset) * size.width
                            alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                        } else if (pinned()) {
                            val r = place()
                            translationX = (if (rtl) r else -r) * size.width
                            // a page a whole turn away must vanish at the same closeness the landing page counts as
                            // at rest, or both draw at once
                            alpha = if (abs(r) >= 1f - SETTLED) 0f else 1f
                        }
                    }
                    .zoomable(zoom, doubleTapScale, regionAt = pageRegion, enabled = isCurrent) { position, size ->
                        // visual side: swapped tap zones go the other way
                        when (tapZone(position, size, enabled = tapZones, inverted = invertTapZones, edge = tapEdge)) {
                            TapZone.Center -> onCenterTap()
                            TapZone.Left -> scope.launch { turnPage(pagerState, spreads.size, if (rtl) +1 else -1, turn) }
                            TapZone.Right -> scope.launch { turnPage(pagerState, spreads.size, if (rtl) -1 else +1, turn) }
                        }
                    },
            ) {
                CompositionLocalProvider(LocalPageZoom provides remember(zoom) { PageZoom(zoom) { Offset.Zero } }) {
                    SpreadContent(
                        spread = spread,
                        halves = if (flipping) halvesOf(spreads, index, source, rtl) else null,
                        source = source,
                        layout = layout,
                        rtl = rtl,
                        leftFx = if (flipping) Modifier.graphicsLayer { alpha = if (halfShows(left = true)) 1f else 0f } else Modifier,
                        rightFx = if (flipping) Modifier.graphicsLayer { alpha = if (halfShows(left = false)) 1f else 0f } else Modifier,
                    )
                }
            }
        }

        // the page in the air, drawn above the pager; the two turns next to the current page stay set up so a turn
        // starts on a full page
        if (flipping) {
            val current = pagerState.currentPage
            for (lower in (current - 1)..current) {
                key(lower) {
                    val out = halvesOf(spreads, lower, source, rtl)
                    val into = halvesOf(spreads, lower + 1, source, rtl)
                    if (lower >= 0 && lower + 1 < spreads.size && out != null && into != null) {
                        FlipOverlay(out, into, lower, source, layout.hinge, rtl, position)
                    }
                }
            }
        }

        // the spine stays put while pages move over it, and is hidden while zoomed
        val hinge = layout.hinge
        val spineAlpha by animateFloatAsState(if (currentZoomed) 0f else 1f, label = "spine")
        if (layout.dual && hinge != null && spineAlpha > 0f) {
            BookSpine(hinge, Modifier.graphicsLayer { alpha = spineAlpha })
        }
    }
}

private fun pageRegion(halves: Pair<Int?, Int?>?, hinge: Rect, position: Offset, size: IntSize): Rect? {
    if (halves == null) return null
    val left = Rect(0f, 0f, hinge.left, size.height.toFloat())
    val right = Rect(hinge.right, 0f, size.width.toFloat(), size.height.toFloat())
    return when {
        halves.first == null -> right
        halves.second == null -> left
        position.x < hinge.center.x -> left
        else -> right
    }
}

private fun first(spread: Spread): Int = spread.first

private fun halvesOf(spreads: List<Spread>, index: Int, source: PageSource, rtl: Boolean): Pair<Int?, Int?>? {
    val spread = spreads.getOrNull(index) ?: return null
    if (spread.isDouble) return if (rtl) spread.pages[1] to spread.pages[0] else spread.pages[0] to spread.pages[1]
    if (source.isWide(spread.first)) return null
    val front = if (rtl) first(spread) to null else null to first(spread)
    val back = if (rtl) null to first(spread) else first(spread) to null
    return if (index == spreads.lastIndex && index != 0) back else front
}

@Composable
private fun SpreadHalves(
    left: Int?,
    right: Int?,
    source: PageSource,
    hinge: Rect?,
    leftFx: Modifier = Modifier,
    rightFx: Modifier = Modifier,
    shade: (() -> Float)? = null,
) {
    val density = LocalDensity.current
    val zoom = LocalPageZoom.current
    // each half starts at its own place in the zoomed area
    val leftZoom = remember(zoom) { zoom?.let { PageZoom(it.state) { Offset.Zero } } }
    val rightZoom = remember(zoom, hinge) {
        zoom?.let { z -> PageZoom(z.state) { hinge?.let { Offset(it.right, 0f) } ?: Offset(z.state.size.width / 2f, 0f) } }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
        if (hinge != null) {
            val leftWidth = with(density) { hinge.left.toDp() }
            val gap = with(density) { hinge.width.toDp() }
            Box(Modifier.width(leftWidth).fillMaxHeight().then(leftFx)) {
                if (left != null) CompositionLocalProvider(LocalPageZoom provides leftZoom) { source.Page(left, Alignment.CenterEnd, Modifier.fillMaxSize()) }
                if (left != null && shade != null) PageShade(shade)
            }
            Spacer(Modifier.width(gap))
            Box(Modifier.weight(1f).fillMaxHeight().then(rightFx)) {
                if (right != null) CompositionLocalProvider(LocalPageZoom provides rightZoom) { source.Page(right, Alignment.CenterStart, Modifier.fillMaxSize()) }
                if (right != null && shade != null) PageShade(shade)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxHeight().then(leftFx)) {
                if (left != null) CompositionLocalProvider(LocalPageZoom provides leftZoom) { source.Page(left, Alignment.CenterEnd, Modifier.fillMaxSize()) }
                if (left != null && shade != null) PageShade(shade)
            }
            Box(Modifier.weight(1f).fillMaxHeight().then(rightFx)) {
                if (right != null) CompositionLocalProvider(LocalPageZoom provides rightZoom) { source.Page(right, Alignment.CenterStart, Modifier.fillMaxSize()) }
                if (right != null && shade != null) PageShade(shade)
            }
        }
    }
}

@Composable
private fun PageShade(alpha: () -> Float) {
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() }.background(Color.Black))
}

@Composable
private fun SpreadContent(
    spread: Spread,
    halves: Pair<Int?, Int?>?,
    source: PageSource,
    layout: ReaderLayout,
    rtl: Boolean,
    leftFx: Modifier,
    rightFx: Modifier,
) {
    if (halves != null) {
        SpreadHalves(halves.first, halves.second, source, layout.hinge, leftFx, rightFx)
        return
    }
    if (!spread.isDouble) {
        source.Page(spread.first, Alignment.Center, Modifier.fillMaxSize())
        return
    }
    val (left, right) = if (rtl) spread.pages[1] to spread.pages[0] else spread.pages[0] to spread.pages[1]
    SpreadHalves(left, right, source, layout.hinge, leftFx, rightFx)
}

private const val SETTLED = 0.002f

private const val TURN_DEGREES = 180f

@Composable
private fun FlipOverlay(
    lowerHalves: Pair<Int?, Int?>,
    upperHalves: Pair<Int?, Int?>,
    lower: Int,
    source: PageSource,
    hinge: Rect?,
    rtl: Boolean,
    position: () -> Float,
) {
    val density = LocalDensity.current
    val camera = 22f * density.density
    val (lowerLeft, lowerRight) = lowerHalves
    val (upperLeft, upperRight) = upperHalves
    // forward, left-to-right books turn the right page to the left, right-to-left the opposite
    val outPage = if (rtl) lowerLeft else lowerRight
    val inPage = if (rtl) upperRight else upperLeft
    fun t() = (position() - lower).coerceIn(0f, 1f)

    val outOnRight = !rtl
    fun turning(out: Boolean, onRight: Boolean): Modifier = Modifier
        .graphicsLayer {
            val t = t()
            cameraDistance = camera
            transformOrigin = if (onRight) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
            val angle = if (out) t * TURN_DEGREES else (1f - t) * TURN_DEGREES
            rotationY = if (onRight) -angle else angle
            alpha = if (out) (if (t < 0.5f) 1f else 0f) else (if (t >= 0.5f) 1f else 0f)
        }
    val shade = { 0.38f * sin(t() * PI.toFloat()) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = position() - lower
                alpha = if (t > SETTLED && t < 1f - SETTLED) 1f else 0f
            },
    ) {
        if (outOnRight) {
            SpreadHalves(
                left = null,
                right = outPage,
                source = source,
                hinge = hinge,
                rightFx = turning(out = true, onRight = true),
                shade = shade,
            )
            SpreadHalves(
                left = inPage,
                right = null,
                source = source,
                hinge = hinge,
                leftFx = turning(out = false, onRight = false),
                shade = shade,
            )
        } else {
            SpreadHalves(
                left = outPage,
                right = null,
                source = source,
                hinge = hinge,
                leftFx = turning(out = true, onRight = false),
                shade = shade,
            )
            SpreadHalves(
                left = null,
                right = inPage,
                source = source,
                hinge = hinge,
                rightFx = turning(out = false, onRight = true),
                shade = shade,
            )
        }
    }
}

internal enum class TapZone { Left, Center, Right }

internal fun tapZone(
    position: Offset,
    size: IntSize,
    enabled: Boolean = true,
    inverted: Boolean = false,
    edge: Float = 0.3f,
): TapZone {
    if (!enabled || size.width <= 0) return TapZone.Center
    val fraction = position.x / size.width
    val zone = when {
        fraction < edge -> TapZone.Left
        fraction > 1f - edge -> TapZone.Right
        else -> TapZone.Center
    }
    return if (!inverted) zone else when (zone) {
        TapZone.Left -> TapZone.Right
        TapZone.Right -> TapZone.Left
        TapZone.Center -> TapZone.Center
    }
}

internal suspend fun turnPage(state: PagerState, count: Int, delta: Int, pageTurn: PageTurn = PageTurn.Slide) {
    val target = (state.currentPage + delta).coerceIn(0, count - 1)
    if (target == state.currentPage) return
    if (pageTurn == PageTurn.Instant) {
        state.scrollToPage(target)
    } else {
        state.animateScrollToPage(
            page = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = if (pageTurn == PageTurn.Flip) Spring.StiffnessLow else Spring.StiffnessMediumLow,
            ),
        )
    }
}
