package com.romanianguy.klover.ui.downloads

import android.text.format.Formatter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.downloads.DownloadEntry
import com.romanianguy.klover.data.downloads.DownloadError
import com.romanianguy.klover.data.downloads.DownloadState
import com.romanianguy.klover.ui.common.BadgeLayer
import com.romanianguy.klover.ui.common.ChoiceButtonRow
import com.romanianguy.klover.ui.common.CoverImage
import com.romanianguy.klover.ui.common.PageSheet
import com.romanianguy.klover.ui.common.PeekAction
import com.romanianguy.klover.ui.common.PeekRequest
import com.romanianguy.klover.ui.common.bouncyClickable
import com.romanianguy.klover.ui.common.rememberQuickView
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.profiles.ProfileButton
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.max

private enum class DownloadFilter { All, Active, Done }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onRead: (chapterId: Int) -> Unit,
    onOpenSeries: (seriesId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.All) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var seriesToDelete by remember { mutableStateOf<SeriesGroup?>(null) }
    var openSeriesId by rememberSaveable { mutableStateOf<Int?>(null) }
    val palette = seriesPalette()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                actions = {
                    ProfileButton()
                    if (!ui.isEmpty) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (ui.canPause) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.downloads_pause_all)) },
                                        leadingIcon = { Icon(Icons.Outlined.Pause, contentDescription = null) },
                                        onClick = { menu = false; viewModel.pauseAll() },
                                    )
                                }
                                if (ui.canResume) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.downloads_resume_all)) },
                                        leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                                        onClick = { menu = false; viewModel.resumeAll() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.downloads_delete_all)) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = { menu = false; confirmDeleteAll = true },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ui.isEmpty) {
                EmptyDownloads(offline = ui.offline)
            } else {
                val showActive = filter != DownloadFilter.Done && ui.active.isNotEmpty()
                val showDone = filter != DownloadFilter.Active && ui.groups.isNotEmpty()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp + LocalFloatingBarInset.current,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (ui.offline) {
                        fullSpan("offline") { OfflineNote() }
                    }
                    fullSpan("hero") { HeroCard(ui, palette) }
                    if (ui.active.isNotEmpty() && ui.groups.isNotEmpty()) {
                        fullSpan("filters") {
                            ChoiceButtonRow(
                                options = DownloadFilter.entries,
                                selected = filter,
                                onSelect = { filter = it },
                                key = { it },
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) { option ->
                                Text(
                                    when (option) {
                                        DownloadFilter.All -> stringResource(R.string.downloads_filter_all)
                                        DownloadFilter.Active -> stringResource(
                                            R.string.downloads_filter_active,
                                            ui.active.size,
                                        )
                                        DownloadFilter.Done -> stringResource(R.string.downloads_filter_done)
                                    },
                                )
                            }
                        }
                    }
                    if (showActive) {
                        fullSpan("active-title") { SectionTitle(R.string.downloads_section_active) }
                        fullSpan("active-row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(ui.active, key = { it.chapterId }) { entry ->
                                    ActiveCard(
                                        entry = entry,
                                        waitingForWifi = ui.waitingForWifi,
                                        offline = ui.offline,
                                        cover = viewModel.cover(entry.chapterId),
                                        onPause = { viewModel.pause(entry.chapterId) },
                                        onResume = { viewModel.resume(entry.chapterId) },
                                        onCancel = { viewModel.remove(entry.chapterId) },
                                        onOpenSeries = { onOpenSeries(entry.seriesId) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    } else if (filter == DownloadFilter.Active) {
                        fullSpan("none-active") {
                            Text(
                                stringResource(R.string.downloads_nothing_active),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    if (showDone) {
                        fullSpan("done-title") { SectionTitle(R.string.downloads_section_done) }
                        items(count = ui.groups.size, key = { ui.groups[it].seriesId }) { index ->
                            val group = ui.groups[index]
                            SeriesTile(
                                group = group,
                                color = palette[index % palette.size],
                                cover = group.cover,
                                index = index,
                                onClick = { openSeriesId = group.seriesId },
                                onContinue = { onRead(continueChapter(group)) },
                                onOpenSeries = { onOpenSeries(group.seriesId) },
                                onDelete = { seriesToDelete = group },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    // The chapters of one series, in a sheet that rises from the bottom.
    val sheetGroup = ui.groups.firstOrNull { it.seriesId == openSeriesId }
    LaunchedEffect(sheetGroup, openSeriesId) { if (openSeriesId != null && sheetGroup == null && !ui.isEmpty) openSeriesId = null }
    if (sheetGroup != null) {
        PageSheet(onDismissRequest = { openSeriesId = null }) {
            SeriesSheet(
                group = sheetGroup,
                color = palette[max(ui.groups.indexOf(sheetGroup), 0) % palette.size],
                onRead = { openSeriesId = null; onRead(it) },
                onOpenSeries = { openSeriesId = null; onOpenSeries(sheetGroup.seriesId) },
                onDeleteChapter = viewModel::remove,
                onDeleteSeries = { seriesToDelete = sheetGroup },
            )
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.downloads_delete_all_title)) },
            text = { Text(stringResource(R.string.downloads_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; viewModel.removeAll() }) {
                    Text(stringResource(R.string.downloads_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    seriesToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { seriesToDelete = null },
            title = { Text(stringResource(R.string.downloads_delete_series_title, group.name)) },
            text = { Text(stringResource(R.string.downloads_delete_series_body)) },
            confirmButton = {
                TextButton(onClick = { seriesToDelete = null; openSeriesId = null; viewModel.removeSeries(group.seriesId) }) {
                    Text(stringResource(R.string.downloads_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { seriesToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun seriesPalette(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(primary, tertiary, secondary, inversePrimary, tertiaryContainer, secondaryContainer, primaryContainer, error)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(ui: DownloadsUi, palette: List<Color>) {
    val context = LocalContext.current
    // The number glides to its new value when something is added or deleted.
    val shownBytes by animateFloatAsState(
        ui.totalBytes.toFloat(),
        tween(600, easing = FastOutSlowInEasing),
        label = "hero-bytes",
    )
    val chapters = ui.groups.sumOf { it.items.size }
    val activeProgress = if (ui.active.isEmpty()) 0f else ui.active.map { it.progress }.average().toFloat()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.downloads_hero_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Text(
                Formatter.formatShortFileSize(context, shownBytes.toLong()),
                style = MaterialTheme.typography.displaySmallEmphasized,
            )
            Text(
                pluralStringResource(R.plurals.downloads_series_count, ui.groups.size, ui.groups.size) +
                    " · " + pluralStringResource(R.plurals.downloads_chapters, chapters, chapters),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ui.groups.isNotEmpty()) StorageBar(ui.groups, palette)
            if (ui.active.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pluralStringResource(R.plurals.downloads_hero_active, ui.active.size, ui.active.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${(activeProgress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    }
                    val animated by animateFloatAsState(activeProgress, tween(450), label = "hero-progress")
                    LinearWavyProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageBar(groups: List<SeriesGroup>, palette: List<Color>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groups.forEachIndexed { index, group ->
            val weight by animateFloatAsState(max(group.bytes, 1L).toFloat(), tween(600), label = "segment")
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(palette[index % palette.size]),
            )
        }
    }
}

@Composable
private fun OfflineNote() {
    Row(
        Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.downloads_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveCard(
    entry: DownloadEntry,
    waitingForWifi: Boolean,
    offline: Boolean,
    cover: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = entry.state == DownloadState.Failed
    val running = entry.state == DownloadState.Running
    val toggle = if (entry.isActive) onPause else onResume
    val progress by animateFloatAsState(entry.progress.coerceIn(0.02f, 1f), tween(450), label = "card-progress")
    val quickView = rememberQuickView()
    val toggleIcon = when {
        entry.isActive -> Icons.Outlined.Pause
        failed -> Icons.Outlined.Refresh
        else -> Icons.Outlined.PlayArrow
    }
    val toggleLabel = when {
        entry.isActive -> R.string.downloads_pause
        failed -> R.string.downloads_retry
        else -> R.string.downloads_resume
    }

    Column(modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .bouncyClickable(
                    holdToExpand = quickView.available,
                    interactionSource = quickView.interaction,
                    onLongClick = if (quickView.available) {
                        {
                            quickView.show(
                                PeekRequest(
                                    key = quickView.key,
                                    origin = quickView.bounds,
                                    originNow = { quickView.bounds },
                                    cornerRadius = 28.dp,
                                    coverUrl = cover,
                                    title = entry.seriesName,
                                    subtitle = entry.displayTitle,
                                    onOpen = {},
                                    showOpen = false,
                                    options = listOf(
                                        PeekAction(toggleIcon, toggleLabel, primary = true, onClick = toggle),
                                        PeekAction(
                                            Icons.AutoMirrored.Outlined.OpenInNew,
                                            R.string.downloads_open_series,
                                            onClick = onOpenSeries,
                                        ),
                                        PeekAction(Icons.Outlined.Close, R.string.downloads_cancel, onClick = onCancel),
                                    ),
                                ),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = toggle,
                )
                .then(quickView.boundsModifier())
                .clip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().then(quickView.hideModifier())) {
                CoverImage(
                    url = cover,
                    contentDescription = null,
                    placeholderTitle = entry.seriesName,
                    shape = RoundedCornerShape(0.dp),
                    aspectRatio = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Scrim, ring and percentage fade on their own around the lift (see BadgeLayer).
            BadgeLayer(quickView.lifted) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
                Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                    if (running) {
                        CircularWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(76.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.28f),
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(76.dp),
                            color = if (failed) MaterialTheme.colorScheme.error else Color.White,
                            trackColor = Color.White.copy(alpha = 0.28f),
                            strokeWidth = 6.dp,
                        )
                    }
                    Text(
                        "${(entry.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
        Column {
            Text(
                entry.seriesName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.auto) {
                    "${entry.displayTitle} · ${stringResource(R.string.downloads_smart_tag)}"
                } else {
                    entry.displayTitle
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusText(entry, waitingForWifi, offline),
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = toggle) {
                Icon(toggleIcon, contentDescription = stringResource(toggleLabel))
            }
            FilledTonalIconButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.downloads_cancel))
            }
        }
    }
}

@Composable
private fun SeriesTile(
    group: SeriesGroup,
    color: Color,
    cover: String?,
    index: Int,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onOpenSeries: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val quickView = rememberQuickView()
    val summary = pluralStringResource(R.plurals.downloads_chapters, group.items.size, group.items.size) +
        " · " + Formatter.formatShortFileSize(context, group.bytes)
    Column(
        modifier
            .appearGently(index)
            .bouncyClickable(
                holdToExpand = quickView.available,
                interactionSource = quickView.interaction,
                onLongClick = if (quickView.available) {
                    {
                        quickView.show(
                            PeekRequest(
                                key = quickView.key,
                                origin = quickView.bounds,
                                originNow = { quickView.bounds },
                                cornerRadius = 28.dp,
                                coverUrl = cover,
                                title = group.name,
                                subtitle = summary,
                                onOpen = onClick,
                                openLabel = R.string.downloads_show_chapters,
                                options = listOf(
                                    PeekAction(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        R.string.series_continue,
                                        onClick = onContinue,
                                    ),
                                    PeekAction(
                                        Icons.AutoMirrored.Outlined.OpenInNew,
                                        R.string.downloads_open_series,
                                        onClick = onOpenSeries,
                                    ),
                                    PeekAction(
                                        Icons.Outlined.DeleteOutline,
                                        R.string.series_download_remove,
                                        onClick = onDelete,
                                    ),
                                ),
                            ),
                        )
                    }
                } else {
                    null
                },
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).then(quickView.boundsModifier()).clip(MaterialTheme.shapes.extraLarge)) {
            Box(Modifier.fillMaxSize().then(quickView.hideModifier())) {
                CoverImage(
                    url = cover,
                    contentDescription = null,
                    placeholderTitle = group.name,
                    shape = RoundedCornerShape(0.dp),
                    aspectRatio = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The size and the chapter count are not part of the cover that is lifted: they fade away as it lifts and
            // ease back once it has landed, instead of blinking with the cover.
            BadgeLayer(quickView.lifted) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                        .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(
                            Formatter.formatShortFileSize(context, group.bytes),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    Text(
                        "${group.items.size}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Text(group.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// the chapter "continue reading" opens: the first one not read to the end yet, else the first
private fun continueChapter(group: SeriesGroup): Int =
    (group.items.firstOrNull { it.pages > 0 && it.localPagesRead < it.pages } ?: group.items.first()).chapterId

@Composable
private fun Modifier.appearGently(index: Int): Modifier {
    val animate = index < 12
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = index * 45, easing = FastOutSlowInEasing),
        label = "tile-appear",
    )
    return graphicsLayer {
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesSheet(
    group: SeriesGroup,
    color: Color,
    onRead: (Int) -> Unit,
    onOpenSeries: () -> Unit,
    onDeleteChapter: (Int) -> Unit,
    onDeleteSeries: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                url = group.cover,
                contentDescription = null,
                placeholderTitle = group.name,
                modifier = Modifier.width(72.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        pluralStringResource(R.plurals.downloads_chapters, group.items.size, group.items.size) +
                            " · " + Formatter.formatShortFileSize(context, group.bytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenSeries) { Text(stringResource(R.string.downloads_open_series)) }
            OutlinedButton(onClick = onDeleteSeries) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.downloads_delete_confirm))
            }
        }
        group.items.forEach { entry ->
            Surface(
                onClick = { onRead(entry.chapterId) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (entry.auto) {
                                "${entry.displayTitle} · ${stringResource(R.string.downloads_smart_tag)}"
                            } else {
                                entry.displayTitle
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Read progress is the file's: it says nothing about the few chapters of it that were
                        // downloaded.
                        val read = if (entry.isWhole) entry.localPagesRead else -1
                        if (read >= 0 && entry.pages > 0) {
                            LinearProgressIndicator(
                                progress = { (read.toFloat() / entry.pages).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(0.6f),
                                drawStopIndicator = {},
                            )
                        }
                        Text(
                            text = if (read >= 0 && entry.pages > 0) {
                                pluralStringResource(
                                    R.plurals.downloads_read_progress,
                                    entry.pages,
                                    read.coerceAtMost(entry.pages),
                                    entry.pages,
                                ) +
                                    " · " + Formatter.formatShortFileSize(context, entry.bytes)
                            } else {
                                Formatter.formatShortFileSize(context, entry.bytes)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDeleteChapter(entry.chapterId) }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.downloads_remove),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyDownloads(offline: Boolean) {
    // A scalloped shape that turns very slowly - lively, but never in the way.
    val turn by rememberInfiniteTransition(label = "empty").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "empty-turn",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 24.dp)) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer { rotationZ = turn }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Icon(
                Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            stringResource(R.string.downloads_empty_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.downloads_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (offline) {
            Text(
                stringResource(R.string.downloads_offline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun statusText(entry: DownloadEntry, waitingForWifi: Boolean, offline: Boolean): String = when (entry.state) {
    DownloadState.Running -> stringResource(R.string.downloads_notification_progress, (entry.progress * 100).toInt())
    DownloadState.Queued -> stringResource(
        when {
            offline -> R.string.downloads_status_connection
            waitingForWifi -> R.string.downloads_status_wifi
            else -> R.string.downloads_status_queued
        },
    )

    DownloadState.Paused -> stringResource(R.string.downloads_status_paused)
    DownloadState.Failed -> stringResource(
        R.string.downloads_status_failed,
        stringResource(
            when (entry.error) {
                DownloadError.Network -> R.string.downloads_error_network
                DownloadError.Storage -> R.string.downloads_error_storage
                DownloadError.Auth -> R.string.downloads_error_auth
                DownloadError.NotFound -> R.string.downloads_error_not_found
                DownloadError.Server -> R.string.downloads_error_server
                else -> R.string.downloads_error_other
            },
        ),
    )

    DownloadState.Completed -> ""
}
