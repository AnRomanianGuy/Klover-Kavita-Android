package com.romanianguy.klover.ui.events

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.events.SeasonalEvent
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.reducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Rind = Color(0xFFB04E08)
private val Stem = Color(0xFF5B8A32)
private val StemDark = Color(0xFF3F6420)
private val Glow = Color(0xFFFFE08A)

// what the event puts on the screen, drawn over the app and never in the way of a touch: webs and spiders in the top
// corners (kept in the band of the status bar, clear of the top bar), pumpkins on the bottom corners and bats. With a
// book open on two pages it all goes on the right one, the screen, and there is more of it on a wide window. [startX] is
// where that page starts; [perched] says the pumpkins sit on the floating bar instead of the corners
@Composable
fun SeasonalDecor(
    layer: DecorLayer,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    startX: Dp = 0.dp,
    perched: Boolean = false,
) {
    val event = LocalActiveEvent.current ?: return
    when (event.event) {
        SeasonalEvent.Halloween -> HalloweenDecor(layer, bottomInset, startX, perched, modifier)
    }
}

// the top of the screen goes over the app (it stays in the band of the status bar, clear of every button); the bottom goes
// behind it, so cards and buttons are on top of the pumpkins and only a bit of each shows
enum class DecorLayer { Top, Bottom }

@Composable
private fun HalloweenDecor(layer: DecorLayer, bottomInset: Dp, startX: Dp, perched: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    // the colours come from the app's own: the webs are its text colour, the spider and the bats lean on its accents
    val strand = scheme.onSurface.copy(alpha = if (dark) 0.42f else 0.36f)
    val spider = scheme.onSurfaceVariant
    val bat = lerp(Color(0xFF7A4DD6), scheme.tertiary, 0.45f)
    val reduced = LocalAppSettings.current.reducedMotion()
    // the top decorations stay inside the band of the status bar, so they never sit on the top bar's title or buttons
    val band = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp).coerceIn(34.dp, 64.dp)
    // arrives once when the screen opens and then stays as it is
    val arrive = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (arrive.value < 1f) arrive.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
    }

    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
            val t = arrive.value
            val dp = density
            val x0 = startX.toPx()
            val room = size.width - x0
            // a wide window, or the page of an open book, has room for more
            val wide = startX.value > 0f || room > 520 * dp
            if (layer == DecorLayer.Top) {
                val ry = band.toPx()
                val color = strand.copy(alpha = strand.alpha * t)
                drawWeb(Offset(x0, 0f), (if (wide) 150 else 112) * dp, ry, 1f, color)
                drawWeb(Offset(size.width, 0f), (if (wide) 120 else 84) * dp, ry, -1f, color)

                // the spider lowers itself on its thread and swings a little past where it stops
                val scale = (band.value / 54f).coerceIn(0.62f, 1f)
                val thread = ry * 0.3f * overshoot(t)
                drawSpider(Offset(x0 + (if (wide) 46 else 84) * dp, 0f), thread, dp * scale, spider, color.copy(alpha = 0.7f * t))

                val spots = if (wide) {
                    listOf(0.26f to 0.34f, 0.34f to 0.62f, 0.42f to 0.26f, 0.5f to 0.58f, 0.57f to 0.3f)
                } else {
                    listOf(0.38f to 0.34f, 0.47f to 0.62f, 0.55f to 0.26f)
                }
                for ((i, spot) in spots.withIndex()) {
                    val s = (if (i % 2 == 0) 22f else 16f) * dp * scale
                    drawBat(Offset(x0 + room * spot.first, ry * spot.second + s * 0.4f * (1f - t)), s, bat.copy(alpha = t))
                }

                if (wide) {
                    // one big pumpkin leaning into the bottom right corner with half of it out of the screen: the corner
                    // is empty margin, so it is the one that is not hidden behind the screen
                    val big = 96 * dp
                    drawPumpkin(
                        Offset(size.width - 2 * dp, size.height + 2 * dp + big * 0.5f * (1f - t)),
                        big,
                        t,
                        tilt = -24f,
                    )
                }
            }

            if (layer == DecorLayer.Bottom && !perched) {
                // they peek up over the bottom edge, so they hide little of what is on the screen
                fun peek(x: Float, width: Float) {
                    val h = width * 0.78f
                    drawPumpkin(Offset(x, size.height - h * 0.2f + h * 0.7f * (1f - t)), width, t)
                }
                peek(x0 + 46 * dp, 54 * dp)
                peek(x0 + 94 * dp, 34 * dp)
                if (wide) {
                    peek(size.width - 112 * dp, 38 * dp)
                    peek(size.width - 148 * dp, 26 * dp)
                    peek(x0 + 126 * dp, 28 * dp)
                } else {
                    peek(size.width - 50 * dp, 58 * dp)
                }
            }
        }
    }
}

