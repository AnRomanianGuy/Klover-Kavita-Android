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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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

// what the event puts on the screen, drawn over the app and never in the way of a touch: webs and a spider in the top
// corners (kept in the band of the status bar, clear of the top bar), bats, pumpkins on the bottom corners and one leaning
// into the bottom right corner. With a book open on two pages this goes on the right one, the screen, and there is more of
// it on a wide window. [startX] is where that page starts; [perched] says the pumpkins sit on the floating bar instead of
// the corners
@Composable
fun SeasonalDecor(bottomInset: Dp, modifier: Modifier = Modifier, startX: Dp = 0.dp, perched: Boolean = false) {
    val event = LocalActiveEvent.current ?: return
    when (event.event) {
        SeasonalEvent.Halloween -> HalloweenDecor(bottomInset, startX, perched, modifier)
    }
}

// the left page of an open book is decorated too. It is drawn behind that page's controls, so every button, row and card
// on the page stays on top of it
@Composable
fun SeasonalPageDecor(modifier: Modifier = Modifier) {
    val event = LocalActiveEvent.current ?: return
    if (event.event != SeasonalEvent.Halloween) return
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) { HalloweenColors.of(scheme) }
    val arrive = rememberArrival()
    Canvas(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val t = arrive.value
        val dp = density
        val w = size.width
        val h = size.height
        val strand = colors.strand.copy(alpha = colors.strand.alpha * t)
        drawWeb(Offset(0f, 0f), 150 * dp, 120 * dp, 1f, strand)
        drawWeb(Offset(w, 0f), 118 * dp, 96 * dp, -1f, strand)
        // the spider hangs down the empty middle of the page, its thread running behind the profile card
        val thread = (h * 0.22f).coerceAtMost(210 * dp) * overshoot(t)
        drawSpider(Offset(w * 0.74f, 0f), thread, dp, colors.spider, strand.copy(alpha = 0.7f * t))
        val bats = listOf(
            Triple(0.18f, 0.29f, 24f), Triple(0.34f, 0.36f, 17f), Triple(0.50f, 0.3f, 21f), Triple(0.62f, 0.41f, 15f),
        )
        for ((fx, fy, span) in bats) {
            drawBat(Offset(w * fx, h * fy + 12 * dp * (1f - t)), span * dp, colors.bat.copy(alpha = t), tilt = (span.toInt() % 3 - 1) * 10f, flap = if (span > 20f) 1f else 0.6f)
        }
        // pumpkins at the foot of the page, the Continue button above them
        fun peek(x: Float, width: Float) {
            val ph = width * 0.78f
            drawPumpkin(Offset(x, h - ph * 0.2f + ph * 0.7f * (1f - t)), width, t)
        }
        peek(w * 0.16f, 58 * dp)
        peek(w * 0.16f + 50 * dp, 36 * dp)
        peek(w * 0.86f, 52 * dp)
        peek(w * 0.86f - 44 * dp, 30 * dp)
    }
}

private class HalloweenColors(val strand: Color, val spider: Color, val bat: Color) {
    companion object {
        // the colours come from the app's own: the webs are its text colour, the spider and the bats lean on its accents
        fun of(scheme: androidx.compose.material3.ColorScheme): HalloweenColors {
            val dark = scheme.surface.luminance() < 0.5f
            return HalloweenColors(
                strand = scheme.onSurface.copy(alpha = if (dark) 0.42f else 0.36f),
                spider = if (dark) lerp(Color(0xFF8D74C6), scheme.tertiary, 0.2f) else lerp(Color(0xFF45306A), scheme.tertiary, 0.2f),
                bat = lerp(Color(0xFF7A4DD6), scheme.tertiary, 0.45f),
            )
        }
    }
}

// arrives once when the screen opens and then stays as it is
@Composable
private fun rememberArrival(): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val reduced = LocalAppSettings.current.reducedMotion()
    val arrive = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (arrive.value < 1f) arrive.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
    }
    return arrive
}

