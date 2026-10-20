package com.romanianguy.klover.ui.series

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.chapters.InnerChapter
import com.romanianguy.klover.data.chapters.innerReadFraction
import com.romanianguy.klover.data.chapters.markKey
import com.romanianguy.klover.data.chapters.numberedTitle
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.downloads.DownloadEntry
import com.romanianguy.klover.data.downloads.PageRange
import com.romanianguy.klover.data.downloads.partBadge
import com.romanianguy.klover.data.downloads.summarizeDownloads
import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.stats.LeftText
import com.romanianguy.klover.data.stats.leftText
import com.romanianguy.klover.data.stats.volumeSecondsLeft
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.PageSheet
import com.romanianguy.klover.ui.common.bouncyClickable
import kotlinx.coroutines.delay

private class VolumeRow(
    val key: String,
    val title: String,
    val number: String,
    val pages: Int,
    val read: Float,
    val chapterId: Int,
    // zero-based page it opens at, or -1 to resume
    val startPage: Int,
    val file: ChapterDto,
    val inner: InnerChapter? = null,
)

private const val READ = 0.99f

private const val DOWNLOAD_AHEAD = 5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VolumeSheet(
    tile: Tile,
    marks: Map<String, Boolean>,
    secondsPerPage: Float?,
    waitingToSync: Boolean,
    loadInner: suspend (tile: Tile) -> List<InnerChapter>,
    onRead: (chapterId: Int) -> Unit,
    onReadFrom: (chapterId: Int, page: Int) -> Unit,
    onMarkFile: (chapterId: Int, read: Boolean, onFailed: () -> Unit) -> Unit,
    onMarkInner: (
        file: ChapterDto,
        chapters: List<InnerChapter>,
        chapter: InnerChapter,
        read: Boolean,
        pagesRead: Int,
        onFailed: () -> Unit,
    ) -> Int,
    downloads: Map<Int, DownloadEntry>,
    canDownload: Boolean,
    onToggleDownload: (file: ChapterDto, part: PageRange?) -> Unit,
    onDownloadMany: (parts: List<Pair<ChapterDto, PageRange?>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val files = remember(tile.key) { tile.chapters.sortedBy { it.minNumber } }
    val single = files.singleOrNull()
    val chapterLabel = stringResource(R.string.series_chapter)
    var rows by remember(tile.key) { mutableStateOf<List<VolumeRow>?>(null) }
    var inner by remember(tile.key) { mutableStateOf<List<InnerChapter>>(emptyList()) }
    var estimated by remember(tile.key) { mutableStateOf(false) }
    // how far into the single file reading is; marking chapters moves it before the page reloads
    var pagesRead by remember(tile.key) { mutableIntStateOf(single?.pagesRead ?: 0) }
    // whole files marked from here, until the page's progress catches up
    val fileRead = remember(tile.key) { mutableStateMapOf<Int, Float>() }
    var menuFor by remember(tile.key) { mutableStateOf<String?>(null) }

    LaunchedEffect(tile.key) {
        if (files.size > 1) {
            rows = files.map { it.asRow(chapterLabel) }.distinctBy { it.key }
        } else {
            val found = if (single == null) emptyList() else loadInner(tile)
            if (single == null || found.isEmpty()) {
                onDismiss()
                onRead(tile.chapterId)
            } else {
                estimated = found.any { it.estimated }
                inner = found
                rows = found.map { it.asRow(single) }.distinctBy { it.key }
            }
        }
    }

    fun readOf(row: VolumeRow): Float = when {
        row.inner != null && single != null -> innerReadFraction(row.inner, pagesRead, marks[markKey(single.id, row.inner.start)])
        else -> fileRead[row.chapterId] ?: row.read
    }

    fun partOf(row: VolumeRow): PageRange? = row.inner?.let { chapter ->
        PageRange(
            chapter.start,
            chapter.endExclusive,
            chapter.number?.let { numberedTitle(chapterLabel, it) } ?: chapter.title,
        )
    }

    fun badgeOf(row: VolumeRow): DownloadBadge {
        val entry = downloads[row.file.id]
        return row.inner?.let { partBadge(entry, it.start, it.endExclusive) } ?: summarizeDownloads(listOf(entry))
    }

    // the server was unreachable or said no: put back what the sheet showed in advance
    var problem by remember(tile.key) { mutableStateOf(false) }
    LaunchedEffect(problem) {
        if (problem) {
            delay(4_000)
            problem = false
        }
    }

    fun mark(row: VolumeRow, read: Boolean) {
        if (row.inner != null && single != null) {
            val before = pagesRead
            pagesRead = onMarkInner(single, inner, row.inner, read, before) {
                pagesRead = before
                problem = true
            }
        } else {
            val before = fileRead[row.chapterId]
            fileRead[row.chapterId] = if (read) 1f else 0f
            onMarkFile(row.chapterId, read) {
                if (before == null) fileRead.remove(row.chapterId) else fileRead[row.chapterId] = before
                problem = true
            }
        }
    }

    PageSheet(onDismissRequest = onDismiss) {
        val list = rows
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            item(key = "header") {
                SheetHeader(
                    tile = tile,
                    rows = list,
                    readOf = ::readOf,
                    estimated = estimated,
                    secondsPerPage = secondsPerPage,
                    waitingToSync = waitingToSync,
                    problem = problem,
                    onStart = {
                        onDismiss()
                        onRead(tile.chapterId)
                    },
                )
            }
            if (list == null) {
                item(key = "loading") { LoadingState(Modifier.padding(32.dp)) }
            } else {
                itemsIndexed(list, key = { _, row -> row.key }) { index, row ->
                    val read = readOf(row)
                    val download = badgeOf(row)
                    val wholeFileDownloaded = row.inner != null && downloads[row.file.id]?.isWhole == true
                    val shape = rowShape(index, list.size)
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SwipeableChapter(
                            read = read >= READ,
                            download = download,
                            canDownload = canDownload,
                            removesWholeVolume = wholeFileDownloaded,
                            shape = shape,
                            onToggleRead = { mark(row, read < READ) },
                            onToggleDownload = { onToggleDownload(row.file, partOf(row)) },
                        ) {
                            ChapterRow(
                                row = row,
                                index = index,
                                shape = shape,
                                read = read,
                                download = download,
                                onOpen = {
                                    onDismiss()
                                    if (row.startPage >= 0) onReadFrom(
                                        row.chapterId,
                                        row.startPage,
                                    ) else onRead(row.chapterId)
                                },
                                onHold = { menuFor = row.key },
                            )
                        }
                        DropdownMenu(expanded = menuFor == row.key, onDismissRequest = { menuFor = null }) {
                            if (read < READ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_mark_read)) },
                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                                    onClick = { menuFor = null; mark(row, true) },
                                )
                            }
                            if (read > 0f) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_mark_unread)) },
                                    leadingIcon = { Icon(Icons.Outlined.RemoveDone, contentDescription = null) },
                                    onClick = { menuFor = null; mark(row, false) },
                                )
                            }
                            if (canDownload) {
                                val failedDownload = download is DownloadBadge.Active && download.failed
                                val have = (download is DownloadBadge.Done || download is DownloadBadge.Active) && !failedDownload
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                when {
                                                    failedDownload -> R.string.swipe_retry_download
                                                    have && wholeFileDownloaded -> R.string.swipe_remove_volume_download
                                                    have -> R.string.swipe_remove_download
                                                    else -> R.string.swipe_download
                                                },
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(if (have) Icons.Outlined.DeleteOutline else Icons.Outlined.Download, contentDescription = null)
                                    },
                                    onClick = { menuFor = null; onToggleDownload(row.file, partOf(row)) },
                                )
                                val ahead = list.drop(index).take(DOWNLOAD_AHEAD)
                                    .filter { badgeOf(it) == DownloadBadge.None || badgeOf(it) == DownloadBadge.Partial }
                                if (ahead.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.swipe_download_next, ahead.size)) },
                                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                        onClick = { menuFor = null; onDownloadMany(ahead.map { it.file to partOf(it) }) },
                                    )
                                }
                            }
                            val unreadAbove = list.take(index).filter { readOf(it) < READ }
                            if (unreadAbove.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.swipe_mark_above_read)) },
                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                                    onClick = { menuFor = null; unreadAbove.forEach { mark(it, true) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetHeader(
    tile: Tile,
    rows: List<VolumeRow>?,
    readOf: (VolumeRow) -> Float,
    estimated: Boolean,
    secondsPerPage: Float?,
    waitingToSync: Boolean,
    problem: Boolean,
    onStart: () -> Unit,
) {
    val motion = LocalAppSettings.current.motionStyle
    Column(
        Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val turn by rememberInfiniteTransition(label = "volume-turn").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
                label = "volume-turn-angle",
            )
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = if (motion == MotionStyle.Reduced) 0f else turn }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tile.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = tile.subtitle
                val count = rows?.size
                val unread = rows?.let { list -> list.sumOf { ((1f - readOf(it)) * it.pages).toDouble() }.toFloat() }
                val left = unread?.let { volumeSecondsLeft(it, secondsPerPage) }?.let { seconds ->
                    when (val t = leftText(seconds)) {
                        LeftText.UnderAMinute -> stringResource(
                            R.string.volume_left,
                            stringResource(R.string.left_under_minute),
                        )
                        is LeftText.Minutes -> stringResource(
                            R.string.volume_left,
                            stringResource(R.string.left_minutes, t.minutes),
                        )
                        is LeftText.HoursMinutes -> stringResource(
                            R.string.volume_left,
                            stringResource(R.string.left_hours_minutes, t.hours, t.minutes),
                        )
                    }
                }
                if (count != null || subtitle != null) {
                    Text(
                        text = listOfNotNull(
                            count?.let { pluralStringResource(R.plurals.volume_chapters_count, it, it) },
                            subtitle,
                            left,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!rows.isNullOrEmpty()) {
            val fractions = rows.map { readOf(it) }
            val done = fractions.count { it >= READ }
            val total = rows.sumOf { it.pages }.coerceAtLeast(1)
            val overall = rows.indices.sumOf { (fractions[it] * rows[it].pages).toDouble() }.toFloat() / total
            val animated by animateFloatAsState(overall.coerceIn(0f, 1f), tween(450), label = "volume-progress")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.volume_read_progress, done, rows.size),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "${(animated * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
            }
        }

        // "Read again" once the whole volume is read, "Continue" part way
        val finished = !rows.isNullOrEmpty() && rows.all { readOf(it) >= READ }
        FilledTonalButton(onClick = onStart) {
            Text(
                stringResource(
                    when {
                        finished -> R.string.series_read_again
                        tile.progress == null -> R.string.series_start
                        else -> R.string.series_continue
                    },
                ),
            )
        }

        if (estimated) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.volume_chapters_estimated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        if (!rows.isNullOrEmpty()) {
            Text(
                stringResource(
                    when {
                        problem -> R.string.volume_mark_failed
                        waitingToSync -> R.string.volume_waiting_to_sync
                        else -> R.string.volume_swipe_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChapterRow(
    row: VolumeRow,
    index: Int,
    shape: Shape,
    read: Float,
    download: DownloadBadge,
    onOpen: () -> Unit,
    onHold: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val style = LocalAppSettings.current.motionStyle
    val done = read >= READ

    var shown by remember { mutableStateOf(style == MotionStyle.Reduced || index > 9) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index * 40L)
            shown = true
        }
    }
    val appear by animateFloatAsState(if (shown) 1f else 0f, motion.defaultSpatialSpec(), label = "row-in")

    val container by animateColorAsState(
        when {
            done -> scheme.secondaryContainer
            read > 0f -> scheme.primaryContainer.copy(alpha = 0.55f)
            else -> scheme.surfaceContainerHigh
        },
        motion.defaultEffectsSpec(),
        label = "row-color",
    )

    Surface(
        shape = shape,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.coerceIn(0f, 1f)
                translationY = (1f - appear) * 48f
            }
            .bouncyClickable(
                onLongClick = onHold,
                onLongClickLabel = stringResource(R.string.action_mark_read),
                onClick = onOpen,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChapterBadge(number = row.number.ifBlank { (index + 1).toString() }, read = read)
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val pages = pluralStringResource(R.plurals.series_pages, row.pages, row.pages)
                Text(
                    text = if (read in 0.01f..0.98f) pages + " · " + stringResource(
                        R.string.volume_chapter_percent,
                        (read * 100).toInt(),
                    ) else pages,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            DownloadMark(download)
        }
    }
}

private fun rowShape(index: Int, count: Int): Shape {
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
private fun DownloadMark(badge: DownloadBadge) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = badge,
        contentKey = { it::class },
        transitionSpec = {
            (scaleIn(motion.defaultSpatialSpec(), initialScale = 0.4f) + fadeIn(motion.defaultEffectsSpec())) togetherWith
                (scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec()))
        },
        label = "download-mark",
    ) { shown ->
        if (shown == DownloadBadge.None) {
            Box(Modifier.size(0.dp))
        } else {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                when (shown) {
                    DownloadBadge.Partial -> Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    DownloadBadge.Done -> Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = stringResource(R.string.downloads_badge_done),
                        tint = scheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    is DownloadBadge.Active -> when {
                        // gave up (server unreachable, say); swiping again retries
                        shown.failed -> Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = stringResource(R.string.swipe_retry_download),
                            tint = scheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        shown.paused -> Icon(
                            Icons.Outlined.Pause,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        else -> {
                            val shownProgress by animateFloatAsState(
                                shown.progress.coerceIn(0.06f, 1f),
                                tween(450),
                                label = "download-mark-progress",
                            )
                            CircularProgressIndicator(
                                progress = { shownProgress },
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                trackColor = scheme.primary.copy(alpha = 0.18f),
                                gapSize = 0.dp,
                            )
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = stringResource(R.string.downloads_badge_active),
                                modifier = Modifier.size(14.dp),
                                tint = scheme.primary,
                            )
                        }
                    }
                    DownloadBadge.None -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChapterBadge(number: String, read: Float) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val done = read >= READ
    val ring by animateFloatAsState(read.coerceIn(0f, 1f), tween(400), label = "badge-ring")
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.5f) + fadeIn(motion.fastEffectsSpec())) togetherWith
                    (scaleOut(motion.fastSpatialSpec(), targetScale = 0.5f) + fadeOut(motion.fastEffectsSpec()))
            },
            label = "badge",
        ) { finished ->
            if (finished) {
                Box(
                    Modifier.size(50.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerHighest))
                    if (ring > 0.01f) {
                        CircularProgressIndicator(
                            progress = { ring },
                            modifier = Modifier.size(50.dp),
                            strokeWidth = 3.dp,
                            trackColor = Color.Transparent,
                        )
                    }
                    Text(number, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = scheme.onSurface)
                }
            }
        }
    }
}

private fun ChapterDto.asRow(chapterLabel: String) = VolumeRow(
    key = "f$id",
    title = (title?.takeIf { it.isNotBlank() } ?: numberedTitle(chapterLabel, number)).trim(),
    number = number.orEmpty().trim(),
    pages = pages,
    read = if (pages > 0) (pagesRead.toFloat() / pages).coerceIn(0f, 1f) else 0f,
    chapterId = id,
    startPage = -1,
    file = this,
)

private fun InnerChapter.asRow(file: ChapterDto) = VolumeRow(
    key = "i${file.id}-$start",
    title = title,
    number = number.orEmpty(),
    pages = pages,
    read = if (pages > 0) ((file.pagesRead - start).toFloat() / pages).coerceIn(0f, 1f) else 0f,
    chapterId = file.id,
    startPage = start,
    file = file,
    inner = this,
)