// pumpkins peeking out from behind the top corners of the thing this is put on, such as the floating bar: it stays on top
@Composable
fun Modifier.eventPerch(): Modifier {
    val event = LocalActiveEvent.current ?: return this
    if (event.event != SeasonalEvent.Halloween) return this
    return drawBehind {
        val dp = density
        drawPumpkin(Offset(26 * dp, -3 * dp), 42 * dp)
        drawPumpkin(Offset(size.width - 24 * dp, -2 * dp), 36 * dp)
        drawPumpkin(Offset(size.width - 54 * dp, 3 * dp), 22 * dp)
    }
}

// a little more than 1 on the way, then 1
private fun overshoot(t: Float): Float {
    val c = 1.9f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

// a web in a corner, [rx] wide and [ry] tall: strands out from the corner, and rings joining them that sag towards it
private fun DrawScope.drawWeb(corner: Offset, rx: Float, ry: Float, dir: Float, color: Color) {
    val strands = 6
    val rings = 5
    val width = 1.1f * density
    fun point(r: Float, quarter: Float) = Offset(
        corner.x + dir * rx * r * sin(quarter * PI.toFloat() / 2f),
        corner.y + ry * r * cos(quarter * PI.toFloat() / 2f),
    )
    for (i in 0 until strands) drawLine(color, corner, point(1f, i / (strands - 1f)), width, StrokeCap.Round)
    val stroke = Stroke(width = width, cap = StrokeCap.Round)
    for (k in 1..rings) {
        val r = k / rings.toFloat()
        val path = Path()
        path.moveTo(point(r, 0f).x, point(r, 0f).y)
        for (i in 0 until strands - 1) {
            val from = i / (strands - 1f)
            val to = (i + 1) / (strands - 1f)
            val control = point(r * 0.84f, (from + to) / 2f)
            val end = point(r, to)
            path.quadraticTo(control.x, control.y, end.x, end.y)
        }
        drawPath(path, color, style = stroke)
    }
}

// [unit] is what a dp is worth here, smaller on a short band
private fun DrawScope.drawSpider(top: Offset, thread: Float, unit: Float, body: Color, line: Color) {
    drawLine(line, top, Offset(top.x, top.y + thread), 1f * unit)
    val abdomen = Offset(top.x, top.y + thread + 8 * unit)
    val head = Offset(top.x, top.y + thread + 19 * unit)
    val legs = Stroke(width = 1.4f * unit, cap = StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        for (i in 0 until 4) {
            val start = Offset(head.x + side * 2f * unit, head.y - 2f * unit + i * 1.5f * unit)
            val knee = Offset(head.x + side * (13f + i * 1.5f) * unit, head.y - 9f * unit + i * 4f * unit)
            val foot = Offset(head.x + side * (22f + i * 1.5f) * unit, head.y + (4f + i * 6f) * unit)
            val path = Path()
            path.moveTo(start.x, start.y)
            path.lineTo(knee.x, knee.y)
            path.lineTo(foot.x, foot.y)
            drawPath(path, body, style = legs)
        }
    }
    drawCircle(body, 7.5f * unit, abdomen)
    drawCircle(body, 5f * unit, head)
    drawCircle(Color(0xFFFF6B4A), 1.3f * unit, Offset(head.x - 1.9f * unit, head.y + 1.5f * unit))
    drawCircle(Color(0xFFFF6B4A), 1.3f * unit, Offset(head.x + 1.9f * unit, head.y + 1.5f * unit))
}

private fun DrawScope.drawBat(center: Offset, span: Float, color: Color) {
    fun wing(dir: Float): Path {
        val p = Path()
        p.moveTo(center.x + dir * 0.08f * span, center.y)
        p.quadraticTo(center.x + dir * 0.5f * span, center.y - 0.62f * span, center.x + dir * span, center.y - 0.18f * span)
        p.quadraticTo(center.x + dir * 0.86f * span, center.y + 0.08f * span, center.x + dir * 0.7f * span, center.y + 0.04f * span)
        p.quadraticTo(center.x + dir * 0.55f * span, center.y + 0.32f * span, center.x + dir * 0.4f * span, center.y + 0.14f * span)
        p.quadraticTo(center.x + dir * 0.24f * span, center.y + 0.42f * span, center.x + dir * 0.08f * span, center.y + 0.2f * span)
        p.close()
        return p
    }
    drawPath(wing(1f), color)
    drawPath(wing(-1f), color)
    drawOval(color, Offset(center.x - 0.1f * span, center.y - 0.16f * span), Size(0.2f * span, 0.4f * span))
    val ears = Path()
    ears.moveTo(center.x - 0.1f * span, center.y - 0.12f * span)
    ears.lineTo(center.x - 0.07f * span, center.y - 0.3f * span)
    ears.lineTo(center.x, center.y - 0.15f * span)
    ears.lineTo(center.x + 0.07f * span, center.y - 0.3f * span)
    ears.lineTo(center.x + 0.1f * span, center.y - 0.12f * span)
    ears.close()
    drawPath(ears, color)
}

// a lit jack-o'-lantern: a wide body of five ribs shaded from light on top to dark below, a curled stem, and a glowing
// face. [width] is its full width, [alpha] fades it in
internal fun DrawScope.drawPumpkin(center: Offset, width: Float, alpha: Float = 1f, tilt: Float = 0f) {
    if (tilt == 0f) drawUprightPumpkin(center, width, alpha) else rotate(tilt, center) { drawUprightPumpkin(center, width, alpha) }
}

private fun DrawScope.drawUprightPumpkin(center: Offset, width: Float, alpha: Float) {
    val h = width * 0.78f
    val top = center.y - h / 2f
    fun c(argb: Long) = Color(argb).copy(alpha = alpha)

    // the glow stays inside a circle that the corner of the screen does not cut
    drawCircle(
        Brush.radialGradient(listOf(c(0xFFF28C1C).copy(alpha = 0.16f * alpha), Color.Transparent), center, width * 0.62f),
        width * 0.62f,
        center,
    )
    // where it sits
    drawOval(Color.Black.copy(alpha = 0.28f * alpha), Offset(center.x - width * 0.44f, center.y + h * 0.4f), Size(width * 0.88f, h * 0.16f))

    // the ribs, back to front: outer ones darker, the middle one in front and lightest
    val ribs = listOf(
        Triple(-0.33f, 0.36f, longArrayOf(0xFFD9700F, 0xFFA84D08)),
        Triple(0.33f, 0.36f, longArrayOf(0xFFD9700F, 0xFFA84D08)),
        Triple(-0.17f, 0.44f, longArrayOf(0xFFEE8618, 0xFFC4600B)),
        Triple(0.17f, 0.44f, longArrayOf(0xFFEE8618, 0xFFC4600B)),
        Triple(0f, 0.46f, longArrayOf(0xFFFFA733, 0xFFE57A14)),
    )
    for ((offset, w, colors) in ribs) {
        val rw = width * w
        val left = center.x + width * offset - rw / 2f
        drawOval(
            Brush.verticalGradient(listOf(c(colors[0]), c(colors[1])), startY = top, endY = top + h),
            Offset(left, top),
            Size(rw, h),
        )
        drawOval(Rind.copy(alpha = 0.85f * alpha), Offset(left, top), Size(rw, h), style = Stroke(width = (width * 0.018f).coerceAtLeast(1f)))
    }
    // a soft shine
    drawOval(Color.White.copy(alpha = 0.16f * alpha), Offset(center.x - width * 0.2f, top + h * 0.12f), Size(width * 0.14f, h * 0.2f))

    // the stem, bent to one side
    val stem = Path()
    stem.moveTo(center.x - width * 0.06f, top + h * 0.06f)
    stem.quadraticTo(center.x - width * 0.05f, top - h * 0.12f, center.x + width * 0.07f, top - h * 0.18f)
    stem.lineTo(center.x + width * 0.13f, top - h * 0.1f)
    stem.quadraticTo(center.x + width * 0.04f, top - h * 0.04f, center.x + width * 0.06f, top + h * 0.06f)
    stem.close()
    drawPath(stem, Brush.verticalGradient(listOf(c(0xFF5B8A32), c(0xFF3F6420)), startY = top - h * 0.18f, endY = top + h * 0.06f))

    // the face: slanted eyes, a nose and a toothy mouth, lit from inside
    val lit = Glow.copy(alpha = alpha)
    val halo = Glow.copy(alpha = 0.22f * alpha)
    fun triangle(a: Offset, b: Offset, cc: Offset): Path {
        val p = Path()
        p.moveTo(a.x, a.y)
        p.lineTo(b.x, b.y)
        p.lineTo(cc.x, cc.y)
        p.close()
        return p
    }
    for (side in listOf(-1f, 1f)) {
        val ex = center.x + side * width * 0.16f
        val ey = center.y - h * 0.1f
        val eye = triangle(
            Offset(ex - side * width * 0.06f, ey + width * 0.07f),
            Offset(ex + side * width * 0.07f, ey + width * 0.05f),
            Offset(ex + side * width * 0.01f, ey - width * 0.07f),
        )
        drawPath(eye, halo, style = Stroke(width = width * 0.05f))
        drawPath(eye, lit)
    }
    val nose = triangle(
        Offset(center.x - width * 0.035f, center.y + h * 0.05f),
        Offset(center.x + width * 0.035f, center.y + h * 0.05f),
        Offset(center.x, center.y - h * 0.02f),
    )
    drawPath(nose, lit)
    val mouth = Path()
    val left = center.x - width * 0.25f
    val my = center.y + h * 0.14f
    mouth.moveTo(left, my)
    val teeth = 6
    for (i in 1..teeth) {
        val x = left + width * 0.5f * i / teeth
        mouth.lineTo(x, if (i % 2 == 1) my + width * 0.07f else my)
    }
    mouth.quadraticTo(center.x, center.y + h * 0.52f, left, my)
    mouth.close()
    drawPath(mouth, halo, style = Stroke(width = width * 0.05f))
    drawPath(mouth, lit)
}