@Composable
private fun HalloweenDecor(bottomInset: Dp, startX: Dp, perched: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) { HalloweenColors.of(scheme) }
    val strand = colors.strand
    val spider = colors.spider
    val bat = colors.bat
    // the top decorations stay inside the band of the status bar, so they never sit on the top bar's title or buttons
    val band = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp).coerceIn(34.dp, 64.dp)
    val arrive = rememberArrival()

    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = arrive.value
            val dp = density
            val x0 = startX.toPx()
            val room = size.width - x0
            // a wide window, or the page of an open book, has room for more
            val wide = startX.value > 0f || room > 520 * dp
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
                drawBat(Offset(x0 + room * spot.first, ry * spot.second + s * 0.4f * (1f - t)), s, bat.copy(alpha = t), tilt = ((i % 3) - 1) * 10f, flap = if (i % 2 == 0) 1f else 0.6f)
            }

            // pumpkins in the bottom corners, over what is there (on a phone they sit on the floating bar instead)
            val floor = size.height - bottomInset.toPx()
            if (!perched) {
                fun peek(x: Float, width: Float) {
                    val h = width * 0.78f
                    drawPumpkin(Offset(x, floor - h * 0.2f + h * 0.7f * (1f - t)), width, t)
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
            if (wide) {
                // one big pumpkin leaning into the bottom right corner with half of it out of the screen
                val big = 96 * dp
                drawPumpkin(
                    Offset(size.width - 2 * dp, size.height + 2 * dp + big * 0.5f * (1f - t)),
                    big,
                    t,
                    tilt = -24f,
                )
            }
        }
    }
}

// pumpkins sitting on the top corners of the thing this is put on, such as the floating bar. Over it, or with [behind] set
// peeking out from behind it
@Composable
fun Modifier.eventPerch(behind: Boolean = false): Modifier {
    val event = LocalActiveEvent.current ?: return this
    if (event.event != SeasonalEvent.Halloween) return this
    val sit: DrawScope.() -> Unit = {
        val dp = density
        drawPumpkin(Offset(26 * dp, -6 * dp), 42 * dp)
        drawPumpkin(Offset(size.width - 24 * dp, -5 * dp), 36 * dp)
        drawPumpkin(Offset(size.width - 54 * dp, 0f), 22 * dp)
    }
    return if (behind) drawBehind(sit) else drawWithContent {
        drawContent()
        sit()
    }
}

// a little more than 1 on the way, then 1
private fun overshoot(t: Float): Float {
    val c = 1.9f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

// a web in a corner, [rx] wide and [ry] tall: thick strands out from the corner, a thin thread winding round them that sags
// between the strands, fading towards the outside, with a few drops of dew and loose threads
private fun DrawScope.drawWeb(corner: Offset, rx: Float, ry: Float, dir: Float, color: Color) {
    val strands = 7
    val rings = 6
    val reach = maxOf(rx, ry)
    val fade = Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.45f)), corner, reach)
    val radial = 1.5f * density
    val capture = Stroke(width = 0.9f * density, cap = StrokeCap.Round)
    fun point(r: Float, quarter: Float) = Offset(
        corner.x + dir * rx * r * sin(quarter * PI.toFloat() / 2f),
        corner.y + ry * r * cos(quarter * PI.toFloat() / 2f),
    )
    for (i in 0 until strands) drawLine(fade, corner, point(1f, i / (strands - 1f)), radial, StrokeCap.Round)
    val dew = ArrayList<Offset>()
    for (k in 1..rings) {
        val r = k / rings.toFloat()
        val path = Path()
        val first = point(r, 0f)
        path.moveTo(first.x, first.y)
        for (i in 0 until strands - 1) {
            val from = i / (strands - 1f)
            val to = (i + 1) / (strands - 1f)
            val control = point(r * 0.84f, (from + to) / 2f)
            val end = point(r, to)
            val start = point(r, from)
            path.quadraticTo(control.x, control.y, end.x, end.y)
            // a drop hangs where the thread sags most, on some of the rings
            if ((k + i) % 3 == 0 && k > 1) dew += Offset(0.25f * start.x + 0.5f * control.x + 0.25f * end.x, 0.25f * start.y + 0.5f * control.y + 0.25f * end.y)
        }
        drawPath(path, fade, style = capture)
    }
    for (d in dew) {
        drawCircle(color.copy(alpha = (color.alpha * 0.9f).coerceAtMost(0.8f)), 2f * density, d)
        drawCircle(Color.White.copy(alpha = color.alpha * 1.3f), 0.8f * density, Offset(d.x - 0.5f * density, d.y - 0.6f * density))
    }
    // two loose threads, drooping from the edge
    for (q in listOf(0.38f, 0.74f)) {
        val from = point(1f, q)
        val to = Offset(from.x + dir * 6f * density, from.y + 15f * density)
        val loose = Path()
        loose.moveTo(from.x, from.y)
        loose.quadraticTo(from.x + dir * 1f * density, from.y + 8f * density, to.x, to.y)
        drawPath(loose, color.copy(alpha = color.alpha * 0.6f), style = capture)
    }
}

