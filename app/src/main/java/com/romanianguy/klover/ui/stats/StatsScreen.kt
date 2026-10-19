package com.romanianguy.klover.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.history.LastRead
import com.romanianguy.klover.data.stats.DayBar
import com.romanianguy.klover.data.stats.MIN_FOR_BAND
import com.romanianguy.klover.data.stats.PaceKind
import com.romanianguy.klover.data.stats.ReadingAccuracy
import com.romanianguy.klover.data.stats.ReadingStats
import com.romanianguy.klover.data.stats.StatsSummary
import com.romanianguy.klover.data.stats.calibrate
import com.romanianguy.klover.data.stats.durationParts
import com.romanianguy.klover.data.stats.summarize
import com.romanianguy.klover.ui.common.reveal
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Composable
fun rememberStatsSummary(): StatsSummary {
    val stats = koinInject<ReadingStats>()
    val days by stats.days.collectAsStateWithLifecycle()
    return remember(days) { summarize(days, LocalDate.now()) }
}

@Composable
fun formatDuration(seconds: Long): String {
    val (hours, minutes) = durationParts(seconds)
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_less)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccuracyCard(modifier: Modifier = Modifier) {
    val data by koinInject<ReadingAccuracy>().data.collectAsStateWithLifecycle()
    val kinds = PaceKind.entries.filter { (data.outcomes[it.key]?.n ?: 0) > 0 }
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.stats_accuracy_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (kinds.isEmpty()) {
                Text(
                    stringResource(R.string.stats_accuracy_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (kind in kinds) {
                val calibration = calibrate(data.outcomes[kind.key])
                Line(
                    when (kind) {
                        PaceKind.Manga -> R.string.stats_kind_manga
                        PaceKind.Pages -> R.string.stats_kind_comics
                        PaceKind.Strips -> R.string.stats_kind_strips
                        PaceKind.Pdf -> R.string.stats_kind_pdf
                        PaceKind.Text -> R.string.stats_kind_books
                        PaceKind.Novel -> R.string.stats_kind_novels
                    },
                    calibration.band?.let { stringResource(R.string.stats_accuracy_value, (it * 100).roundToInt(), calibration.samples) }
                        ?: stringResource(R.string.stats_accuracy_learning, calibration.samples, MIN_FOR_BAND),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, onOpenHistory: () -> Unit = {}) {
    val summary = rememberStatsSummary()
    val stats = koinInject<ReadingStats>()
    var confirmReset by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.history_title))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopStart) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp + LocalFloatingBarInset.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!summary.hasData) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.stats_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.stats_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    // This week
                    Surface(
                        Modifier.reveal(0),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.stats_this_week), style = MaterialTheme.typography.labelLarge)
                            Text(
                                formatDuration(summary.weekSeconds),
                                style = MaterialTheme.typography.displaySmallEmphasized,
                            )
                            Text(
                                pluralStringResource(R.plurals.stats_pages, summary.weekPages, summary.weekPages),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            WeekChart(
                                summary.last7,
                                barHeight = 120.dp,
                                showLabels = true,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(Modifier.reveal(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(R.string.stats_today, formatDuration(summary.todaySeconds), Modifier.weight(1f))
                        StatTile(R.string.stats_last_30, formatDuration(summary.monthSeconds), Modifier.weight(1f))
                    }
                    Row(Modifier.reveal(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StreakTile(
                            R.string.stats_current_streak,
                            summary.currentStreak,
                            Modifier.weight(1f),
                            highlight = summary.currentStreak > 0,
                        )
                        StreakTile(
                            R.string.stats_best_streak,
                            summary.bestStreak,
                            Modifier.weight(1f),
                            highlight = false,
                        )
                    }
                    Surface(
                        Modifier.reveal(3),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.stats_all_time),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Line(R.string.stats_time, formatDuration(summary.totalSeconds))
                            Line(
                                R.string.stats_pages_label,
                                pluralStringResource(R.plurals.stats_pages, summary.totalPages, summary.totalPages),
                            )
                            Line(R.string.stats_chapters_label, summary.totalChapters.toString())
                            Line(
                                R.string.stats_active_days,
                                pluralStringResource(R.plurals.stats_days, summary.activeDays, summary.activeDays),
                            )
                            Line(R.string.stats_average, formatDuration(summary.averageSecondsPerActiveDay))
                        }
                    }
                    AccuracyCard(Modifier.reveal(4))
                    OutlinedButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.stats_reset)) }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.stats_reset_title)) },
            text = { Text(stringResource(R.string.stats_reset_body)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; stats.reset() }) { Text(stringResource(R.string.stats_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun StatTile(label: Int, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StreakTile(label: Int, days: Int, modifier: Modifier = Modifier, highlight: Boolean) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(
                    pluralStringResource(R.plurals.stats_days, days, days),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun Line(label: Int, value: String) {
    Row {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun WeekChart(
    bars: List<DayBar>,
    barHeight: Dp,
    showLabels: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
) {
    val max = (bars.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            val target = if (shown) (bar.seconds.toFloat() / max).coerceIn(0f, 1f) else 0f
            val fraction by animateFloatAsState(
                target,
                tween(durationMillis = 600, delayMillis = index * 50, easing = FastOutSlowInEasing),
                label = "bar",
            )
            val isToday = index == bars.lastIndex
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.height(barHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    // A day without reading still shows a small stub, so the row reads as a row.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((barHeight * fraction).coerceAtLeast(6.dp))
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = if (bar.seconds == 0L) 0.2f else if (isToday) 1f else 0.6f)),
                    )
                }
                if (showLabels) {
                    Text(
                        bar.date.dayOfWeek.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    summary: StatsSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    continueWith: LastRead? = null,
    onContinue: (chapterId: Int) -> Unit = {},
) {
    // On a narrow screen (the cover display of a fold) the card is compact: the week's bars are smaller, so the words
    // fit on
    // one line each, and it is padded less.
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val narrow = maxWidth < 420.dp
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column {
        Row(
            Modifier.padding(horizontal = if (narrow) 16.dp else 20.dp, vertical = if (narrow) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        if (summary.currentStreak > 0) pluralStringResource(
                            R.plurals.stats_streak,
                            summary.currentStreak,
                            summary.currentStreak,
                        )
                        else stringResource(R.string.stats_no_streak),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    stringResource(R.string.stats_this_week) + " · " + formatDuration(summary.weekSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.size(if (narrow) 10.dp else 12.dp))
            if (narrow) {
                // Small: a little row of bars, so the activity is still there and the words keep their line.
                WeekChart(
                    summary.last7,
                    barHeight = 26.dp,
                    showLabels = false,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(72.dp),
                    gap = 3.dp,
                )
            } else {
                WeekChart(
                    summary.last7,
                    barHeight = 36.dp,
                    showLabels = false,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        if (continueWith != null) {
            ContinueRow(
                continueWith,
                onContinue,
                narrow,
                Modifier.padding(
                    start = if (narrow) 16.dp else 20.dp,
                    end = if (narrow) 16.dp else 20.dp,
                    bottom = if (narrow) 12.dp else 14.dp,
                ),
            )
        }
        }
    }
    }
}

@Composable
private fun ContinueRow(
    last: LastRead,
    onContinue: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = { onContinue(last.chapterId) },
        modifier = modifier.height(if (compact) 36.dp else 40.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            // Small screen: just what to carry on with, not the chapter after it.
            stringResource(
                R.string.home_continue_last,
                (if (compact) last.shortLabel else last.label).ifBlank { stringResource(R.string.app_name) },
            ),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).widthIn(max = 260.dp),
        )
    }
}

@Composable
fun ContinueCard(last: LastRead, onContinue: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) { ContinueRow(last, onContinue, compact = false, modifier = Modifier.padding(14.dp)) }
}
