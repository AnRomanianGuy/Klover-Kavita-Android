package com.romanianguy.klover.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.stats.ACCURATE_BAND
import com.romanianguy.klover.data.stats.ACTIVE_DAY_SECONDS
import com.romanianguy.klover.data.stats.LeftText
import com.romanianguy.klover.data.stats.PaceKind
import com.romanianguy.klover.data.stats.StreakStatus
import com.romanianguy.klover.data.stats.leftText
import com.romanianguy.klover.data.stats.secondsLeft
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.RollingText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class LeftScope { Chapter, Book, Section }

internal class ReadAloudLeft(val seconds: Long, val scope: LeftScope)

private val InnerCorner = 6.dp

private class PillShape(private val joined: State<Boolean>, private val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val full = size.height / 2f
        val inner = with(density) { InnerCorner.toPx() }.coerceAtMost(full)
        val together = joined.value
        val onTheLeft = if (layoutDirection == LayoutDirection.Ltr) left else !left
        val leftCorner = CornerRadius(if (together && !onTheLeft) inner else full)
        val rightCorner = CornerRadius(if (together && onTheLeft) inner else full)
        return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), leftCorner, rightCorner, rightCorner, leftCorner))
    }
}

@Composable
private fun PillRow(joined: MutableState<Boolean>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth().padding(vertical = 4.dp)) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val shown = measurables.map { it.measure(loose) }.filter { it.width > 0 }
        val gap = 2.dp.roundToPx()
        val rowGap = 6.dp.roundToPx()
        val total = shown.sumOf { it.width } + gap * (shown.size - 1).coerceAtLeast(0)
        val sideBySide = total <= constraints.maxWidth
        val together = sideBySide && shown.size > 1
        if (joined.value != together) joined.value = together
        val width = constraints.maxWidth
        val height = if (sideBySide) shown.maxOfOrNull { it.height } ?: 0 else shown.sumOf { it.height } + rowGap * (shown.size - 1)
        layout(width, height) {
            if (sideBySide) {
                var x = (width - total) / 2
                for (p in shown) {
                    p.placeRelative(x, (height - p.height) / 2)
                    x += p.width + gap
                }
            } else {
                var y = 0
                for (p in shown) {
                    p.placeRelative((width - p.width) / 2, y)
                    y += p.height + rowGap
                }
            }
        }
    }
}

private val PillHeight = 36.dp

private const val FLICKER_MS = 520f

private const val BURN_IN_PILL_MS = 1_800L
private const val BURN_ALONE_MS = 2_800L

private val FlameOrange = Color(0xFFFF9100)
private val FlameAmber = Color(0xFFFFC400)

@Composable
internal fun ReaderInsightsLine(
    viewModel: ReaderViewModel,
    kind: PaceKind,
    unitsLeft: Float?,
    scope: LeftScope,
    progress: Float,
    modifier: Modifier = Modifier,
    readAloud: ReadAloudLeft? = null,
) {
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val perUnit by viewModel.secondsPerUnit(kind).collectAsStateWithLifecycle()
    val calibration by viewModel.calibration(kind).collectAsStateWithLifecycle()
    val smoothed by viewModel.smoothedLeft.collectAsStateWithLifecycle()
    val raw = unitsLeft?.takeIf { it > 0f }?.let { smoothed ?: secondsLeft(it, perUnit) }
    val seconds = readAloud?.seconds ?: raw?.let { (it * calibration.correction).toLong() }
    // one number when sure enough, a range otherwise; hysteresis so it doesn't flicker around the line
    val latch = remember { booleanArrayOf(false) }
    val band = calibration.bandAt(progress)
    latch[0] = if (latch[0]) band <= ACCURATE_BAND * 1.2f else band <= ACCURATE_BAND * 0.9f
    val accurate = readAloud != null || latch[0]
    val showStreak = streak?.let { it.days > 0 } == true
    if (seconds == null && !showStreak) return

    val motion = MaterialTheme.motionScheme
    val joined = remember { mutableStateOf(true) }
    val leftShape = remember { PillShape(joined, left = true) }
    val rightShape = remember { PillShape(joined, left = false) }
    PillRow(joined, modifier) {
        if (seconds != null) {
            TimeLeftPill(
                seconds,
                calibration.range(seconds, progress),
                accurate,
                readAloud?.scope ?: scope,
                readAloud != null,
                if (showStreak) leftShape else CircleShape,
            )
        }
        AnimatedVisibility(
            visible = showStreak,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
        ) {
            streak?.let { StreakPill(it, viewModel.streakReached, false, if (seconds != null) rightShape else CircleShape) }
        }
    }
}

