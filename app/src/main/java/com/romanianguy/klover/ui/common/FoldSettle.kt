package com.romanianguy.klover.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.romanianguy.klover.data.prefs.MotionStyle

// dips and springs back when the posture changes, so the layout change reads as adapting and not a jump.
// nothing on first appearance; Reduced is only a short fade
fun Modifier.foldSettle(posture: Boolean): Modifier = composed {
    val motion = LocalAppSettings.current.motionStyle
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(posture) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        if (motion == MotionStyle.Reduced) progress.animateTo(1f, tween(180)) else progress.animateTo(
            1f,
            spring(0.72f, Spring.StiffnessLow),
        )
    }
    val reduced = motion == MotionStyle.Reduced
    Modifier.graphicsLayer {
        val p = progress.value
        alpha = (0.35f + 0.65f * p).coerceIn(0f, 1f)
        if (!reduced) {
            val s = 0.94f + 0.06f * p
            scaleX = s
            scaleY = s
        }
    }
}
