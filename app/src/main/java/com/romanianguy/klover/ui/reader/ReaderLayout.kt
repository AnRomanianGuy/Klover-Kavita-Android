package com.romanianguy.klover.ui.reader

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass
import com.romanianguy.klover.data.prefs.PageMode
import com.romanianguy.klover.ui.common.rememberBookHinge

@Immutable
data class ReaderLayout(
    val dual: Boolean,
    val hinge: Rect?,
    val tabletop: Rect? = null,
)

// two pages when wide enough and roughly as wide as tall, or when a vertical hinge splits the window; a portrait
// tablet stays on one page
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberReaderLayout(mode: PageMode, tabletopEnabled: Boolean = true): ReaderLayout {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSize = LocalWindowInfo.current.containerSize
    // A fold half open, or just bent to under 170 degrees: the pages go on either side of it like a book's.
    val hinge = rememberBookHinge()
    // fold lying across the screen (laptop style); stays null for a book-style fold
    val tabletop = if (tabletopEnabled) adaptiveInfo.windowPosture.separatingHorizontalHingeBounds.firstOrNull() else null
    val wideEnough = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val aspect = if (windowSize.height > 0) windowSize.width.toFloat() / windowSize.height else 0f

    return remember(mode, hinge, tabletop, wideEnough, aspect) {
        if (tabletop != null) return@remember ReaderLayout(dual = false, hinge = null, tabletop = tabletop)
        val dual = when (mode) {
            PageMode.Single -> false
            PageMode.Double -> true
            PageMode.Auto -> hinge != null || (wideEnough && aspect >= AUTO_DUAL_MIN_ASPECT)
        }
        ReaderLayout(dual = dual, hinge = if (dual) hinge else null)
    }
}

private const val AUTO_DUAL_MIN_ASPECT = 0.85f