@Composable
private fun TimeLeftPill(
    seconds: Long,
    range: LongRange,
    accurate: Boolean,
    scope: LeftScope,
    aloud: Boolean,
    shape: Shape,
) {
    val duration = (if (accurate) null else rangeText(range)) ?: when (val t = leftText(seconds)) {
        LeftText.UnderAMinute -> stringResource(R.string.left_under_minute)
        is LeftText.Minutes -> stringResource(R.string.left_minutes, t.minutes)
        is LeftText.HoursMinutes -> stringResource(R.string.left_hours_minutes, t.hours, t.minutes)
    }
    val text = stringResource(
        when {
            aloud && scope == LeftScope.Book -> R.string.reader_aloud_left_book
            aloud -> R.string.reader_aloud_left_section
            scope == LeftScope.Chapter -> R.string.reader_left_chapter
            scope == LeftScope.Book -> R.string.reader_left_book
            else -> R.string.reader_left_section
        },
        duration,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = shape,
    ) {
        Row(
            Modifier.height(PillHeight).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (aloud) Icons.Filled.Headphones else Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            RollingText(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun minutesText(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.duration_minutes, minutes) else stringResource(
        R.string.duration_hours_minutes,
        minutes / 60,
        minutes % 60,
    )

@Composable
private fun rangeText(range: LongRange): String? {
    val low = ((range.first + 30) / 60).toInt().coerceAtLeast(1)
    val high = ((range.last + 30) / 60).toInt()
    if (high <= low) return null
    return if (high < 60) stringResource(R.string.left_range_minutes, low, high) else stringResource(
        R.string.left_range_join,
        minutesText(low),
        minutesText(high),
    )
}

@Composable
internal fun ReaderForecast(viewModel: ReaderViewModel, kind: PaceKind, unitsLeft: Float?, progress: Float) {
    val perUnit by viewModel.secondsPerUnit(kind).collectAsStateWithLifecycle()
    val latestPace = rememberUpdatedState(perUnit)
    LaunchedEffect(kind, unitsLeft, progress, perUnit) {
        if (unitsLeft != null) viewModel.noteForecast(
            kind,
            secondsLeft(unitsLeft, latestPace.value),
            unitsLeft,
            progress,
        )
    }
}

private class Celebration {
    val pulse = Animatable(1f)
    val flame = Animatable(1f)
    val burst = Animatable(0f)
    val flash = Animatable(0f)

    val burn = Animatable(0f)
    val phase = mutableFloatStateOf(0f)

    var sparks = false

    suspend fun play(style: MotionStyle, burnMillis: Long) = coroutineScope {
        if (style == MotionStyle.Reduced) {
            launch { flash.snapTo(1f); flash.animateTo(0f, tween(350)) }
            return@coroutineScope
        }
        launch {
            burn.animateTo(1f, tween(250))
            val start = withFrameNanos { it }
            var now = start
            while ((now - start) / 1_000_000 < burnMillis) {
                now = withFrameNanos { it }
                phase.floatValue = (((now - start) / 1_000_000f) % FLICKER_MS) / FLICKER_MS
            }
            burn.animateTo(0f, tween(700))
        }
        val bouncy = style == MotionStyle.Bouncy
        sparks = bouncy
        launch { flame.snapTo(0.4f); flame.animateTo(1f, spring(if (bouncy) 0.3f else 1f, 400f)) }
        launch { pulse.snapTo(if (bouncy) 1.14f else 1.06f); pulse.animateTo(1f, spring(if (bouncy) 0.4f else 1f, 320f)) }
        launch { burst.snapTo(0f); burst.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { flash.snapTo(1f); flash.animateTo(0f, tween(1000)) }
    }
}

@Composable
private fun StreakPill(
    streak: StreakStatus,
    celebrations: Flow<Int>?,
    celebrateOnShow: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = LocalAppSettings.current.motionStyle
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val celebration = remember { Celebration() }

    LaunchedEffect(celebrations, style) {
        celebrations?.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            celebration.play(style, BURN_IN_PILL_MS)
        }
    }
    LaunchedEffect(celebrateOnShow, style) {
        if (celebrateOnShow) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            celebration.play(style, BURN_ALONE_MS)
        }
    }

    val baseContainer by animateColorAsState(
        if (streak.atRisk) colors.tertiaryContainer else colors.primaryContainer,
        motion.defaultEffectsSpec(),
        label = "streak-container",
    )
    val baseContent by animateColorAsState(
        if (streak.atRisk) colors.onTertiaryContainer else colors.onPrimaryContainer,
        motion.defaultEffectsSpec(),
        label = "streak-content",
    )
    val ringColor = colors.primary
    val sparkColors = listOf(colors.primary, colors.tertiary, colors.secondary)
    val description = if (streak.atRisk) {
        stringResource(R.string.reader_streak_keep_description, streak.days, streak.secondsToKeep.toInt())
    } else {
        stringResource(R.string.reader_streak, streak.days)
    }

    Box(
        modifier
            .semantics { contentDescription = description }
            .graphicsLayer {
                scaleX = celebration.pulse.value
                scaleY = celebration.pulse.value
            }
            .drawBehind {
                val burn = celebration.burn.value
                if (burn > 0f) {
                    val phase = celebration.phase.floatValue
                    for (k in 0 until 7) {
                        val t = (phase * (1f + k % 2 * 0.35f) + k / 7f) % 1f
                        val x = 21.dp.toPx() + sin(k * 2.1f + t * 3.4f) * 6.dp.toPx()
                        val y = size.height / 2 - 6.dp.toPx() - t * 26.dp.toPx()
                        drawCircle(
                            color = (if (k % 2 == 0) FlameOrange else FlameAmber).copy(alpha = (1f - t) * burn),
                            radius = ((1f - t) * 2.2f + 0.6f).dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }
                val b = celebration.burst.value
                if (b <= 0f || b >= 1f) return@drawBehind
                for ((start, strength) in listOf(0f to 0.7f, 0.2f to 0.45f)) {
                    val t = ((b - start) / (1f - start)).coerceIn(0f, 1f)
                    if (t <= 0f || t >= 1f) continue
                    val grow = t * 12.dp.toPx()
                    drawRoundRect(
                        color = ringColor.copy(alpha = (1f - t) * strength),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(size.height / 2 + grow),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                if (celebration.sparks) {
                    val origin = Offset(21.dp.toPx(), size.height / 2)
                    val travel = 1f - (1f - b) * (1f - b)
                    for (i in 0 until 12) {
                        val angle = i * (2 * PI / 12).toFloat() + if (i % 2 == 0) 0f else 0.12f
                        val distance = 10.dp.toPx() + travel * (if (i % 2 == 0) 40.dp else 28.dp).toPx()
                        drawCircle(
                            color = sparkColors[i % sparkColors.size].copy(alpha = (1f - b).coerceIn(0f, 1f)),
                            radius = (1f - b) * (if (i % 2 == 0) 3.5.dp else 2.5.dp).toPx(),
                            center = origin + Offset(cos(angle) * distance, sin(angle) * distance),
                        )
                    }
                }
            },
    ) {
        Surface(
            color = lerp(baseContainer, colors.primary, celebration.flash.value),
            contentColor = lerp(baseContent, colors.onPrimary, celebration.flash.value),
            shape = shape,
        ) {
            Row(
                Modifier.height(PillHeight).padding(start = 5.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StreakGlyph(streak, celebration)
                Spacer(Modifier.width(6.dp))
                RollingText(
                    stringResource(R.string.reader_streak, streak.days),
                    style = MaterialTheme.typography.labelLarge,
                )
                AnimatedVisibility(
                    visible = streak.atRisk,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("  ·  ", style = MaterialTheme.typography.labelLarge)
                        RollingText(
                            stringResource(R.string.reader_streak_left, streak.secondsToKeep.toInt()),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakGlyph(streak: StreakStatus, celebration: Celebration) {
    val motion = MaterialTheme.motionScheme
    val content = LocalContentColor.current
    // fed once a second, fills evenly towards the target
    val progress by animateFloatAsState(
        if (streak.atRisk) 1f - streak.secondsToKeep / ACTIVE_DAY_SECONDS.toFloat() else 1f,
        tween(1000, easing = LinearEasing),
        label = "streak-ring",
    )
    val flameSize by animateDpAsState(
        if (streak.atRisk) 14.dp else 20.dp,
        motion.defaultSpatialSpec(),
        label = "streak-flame-size",
    )
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = streak.atRisk,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.5f),
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(26.dp),
                color = content,
                trackColor = content.copy(alpha = 0.18f),
                strokeWidth = 2.5.dp,
                gapSize = 0.dp,
            )
        }
        val burn = celebration.burn.value
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = lerp(content, if (celebration.phase.floatValue < 0.5f) FlameOrange else FlameAmber, burn),
            modifier = Modifier
                .size(flameSize)
                .drawBehind {
                    if (burn > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(FlameOrange.copy(alpha = 0.55f * burn), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 1.1f,
                            ),
                            radius = size.minDimension * 1.1f,
                        )
                    }
                }
                .graphicsLayer {
                    val b = celebration.burn.value
                    val wave = sin(celebration.phase.floatValue * 2f * PI.toFloat())
                    val flutter = sin(celebration.phase.floatValue * 2f * PI.toFloat() * 2.3f + 1f)
                    scaleX = celebration.flame.value * (1f - 0.08f * b * flutter)
                    scaleY = celebration.flame.value * (1f + 0.18f * b * (0.5f + 0.5f * wave))
                    rotationZ = 6f * b * flutter
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                },
        )
    }
}

@Composable
internal fun StreakCelebrationHost(viewModel: ReaderViewModel, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.streak.collect { } }
    }
    var visible by remember { mutableStateOf(false) }
    var days by remember { mutableIntStateOf(1) }
    // shown one lower, counts up once landed
    var shownDays by remember { mutableIntStateOf(1) }
    LaunchedEffect(viewModel) {
        viewModel.streakReached.collect { reached ->
            if (!viewModel.controlsShown) {
                days = reached.coerceAtLeast(1)
                shownDays = (days - 1).coerceAtLeast(1)
                visible = true
                delay(550)
                shownDays = days
                delay(3_400)
                visible = false
            }
        }
    }
    val motion = MaterialTheme.motionScheme
    val bouncy = LocalAppSettings.current.motionStyle == MotionStyle.Bouncy
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.navigationBarsPadding().padding(bottom = 72.dp),
        enter = fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(
                if (bouncy) spring(dampingRatio = 0.5f, stiffness = 380f) else motion.defaultSpatialSpec(),
                initialScale = 0.5f,
            ) +
            slideInVertically(if (bouncy) spring(dampingRatio = 0.55f, stiffness = 330f) else motion.defaultSpatialSpec()) { it * 2 },
        exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.defaultSpatialSpec()) { it },
    ) {
        StreakPill(StreakStatus(shownDays, true, 0), null, celebrateOnShow = true, shape = CircleShape)
    }
}
