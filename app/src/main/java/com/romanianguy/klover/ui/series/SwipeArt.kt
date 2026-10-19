package com.romanianguy.klover.ui.series

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.sin

// the art that grows out of the card edge: a clover blooms for read (and wilts for unread), a book drops into a tray
// for download
// (and puffs out when removed). Drawn here so it follows the finger

private fun leafPath(): Path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(-0.55f, -0.18f, -0.78f, -0.72f, -0.40f, -0.92f)
    cubicTo(-0.20f, -1.02f, 0.00f, -0.90f, 0.00f, -0.74f)
    cubicTo(0.00f, -0.90f, 0.20f, -1.02f, 0.40f, -0.92f)
    cubicTo(0.78f, -0.72f, 0.55f, -0.18f, 0f, 0f)
    close()
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun easeOutCubic(t: Float): Float {
    val x = 1f - t
    return 1f - x * x * x
}

@Composable
internal fun SwipeArt(
    action: SwipeAction,
    reveal: Float,
    pop: Float,
    burst: Float,
    wiggle: Float,
    color: Color,
    accents: List<Color>,
    modifier: Modifier = Modifier,
) {
    val leaf = remember { leafPath() }
    Canvas(modifier.graphicsLayer { rotationZ = wiggle }) {
        when (action) {
            SwipeAction.MarkRead -> drawClover(leaf, reveal, pop, wilt = false, color, accents)
            SwipeAction.MarkUnread -> drawClover(leaf, reveal, pop, wilt = true, color, accents)
            SwipeAction.Download -> drawBook(reveal, pop, leaving = false, color, accents)
            SwipeAction.RemoveDownload -> drawBook(reveal, pop, leaving = true, color, accents)
        }
        drawBurst(burst, accents)
    }
}

private fun DrawScope.drawClover(
    leaf: Path,
    reveal: Float,
    pop: Float,
    wilt: Boolean,
    color: Color,
    accents: List<Color>,
) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f - s * 0.04f
    val tone = if (wilt) color.copy(alpha = 0.55f + 0.45f * (1f - pop.coerceIn(0f, 1f))) else color

    // The stem grows with the pull.
    val stem = Path().apply {
        moveTo(cx, cy)
        quadraticTo(cx + s * 0.02f, cy + s * 0.26f, cx + s * 0.20f, cy + s * 0.44f)
    }
    drawTrimmed(stem, reveal, tone, Stroke(width = s * 0.07f, cap = StrokeCap.Round))

    val leafSize = s * 0.34f
    for (i in 0 until 4) {
        val t = (reveal * 1.8f - i * 0.30f).coerceIn(0f, 1f)
        if (t <= 0f) continue
        val droop = if (wilt) 1f - 0.38f * pop else 1f
        val scale = easeOutBack(t) * droop * (1f + 0.10f * pop) * leafSize
        withTransform({
            translate(cx, cy)
            rotate(i * 90f + 90f * pop, Offset.Zero)
            // The tips stay a little apart so the four leaves read as leaves, not as one blob.
            translate(0f, -s * 0.045f)
            scale(scale, scale, Offset.Zero)
        }) {
            drawPath(leaf, tone)
        }
    }
    // A bright heart in the middle once it has bloomed.
    if (!wilt && pop > 0f) {
        drawCircle(accents.firstOrNull() ?: color, radius = s * 0.07f * pop.coerceIn(0f, 1.3f), center = Offset(cx, cy))
    }
}

