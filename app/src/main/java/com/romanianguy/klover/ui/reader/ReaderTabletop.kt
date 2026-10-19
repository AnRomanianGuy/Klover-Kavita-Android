package com.romanianguy.klover.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import kotlin.math.roundToInt

data class TabletopMetrics(val pageHeight: Dp, val fold: Dp, val panelHeight: Dp)

@Composable
fun rememberTabletopMetrics(hinge: Rect?): TabletopMetrics? {
    if (hinge == null) return null
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    return with(density) {
        TabletopMetrics(
            pageHeight = hinge.top.toDp(),
            fold = hinge.height.toDp(),
            panelHeight = (windowHeight - hinge.bottom).coerceAtLeast(0f).toDp(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TabletopPanel(
    title: @Composable () -> Unit,
    label: String,
    index: Int,
    lastIndex: Int,
    rtl: Boolean,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    previousChapterId: Int?,
    nextChapterId: Int?,
    onOpenChapter: (Int) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable () -> Unit = {},
    insights: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Column(Modifier.weight(1f)) { title() }
                actions()
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.reader_settings))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            insights()
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previous = if (rtl) nextChapterId else previousChapterId
                val next = if (rtl) previousChapterId else nextChapterId
                ChapterButton(previous, forward = rtl, onOpenChapter)
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Slider(
                        value = index.toFloat(),
                        onValueChange = { onSeek(it.roundToInt()) },
                        valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                }
                ChapterButton(next, forward = !rtl, onOpenChapter)
            }
            extra()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                // Left button goes left on screen: backwards, or forwards for right-to-left reading.
                val leftIsNext = rtl
                FilledTonalButton(
                    onClick = if (leftIsNext) onNext else onPrevious,
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                    Text(stringResource(if (leftIsNext) R.string.tabletop_next else R.string.tabletop_previous))
                }
                FilledTonalButton(
                    onClick = if (leftIsNext) onPrevious else onNext,
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Text(stringResource(if (leftIsNext) R.string.tabletop_previous else R.string.tabletop_next))
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                }
            }
        }
    }
}
