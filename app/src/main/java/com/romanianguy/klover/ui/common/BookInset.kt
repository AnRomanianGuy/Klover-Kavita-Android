package com.romanianguy.klover.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

// how far in from the left the right page starts (the fold's end in book posture, else 0); springs so things on that
// page slide instead of jumping
@Composable
fun rememberBookInset(hinge: Rect?): Dp {
    val density = LocalDensity.current
    val target = with(density) { (hinge?.right ?: 0f).toDp() }
    return animateDpAsState(
        target,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "book-inset",
    ).value
}
