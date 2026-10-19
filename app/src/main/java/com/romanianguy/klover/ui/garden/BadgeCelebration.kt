package com.romanianguy.klover.ui.garden

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.garden.Badge
import com.romanianguy.klover.data.garden.BadgeWatcher
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.ui.common.LocalAppSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun badgeIcon(badge: Badge): ImageVector = when (badge) {
    Badge.FirstChapter -> Icons.Filled.Eco
    Badge.TenChapters -> Icons.AutoMirrored.Filled.MenuBook
    Badge.FiftyChapters -> Icons.Filled.Stars
    Badge.HundredChapters -> Icons.Filled.EmojiEvents
    Badge.Streak3, Badge.Streak7, Badge.Streak30 -> Icons.Filled.LocalFireDepartment
    Badge.LongDay -> Icons.Filled.Schedule
    Badge.TenHours -> Icons.Filled.HourglassBottom
    Badge.NightOwl -> Icons.Filled.Bedtime
    Badge.EarlyBird -> Icons.Filled.WbSunny
    Badge.GoalMet -> Icons.Filled.Flag
}

private const val STAY_MS = 4_200L
private const val GAP_MS = 450L

// a pill drops in from the top wherever the reader is; several badges come one after the other; not shown when the
// garden is off
@Composable
fun BadgeCelebrationHost(modifier: Modifier = Modifier) {
    val watcher = koinInject<BadgeWatcher>()
    var shown by remember { mutableStateOf<Badge?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(watcher) {
        watcher.justEarned.collect { badge ->
            shown = badge
            visible = true
            delay(STAY_MS)
            visible = false
            delay(GAP_MS)
        }
    }
    val motion = MaterialTheme.motionScheme
    val bouncy = LocalAppSettings.current.motionStyle == MotionStyle.Bouncy
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 12.dp),
        // It drops from above the screen and lands with a bounce (a plain glide when bounce is off).
        enter = fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(
                if (bouncy) spring(dampingRatio = 0.5f, stiffness = 380f) else motion.defaultSpatialSpec(),
                initialScale = 0.5f,
            ) +
            slideInVertically(if (bouncy) spring(dampingRatio = 0.55f, stiffness = 330f) else motion.defaultSpatialSpec()) { -it * 2 },
        exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.defaultSpatialSpec()) { -it },
    ) {
        shown?.let { badge ->
            BadgePill(
                badge,
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { visible = false },
            )
        }
    }
}

private class BadgePop {
    val pulse = Animatable(1f)
    val burst = Animatable(0f)
    val flash = Animatable(0f)
    val turn = Animatable(0f)
    val grow = Animatable(1f)
    var sparks = false

    suspend fun play(style: MotionStyle) = coroutineScope {
        if (style == MotionStyle.Reduced) {
            launch { flash.snapTo(1f); flash.animateTo(0f, tween(350)) }
            return@coroutineScope
        }
        val bouncy = style == MotionStyle.Bouncy
        sparks = bouncy
        launch { pulse.snapTo(if (bouncy) 1.14f else 1.06f); pulse.animateTo(1f, spring(if (bouncy) 0.4f else 1f, 320f)) }
        launch { burst.snapTo(0f); burst.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { flash.snapTo(1f); flash.animateTo(0f, tween(1000)) }
        // The badge itself spins a full turn and swells as it settles.
        launch { turn.snapTo(-180f); turn.animateTo(0f, spring(if (bouncy) 0.45f else 1f, 120f)) }
        launch { grow.snapTo(0.3f); grow.animateTo(1f, spring(if (bouncy) 0.35f else 1f, 260f)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BadgePill(badge: Badge, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = LocalAppSettings.current.motionStyle
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalAppSettings.current.haptics
    val pop = remember { BadgePop() }
    LaunchedEffect(badge, style) {
        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        pop.play(style)
    }
    val name = stringResource(badgeName(badge))
    val title = stringResource(R.string.badge_earned)
    val sparkColors = listOf(scheme.tertiary, scheme.primary, scheme.secondary)

    Box(
        modifier
            .semantics { contentDescription = "$title: $name" }
            .graphicsLayer { scaleX = pop.pulse.value; scaleY = pop.pulse.value }
            // Rings and sparks spread out of the pill and are drawn behind it, so they show around its edge.
            .drawBehind {
                val b = pop.burst.value
                if (b <= 0f || b >= 1f) return@drawBehind
                for ((start, strength) in listOf(0f to 0.7f, 0.2f to 0.45f)) {
                    val t = ((b - start) / (1f - start)).coerceIn(0f, 1f)
                    if (t <= 0f || t >= 1f) continue
                    val grow = t * 14.dp.toPx()
                    drawRoundRect(
                        color = scheme.tertiary.copy(alpha = (1f - t) * strength),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(size.height / 2 + grow),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                if (pop.sparks) {
                    // Sparks leave the badge in every direction, slowing as they go, and shrink away.
                    val origin = Offset(34.dp.toPx(), size.height / 2)
                    val travel = 1f - (1f - b) * (1f - b)
                    for (i in 0 until 14) {
                        val angle = i * (2 * PI / 14).toFloat() + if (i % 2 == 0) 0f else 0.1f
                        val distance = 14.dp.toPx() + travel * (if (i % 2 == 0) 56.dp else 36.dp).toPx()
                        drawCircle(
                            color = sparkColors[i % sparkColors.size].copy(alpha = (1f - b).coerceIn(0f, 1f)),
                            radius = (1f - b) * (if (i % 2 == 0) 4.dp else 3.dp).toPx(),
                            center = origin + Offset(cos(angle) * distance, sin(angle) * distance),
                        )
                    }
                }
            },
    ) {
        Surface(
            color = lerp(scheme.tertiaryContainer, scheme.tertiary, pop.flash.value),
            contentColor = lerp(scheme.onTertiaryContainer, scheme.onTertiary, pop.flash.value),
            shape = CircleShape,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .graphicsLayer { rotationZ = pop.turn.value; scaleX = pop.grow.value; scaleY = pop.grow.value }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(scheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        badgeIcon(badge),
                        contentDescription = null,
                        tint = scheme.onTertiary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = LocalContentColorFaded())
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LocalContentColorFaded(): Color = LocalContentColor.current.copy(alpha = 0.75f)