private fun DrawScope.drawBook(reveal: Float, pop: Float, leaving: Boolean, color: Color, accents: List<Color>) {
    val s = size.minDimension
    val width = s * 0.36f
    val height = s * 0.44f
    val left = (size.width - width) / 2f
    val line = s * 0.06f

    val tray = Path().apply {
        moveTo(s * 0.16f, s * 0.66f)
        lineTo(s * 0.16f, s * 0.84f)
        lineTo(s * 0.84f, s * 0.84f)
        lineTo(s * 0.84f, s * 0.66f)
    }
    drawTrimmed(
        tray,
        if (leaving) 1f else reveal,
        color,
        Stroke(width = s * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    val restTop = s * 0.30f
    val top: Float
    val alpha: Float
    val tilt: Float
    val squish: Float
    if (!leaving) {
        top = lerp(-s * 0.10f, restTop, easeOutCubic(reveal.coerceIn(0f, 1f))) + s * 0.035f * pop
        alpha = (reveal * 1.6f).coerceIn(0f, 1f)
        tilt = (1f - reveal.coerceIn(0f, 1f)) * -14f
        squish = 1f - 0.08f * pop
    } else {
        top = restTop - s * 0.55f * pop
        alpha = ((1f - pop * 1.1f).coerceIn(0f, 1f)) * (reveal * 2f).coerceIn(0f, 1f)
        tilt = 18f * pop
        squish = 1f
    }
    if (alpha <= 0f) return

    val ink = color.copy(alpha = alpha)
    withTransform({
        rotate(tilt, Offset(size.width / 2f, top + height / 2f))
        scale(1f, squish, Offset(size.width / 2f, top + height))
    }) {
        drawRoundRect(
            ink,
            Offset(left, top),
            Size(width, height),
            CornerRadius(s * 0.06f),
            style = Stroke(width = line, join = StrokeJoin.Round),
        )
        drawLine(
            ink,
            Offset(left + width * 0.30f, top + height * 0.10f),
            Offset(left + width * 0.30f, top + height * 0.90f),
            strokeWidth = line * 0.8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            ink,
            Offset(left + width * 0.52f, top + height * 0.36f),
            Offset(left + width * 0.80f, top + height * 0.36f),
            strokeWidth = line * 0.8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            ink,
            Offset(left + width * 0.52f, top + height * 0.56f),
            Offset(left + width * 0.72f, top + height * 0.56f),
            strokeWidth = line * 0.8f,
            cap = StrokeCap.Round,
        )
        // A ribbon, for a little colour.
        val ribbon = Path().apply {
            moveTo(left + width * 0.62f, top)
            lineTo(left + width * 0.62f, top + height * 0.28f)
            lineTo(left + width * 0.69f, top + height * 0.21f)
            lineTo(left + width * 0.76f, top + height * 0.28f)
            lineTo(left + width * 0.76f, top)
            close()
        }
        drawPath(ribbon, (accents.getOrNull(1) ?: color).copy(alpha = alpha))
    }
}

private fun DrawScope.drawBurst(burst: Float, accents: List<Color>) {
    if (burst <= 0f || burst >= 1f || accents.isEmpty()) return
    val s = size.minDimension
    val fade = 1f - burst
    val reach = s * (0.40f + 0.55f * easeOutCubic(burst))
    for (k in 0 until 8) {
        val angle = Math.toRadians(k * 45.0 + 11.0)
        val at = Offset(center.x + (cos(angle) * reach).toFloat(), center.y + (sin(angle) * reach).toFloat())
        val tint = accents[k % accents.size].copy(alpha = fade)
        if (k % 2 == 0) {
            drawCircle(tint, radius = s * 0.015f + s * 0.05f * fade, center = at)
        } else {
            val arm = s * 0.07f * (0.4f + fade)
            drawLine(
                tint,
                Offset(at.x - arm, at.y),
                Offset(at.x + arm, at.y),
                strokeWidth = s * 0.03f,
                cap = StrokeCap.Round,
            )
            drawLine(
                tint,
                Offset(at.x, at.y - arm),
                Offset(at.x, at.y + arm),
                strokeWidth = s * 0.03f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawTrimmed(path: Path, fraction: Float, color: Color, stroke: Stroke) {
    if (fraction <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), part, true)
    drawPath(part, color, style = stroke)
}