// [unit] is what a dp is worth here, smaller on a short band. A friendly cartoon spider on its thread: a round purple body
// with a highlight and a few spots, a big head with round eyes, and eight legs that arch up at the knee
private fun DrawScope.drawSpider(top: Offset, thread: Float, unit: Float, body: Color, line: Color) {
    val light = lerp(body, Color.White, 0.42f)
    val dark = lerp(body, Color.Black, 0.45f)
    drawLine(line, top, Offset(top.x, top.y + thread), 1.1f * unit, StrokeCap.Round)
    val abdomen = Offset(top.x, top.y + thread + 11 * unit)
    val head = Offset(top.x, top.y + thread + 25 * unit)

    // the legs sit behind the body: each side has four, the upper segment rising to a knee, the lower one coming down
    class Leg(val startX: Float, val startY: Float, val kneeX: Float, val kneeY: Float, val footX: Float, val footY: Float)
    val legs = listOf(
        Leg(3f, -5f, 13f, -20f, 25f, -9f),
        Leg(3.5f, -3f, 17f, -14f, 28f, 2f),
        Leg(4f, -1f, 18f, -7f, 28f, 11f),
        Leg(3.5f, 1f, 15f, -1f, 23f, 19f),
    )
    for (side in listOf(-1f, 1f)) {
        for (leg in legs) {
            val start = Offset(head.x + side * leg.startX * unit, head.y + leg.startY * unit)
            val knee = Offset(head.x + side * leg.kneeX * unit, head.y + leg.kneeY * unit)
            val foot = Offset(head.x + side * leg.footX * unit, head.y + leg.footY * unit)
            val upper = Path()
            upper.moveTo(start.x, start.y)
            upper.lineTo(knee.x, knee.y)
            val lower = Path()
            lower.moveTo(knee.x, knee.y)
            lower.quadraticTo(foot.x + side * 1.5f * unit, knee.y + (foot.y - knee.y) * 0.45f, foot.x, foot.y)
            drawPath(upper, dark, style = Stroke(2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(lower, dark, style = Stroke(2.0f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(upper, body, style = Stroke(1.9f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(lower, body, style = Stroke(1.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(light.copy(alpha = 0.7f), 1.0f * unit, knee)
        }
    }

    // the abdomen: round, shaded from the top left, with a shine and a few spots
    val ar = Size(20f * unit, 23f * unit)
    val aTop = Offset(abdomen.x - ar.width / 2f, abdomen.y - ar.height / 2f)
    drawOval(
        Brush.radialGradient(listOf(light, body, dark), Offset(abdomen.x - 4f * unit, abdomen.y - 5f * unit), 16f * unit),
        aTop,
        ar,
    )
    drawOval(dark.copy(alpha = 0.8f), aTop, ar, style = Stroke(0.9f * unit))
    drawOval(Color.White.copy(alpha = 0.45f), Offset(abdomen.x - 6.5f * unit, abdomen.y - 8f * unit), Size(4.5f * unit, 6.5f * unit))
    val spot = Color(0xFFFFA23A).copy(alpha = 0.95f)
    drawCircle(spot, 1.9f * unit, Offset(abdomen.x, abdomen.y + 1f * unit))
    drawCircle(spot, 1.4f * unit, Offset(abdomen.x - 4.2f * unit, abdomen.y + 4.5f * unit))
    drawCircle(spot, 1.4f * unit, Offset(abdomen.x + 4.2f * unit, abdomen.y + 4.5f * unit))

    // the head: big and round, with two round eyes that look down, and a small smile
    val hr = 8.6f * unit
    drawCircle(Brush.radialGradient(listOf(light, body, dark), Offset(head.x - 2.5f * unit, head.y - 3f * unit), 11f * unit), hr, head)
    drawCircle(dark.copy(alpha = 0.8f), hr, head, style = Stroke(0.9f * unit))
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(head.x + side * 3.4f * unit, head.y - 0.8f * unit)
        drawCircle(Color.White, 3.1f * unit, eye)
        drawCircle(dark.copy(alpha = 0.55f), 3.1f * unit, eye, style = Stroke(0.6f * unit))
        val pupil = Offset(eye.x - side * 0.2f * unit, eye.y + 0.8f * unit)
        drawCircle(Color(0xFF1B1226), 1.6f * unit, pupil)
        drawCircle(Color.White, 0.55f * unit, Offset(pupil.x - 0.5f * unit, pupil.y - 0.6f * unit))
    }
    val smile = Path()
    smile.moveTo(head.x - 3f * unit, head.y + 4.2f * unit)
    smile.quadraticTo(head.x, head.y + 6.4f * unit, head.x + 3f * unit, head.y + 4.2f * unit)
    drawPath(smile, dark, style = Stroke(1.1f * unit, cap = StrokeCap.Round))
}

// a bat in flight: wings with three finger bones and a scalloped edge, shaded from the body outwards, a head with ears
// and glowing eyes. [tilt] leans it, [flap] is how high the wings are held (1 up, less is more spread)
private fun DrawScope.drawBat(center: Offset, span: Float, color: Color, tilt: Float = 0f, flap: Float = 1f) {
    val light = lerp(color, Color.White, 0.28f)
    val dark = lerp(color, Color.Black, 0.5f)
    rotate(tilt, center) {
        fun p(dir: Float, x: Float, y: Float) = Offset(center.x + dir * x * span, center.y + y * span * flap.coerceAtLeast(0.4f).let { if (y < 0f) it else 0.7f + 0.3f * it })
        for (dir in listOf(-1f, 1f)) {
            val shoulder = p(dir, 0.1f, -0.02f)
            val wrist = p(dir, 0.55f, -0.36f)
            val tip1 = p(dir, 1.02f, -0.16f)
            val tip2 = p(dir, 0.86f, 0.17f)
            val tip3 = p(dir, 0.6f, 0.3f)
            val tip4 = p(dir, 0.33f, 0.24f)
            val back = p(dir, 0.1f, 0.2f)
            val wing = Path()
            wing.moveTo(shoulder.x, shoulder.y)
            wing.quadraticTo(p(dir, 0.34f, -0.34f).x, p(dir, 0.34f, -0.34f).y, wrist.x, wrist.y)
            wing.quadraticTo(p(dir, 0.82f, -0.38f).x, p(dir, 0.82f, -0.38f).y, tip1.x, tip1.y)
            // the edge between the finger tips curves in, as a bat's skin does
            wing.quadraticTo(p(dir, 0.88f, 0.02f).x, p(dir, 0.88f, 0.02f).y, tip2.x, tip2.y)
            wing.quadraticTo(p(dir, 0.72f, 0.12f).x, p(dir, 0.72f, 0.12f).y, tip3.x, tip3.y)
            wing.quadraticTo(p(dir, 0.46f, 0.14f).x, p(dir, 0.46f, 0.14f).y, tip4.x, tip4.y)
            wing.quadraticTo(p(dir, 0.2f, 0.1f).x, p(dir, 0.2f, 0.1f).y, back.x, back.y)
            wing.close()
            drawPath(wing, Brush.linearGradient(listOf(dark, color, light), Offset(center.x, center.y), Offset(center.x + dir * span, center.y - 0.2f * span)))
            val bone = Stroke(width = (span * 0.035f).coerceAtLeast(0.8f), cap = StrokeCap.Round)
            for (tip in listOf(tip1, tip2, tip3, tip4)) {
                val b = Path()
                b.moveTo(wrist.x, wrist.y)
                b.lineTo(tip.x, tip.y)
                drawPath(b, dark.copy(alpha = 0.7f), style = bone)
            }
        }
        drawOval(Brush.verticalGradient(listOf(light, dark), center.y - 0.2f * span, center.y + 0.26f * span), Offset(center.x - 0.1f * span, center.y - 0.16f * span), Size(0.2f * span, 0.42f * span))
        drawCircle(Brush.radialGradient(listOf(light, color), Offset(center.x - 0.02f * span, center.y - 0.2f * span), 0.12f * span), 0.1f * span, Offset(center.x, center.y - 0.17f * span))
        for (side in listOf(-1f, 1f)) {
            val ear = Path()
            ear.moveTo(center.x + side * 0.02f * span, center.y - 0.24f * span)
            ear.lineTo(center.x + side * 0.1f * span, center.y - 0.4f * span)
            ear.lineTo(center.x + side * 0.12f * span, center.y - 0.2f * span)
            ear.close()
            drawPath(ear, color)
            drawCircle(Color(0xFFE8F26B), (span * 0.018f).coerceAtLeast(0.7f), Offset(center.x + side * 0.04f * span, center.y - 0.18f * span))
        }
    }
}

// a lit jack-o'-lantern: a wide body of five ribs, each shaded round with a highlight and a darker edge, a curled stem with
// a tendril and a leaf, and a face lit from inside by a candle. [width] is its full width, [alpha] fades it in
internal fun DrawScope.drawPumpkin(center: Offset, width: Float, alpha: Float = 1f, tilt: Float = 0f) {
    if (tilt == 0f) drawUprightPumpkin(center, width, alpha) else rotate(tilt, center) { drawUprightPumpkin(center, width, alpha) }
}

private fun DrawScope.drawUprightPumpkin(center: Offset, width: Float, alpha: Float) {
    val h = width * 0.78f
    val top = center.y - h / 2f
    fun c(argb: Long, a: Float = 1f) = Color(argb).copy(alpha = (alpha * a).coerceIn(0f, 1f))

    // the glow of the candle on its surroundings, and the shadow it sits in
    drawCircle(Brush.radialGradient(listOf(c(0xFFFFA43A, 0.2f), Color.Transparent), center, width * 0.66f), width * 0.66f, center)
    val shadowTop = Offset(center.x - width * 0.46f, center.y + h * 0.36f)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.4f * alpha), Color.Transparent), Offset(center.x, shadowTop.y + h * 0.1f), width * 0.5f),
        shadowTop,
        Size(width * 0.92f, h * 0.22f),
    )

    // the ribs, back to front: outer ones darkest, the one in the middle lightest; each is shaded round, light from above left
    class Rib(val at: Float, val w: Float, val light: Long, val mid: Long, val dark: Long)
    val ribs = listOf(
        Rib(-0.34f, 0.36f, 0xFFE8841A, 0xFFC25A0B, 0xFF8F3E05),
        Rib(0.34f, 0.36f, 0xFFE8841A, 0xFFC25A0B, 0xFF8F3E05),
        Rib(-0.18f, 0.44f, 0xFFF89A2A, 0xFFDB6E10, 0xFFA64908),
        Rib(0.18f, 0.44f, 0xFFF89A2A, 0xFFDB6E10, 0xFFA64908),
        Rib(0f, 0.47f, 0xFFFFB640, 0xFFEE8419, 0xFFBC5A0C),
    )
    val seam = (width * 0.016f).coerceAtLeast(1f)
    for (rib in ribs) {
        val rw = width * rib.w
        val left = center.x + width * rib.at - rw / 2f
        val brush = Brush.radialGradient(
            listOf(c(rib.light), c(rib.mid), c(rib.dark)),
            Offset(left + rw * 0.36f, top + h * 0.3f),
            maxOf(rw, h) * 0.78f,
        )
        drawOval(brush, Offset(left, top), Size(rw, h))
        // the darker rim of the rib, and a thin light line inside it where the light catches
        drawOval(c(0xFF8C3A05, 0.8f), Offset(left, top), Size(rw, h), style = Stroke(seam))
        drawOval(c(0xFFFFD28A, 0.22f), Offset(left + rw * 0.1f, top + h * 0.06f), Size(rw * 0.8f, h * 0.88f), style = Stroke(seam * 0.8f))
    }
    // a shine on the middle rib
    drawOval(Color.White.copy(alpha = 0.2f * alpha), Offset(center.x - width * 0.2f, top + h * 0.1f), Size(width * 0.12f, h * 0.24f))

    // the dip around the stem
    drawOval(c(0xFF7A3304, 0.55f), Offset(center.x - width * 0.14f, top - h * 0.03f), Size(width * 0.28f, h * 0.13f))

    // the stem: curved, shaded across, cut at the top; a tendril that curls and a leaf
    val stem = Path()
    stem.moveTo(center.x - width * 0.07f, top + h * 0.05f)
    stem.quadraticTo(center.x - width * 0.06f, top - h * 0.14f, center.x + width * 0.06f, top - h * 0.2f)
    stem.lineTo(center.x + width * 0.15f, top - h * 0.12f)
    stem.quadraticTo(center.x + width * 0.05f, top - h * 0.05f, center.x + width * 0.07f, top + h * 0.05f)
    stem.close()
    drawPath(
        stem,
        Brush.horizontalGradient(listOf(c(0xFF86AB45), c(0xFF4F7A28), c(0xFF2F521A)), center.x - width * 0.07f, center.x + width * 0.15f),
    )
    drawOval(c(0xFFB5D468), Offset(center.x + width * 0.045f, top - h * 0.24f), Size(width * 0.13f, h * 0.1f))
    val tendril = Path()
    tendril.moveTo(center.x + width * 0.08f, top - h * 0.02f)
    tendril.cubicTo(
        center.x + width * 0.3f, top - h * 0.1f,
        center.x + width * 0.34f, top + h * 0.14f,
        center.x + width * 0.24f, top + h * 0.1f,
    )
    tendril.cubicTo(
        center.x + width * 0.19f, top + h * 0.08f,
        center.x + width * 0.22f, top + h * 0.01f,
        center.x + width * 0.27f, top + h * 0.03f,
    )
    drawPath(tendril, c(0xFF4F7A28), style = Stroke(width * 0.022f, cap = StrokeCap.Round))
    val leaf = Path()
    leaf.moveTo(center.x - width * 0.06f, top + h * 0.0f)
    leaf.quadraticTo(center.x - width * 0.2f, top - h * 0.16f, center.x - width * 0.36f, top - h * 0.05f)
    leaf.quadraticTo(center.x - width * 0.22f, top + h * 0.1f, center.x - width * 0.06f, top + h * 0.0f)
    leaf.close()
    drawPath(leaf, Brush.linearGradient(listOf(c(0xFF86AB45), c(0xFF3F6420)), Offset(center.x - width * 0.06f, top - h * 0.1f), Offset(center.x - width * 0.36f, top))
    )
    val vein = Path()
    vein.moveTo(center.x - width * 0.07f, top - h * 0.0f)
    vein.quadraticTo(center.x - width * 0.2f, top - h * 0.06f, center.x - width * 0.33f, top - h * 0.05f)
    drawPath(vein, c(0xFF2F521A, 0.8f), style = Stroke(width * 0.01f, cap = StrokeCap.Round))

    // the light of the candle spilling over the face
    val face = Offset(center.x, center.y + h * 0.04f)
    drawCircle(Brush.radialGradient(listOf(c(0xFFFFC94A, 0.3f), Color.Transparent), face, width * 0.4f), width * 0.4f, face)

    // eyes, nose and mouth lit from inside: a soft halo, the flame colour, a bright core
    fun lit(path: Path, centroid: Offset) {
        drawPath(path, c(0xFFFFB02E, 0.28f), style = Stroke(width * 0.07f, join = StrokeJoin.Round))
        drawPath(path, c(0xFFFFCB4F))
        scale(0.55f, centroid) { drawPath(path, c(0xFFFFF4BC)) }
    }
    fun triangle(a: Offset, b: Offset, d: Offset): Path {
        val p = Path()
        p.moveTo(a.x, a.y)
        p.lineTo(b.x, b.y)
        p.lineTo(d.x, d.y)
        p.close()
        return p
    }
    for (side in listOf(-1f, 1f)) {
        val ex = center.x + side * width * 0.16f
        val ey = center.y - h * 0.1f
        val a = Offset(ex - side * width * 0.065f, ey + width * 0.07f)
        val b = Offset(ex + side * width * 0.075f, ey + width * 0.05f)
        val d = Offset(ex + side * width * 0.01f, ey - width * 0.075f)
        lit(triangle(a, b, d), Offset((a.x + b.x + d.x) / 3f, (a.y + b.y + d.y) / 3f))
    }
    val noseA = Offset(center.x - width * 0.035f, center.y + h * 0.05f)
    val noseB = Offset(center.x + width * 0.035f, center.y + h * 0.05f)
    val noseC = Offset(center.x, center.y - h * 0.02f)
    lit(triangle(noseA, noseB, noseC), Offset(center.x, center.y + h * 0.027f))
    val mouth = Path()
    val left = center.x - width * 0.26f
    val my = center.y + h * 0.15f
    mouth.moveTo(left, my)
    val teeth = 6
    for (i in 1..teeth) {
        val x = left + width * 0.52f * i / teeth
        mouth.lineTo(x, if (i % 2 == 1) my + width * 0.075f else my)
    }
    mouth.quadraticTo(center.x, center.y + h * 0.56f, left, my)
    mouth.close()
    lit(mouth, Offset(center.x, my + h * 0.1f))
}
