package com.romanianguy.klover.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.prefs.MotionStyle
import kotlinx.coroutines.delay

// cards rise, grow and fade in one after another (index), once; Calm has no bounce, Reduced is a short fade
fun Modifier.reveal(index: Int): Modifier = composed {
    val motion = LocalAppSettings.current.motionStyle
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 55L))
        when (motion) {
            MotionStyle.Reduced -> progress.animateTo(1f, tween(160))
            MotionStyle.Calm -> progress.animateTo(1f, spring(Spring.DampingRatioNoBouncy, 380f))
            MotionStyle.Bouncy -> progress.animateTo(1f, spring(Spring.DampingRatioLowBouncy, 320f))
        }
    }
    val reduced = motion == MotionStyle.Reduced
    Modifier.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        if (!reduced) {
            translationY = (1f - p) * 36.dp.toPx()
            val s = 0.94f + 0.06f * p
            scaleX = s
            scaleY = s
        }
    }
}
