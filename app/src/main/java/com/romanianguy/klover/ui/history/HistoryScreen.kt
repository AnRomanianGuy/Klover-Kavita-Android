package com.romanianguy.klover.ui.history

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.history.Bookmark
import com.romanianguy.klover.data.history.Bookmarks
import com.romanianguy.klover.data.history.HistoryEntry
import com.romanianguy.klover.data.history.ReadingHistory
import com.romanianguy.klover.data.history.groupByDay
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.ui.common.CoverImage
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.bouncyClickable
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.reader.NoteDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpen: (chapterId: Int, page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = koinInject<ReadingHistory>()
    val bookmarks = koinInject<Bookmarks>()
    val entries by history.entries.collectAsStateWithLifecycle()
    val flags by bookmarks.items.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                subtitle = {
                    Text(
                        pluralStringResource(
                            R.plurals.history_subtitle,
                            entries.count { it.read },
                            entries.count { it.read },
                        ) +
                            " · " + pluralStringResource(R.plurals.history_flags_count, flags.size, flags.size),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (pager.currentPage == 0 && entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.history_clear),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = pager.currentPage == 0,
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.history_tab_history), style = MaterialTheme.typography.labelLarge) },
                )
                Tab(
                    selected = pager.currentPage == 1,
                    onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.history_tab_flags), style = MaterialTheme.typography.labelLarge) },
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    if (page == 0) {
                        HistoryTab(entries, onOpen = { onOpen(it.chapterId, -1) }, onRemove = history::remove)
                    } else {
                        FlagsTab(
                            flags,
                            onOpen = { onOpen(it.chapterId, it.page) },
                            onRemove = bookmarks::remove,
                            onNote = { flag, note -> bookmarks.setNote(flag.chapterId, flag.page, note) },
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; history.clear() }) {
                    Text(stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private sealed interface Row {
    val key: String

    class Day(val day: LocalDate, val count: Int) : Row {
        override val key get() = "day-$day"
    }

    class Chapter(val entry: HistoryEntry, val index: Int, val of: Int) : Row {
        override val key get() = "c-${entry.chapterId}-${entry.at}"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryTab(entries: List<HistoryEntry>, onOpen: (HistoryEntry) -> Unit, onRemove: (HistoryEntry) -> Unit) {
    if (entries.isEmpty()) {
        EmptyPage(Icons.Outlined.History, R.string.history_empty_title, R.string.history_empty_body)
        return
    }
    val rows = remember(entries) {
        buildList {
            for ((day, list) in groupByDay(entries)) {
                add(Row.Day(day, list.size))
                list.forEachIndexed { i, entry -> add(Row.Chapter(entry, i, list.size)) }
            }
        }
    }
    val urls = koinInject<ServerUrls>()
    val time = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val thisWeek = remember(entries) {
        val weekAgo = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        entries.count { it.read && it.at >= weekAgo }
    }
    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 24.dp + LocalFloatingBarInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item(key = "summary") { SummaryCard(total = entries.count { it.read }, thisWeek = thisWeek) }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is Row.Day -> DayHeader(row)
                is Row.Chapter -> ChapterCard(
                    entry = row.entry,
                    cover = urls.seriesCover(row.entry.seriesId),
                    time = Instant.ofEpochMilli(row.entry.at).atZone(ZoneId.systemDefault()).format(time),
                    shape = blockShape(row.index, row.of),
                    index = row.index,
                    onOpen = { onOpen(row.entry) },
                    onRemove = { onRemove(row.entry) },
                )
            }
        }
    }
}

private fun blockShape(index: Int, count: Int): Shape {
    val big = 26.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryCard(total: Int, thisWeek: Int) {
    val counted by animateIntAsState(total, tween(700), label = "history-total")
    val style = LocalAppSettings.current.motionStyle
    val turn by rememberInfiniteTransition(label = "history-turn").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "history-turn-angle",
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = if (style == MotionStyle.Reduced) 0f else turn }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                )
                Icon(
                    Icons.Filled.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(Modifier.padding(start = 18.dp)) {
                Text(counted.toString(), style = MaterialTheme.typography.displaySmallEmphasized)
                Text(stringResource(R.string.history_finished), style = MaterialTheme.typography.bodyMedium)
                if (thisWeek > 0) {
                    Text(
                        pluralStringResource(R.plurals.history_this_week, thisWeek, thisWeek),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(row: Row.Day) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                dayLabel(row.day),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Text(
            pluralStringResource(R.plurals.history_day_count, row.count, row.count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChapterCard(
    entry: HistoryEntry,
    cover: String?,
    time: String,
    shape: Shape,
    index: Int,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().appearing(index).bouncyClickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = cover,
                contentDescription = null,
                shape = RoundedCornerShape(14.dp),
                placeholderTitle = entry.seriesName,
                modifier = Modifier.width(46.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.seriesName.ifBlank { entry.title },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        entry.title.takeIf { it.isNotBlank() && entry.seriesName.isNotBlank() },
                        time,
                        stringResource(R.string.history_skimmed).takeIf { !entry.read },
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.history_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlagsTab(
    flags: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
    onNote: (Bookmark, String) -> Unit,
) {
    if (flags.isEmpty()) {
        EmptyPage(Icons.Filled.Bookmark, R.string.flags_empty_title, R.string.flags_empty_body)
        return
    }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    val sorted = remember(flags) { flags.sortedByDescending { it.at } }
    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 24.dp + LocalFloatingBarInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(sorted, key = { "${it.chapterId}-${it.page}" }) { flag ->
            val index = sorted.indexOf(flag)
            FlagCard(
                flag = flag,
                shape = blockShape(index, sorted.size),
                index = index,
                onOpen = { onOpen(flag) },
                onEdit = { editing = flag },
                onRemove = { onRemove(flag) },
            )
        }
    }
    editing?.let { flag ->
        NoteDialog(initial = flag.note, onSave = { onNote(flag, it); editing = null }, onSkip = { editing = null })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlagCard(
    flag: Bookmark,
    shape: Shape,
    index: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().appearing(index).bouncyClickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (flag.page + 1).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    flag.seriesName.ifBlank { flag.title },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        flag.title.takeIf { it.isNotBlank() && flag.seriesName.isNotBlank() },
                        stringResource(R.string.flag_page, flag.page + 1),
                    )
                        .filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (flag.note.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 16.dp,
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            flag.note,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.flag_edit_note),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.history_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.appearing(index: Int): Modifier {
    val style = LocalAppSettings.current.motionStyle
    var shown by remember { mutableStateOf(style == MotionStyle.Reduced || index > 8) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index * 45L)
            shown = true
        }
    }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "card-in",
    )
    return graphicsLayer {
        alpha = appear.coerceIn(0f, 1f)
        translationY = (1f - appear) * 40f
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyPage(icon: ImageVector, title: Int, body: Int) {
    val turn by rememberInfiniteTransition(label = "empty").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "empty-turn",
    )
    val style = LocalAppSettings.current.motionStyle
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 24.dp)) {
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer { rotationZ = if (style == MotionStyle.Reduced) 0f else turn }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.history_today)
        today.minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
