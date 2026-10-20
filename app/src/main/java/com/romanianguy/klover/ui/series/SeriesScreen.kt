package com.romanianguy.klover.ui.series

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.chapters.numberedTitle
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.downloads.DownloadRequest
import com.romanianguy.klover.data.downloads.chapterRequest
import com.romanianguy.klover.data.downloads.partBadge
import com.romanianguy.klover.data.downloads.requestsForDetail
import com.romanianguy.klover.data.downloads.seriesDownloadBadge
import com.romanianguy.klover.data.downloads.summarizeDownloads
import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.model.SeriesMetadataDto
import com.romanianguy.klover.data.model.VolumeDto
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.ui.common.CoverImage
import com.romanianguy.klover.ui.common.DownloadControl
import com.romanianguy.klover.ui.common.ErrorState
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.LocalOpenTransition
import com.romanianguy.klover.ui.common.SeriesCard
import com.romanianguy.klover.ui.common.displayName
import com.romanianguy.klover.ui.common.openTargetCover
import com.romanianguy.klover.ui.common.progress
import com.romanianguy.klover.ui.common.readActions
import com.romanianguy.klover.ui.common.rememberNotificationAsker
import com.romanianguy.klover.ui.common.revealOnOpen
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal data class Tile(
    val key: String,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val progress: Float?,
    val chapterId: Int,
    val volumeId: Int? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val volumeOrder: Float = 0f,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeriesScreen(
    seriesId: Int,
    onBack: (() -> Unit)?,
    onRead: (chapterId: Int) -> Unit,
    onReadFrom: (chapterId: Int, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = koinViewModel(key = "series-$seriesId") { parametersOf(seriesId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val urls = koinInject<ServerUrls>()
    LaunchedEffect(Unit) { viewModel.onShown() }
    var openVolume by remember { mutableStateOf<Tile?>(null) }

    // the back gesture drives the cover's flight home: follows the finger, flies on past the middle, settles back if
    // cancelled
    val openTransition = LocalOpenTransition.current
    val gestureBack = openTransition != null && onBack != null && openTransition.canClose(seriesId)
    PredictiveBackHandler(enabled = gestureBack) { events ->
        val ot = openTransition ?: return@PredictiveBackHandler
        ot.startGesture()
        try {
            events.collect { ot.updateGesture(it.progress) }
            ot.commitGesture()
            onBack?.invoke()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val from = ot.gesture ?: 0f
                animate(from, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) { v, _ -> ot.updateGesture(v) }
                ot.cancelGesture()
            }
            throw e
        }
    }

    val downloads = koinInject<DownloadManager>()
    val downloadEntries by downloads.entries.collectAsStateWithLifecycle()
    val downloadsByChapter = remember(downloadEntries) { downloadEntries.associateBy { it.chapterId } }
    val askNotifications = rememberNotificationAsker()
    val volumeLabel = stringResource(R.string.series_volume)
    val chapterLabel = stringResource(R.string.series_chapter)
    val startDownloads = { requests: List<DownloadRequest> ->
        if (requests.isNotEmpty()) {
            askNotifications()
            downloads.enqueue(requests)
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(
        modifier.graphicsLayer {
            val g = openTransition?.gesture ?: 0f
            scaleX = 1f - 0.05f * g
            scaleY = 1f - 0.05f * g
            alpha = 1f - 0.35f * g
        },
    ) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {},
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    val seriesLoaded = state.series is Loadable.Ready
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = seriesLoaded) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.series_mark_read)) },
                                leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.markSeries(true) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.series_mark_unread)) },
                                leadingIcon = { Icon(Icons.Outlined.RemoveDone, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.markSeries(false) },
                            )
                            val readySeries = (state.series as? Loadable.Ready)?.value
                            val readyDetail = (state.detail as? Loadable.Ready)?.value
                            if (readySeries != null && readyDetail != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.series_download_all)) },
                                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        startDownloads(
                                            requestsForDetail(readySeries, readyDetail, volumeLabel, chapterLabel, onlyUnread = false),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.series_download_unread)) },
                                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        startDownloads(
                                            requestsForDetail(readySeries, readyDetail, volumeLabel, chapterLabel, onlyUnread = true),
                                        )
                                    },
                                )
                                if (downloadEntries.any { it.seriesId == readySeries.id }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.series_download_remove)) },
                                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                        onClick = { menuOpen = false; downloads.removeSeries(readySeries.id) },
                                    )
                                }
                            }
                            state.wantToRead?.let { onShelf ->
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(if (onShelf) R.string.action_want_remove else R.string.action_want_add))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (onShelf) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = { menuOpen = false; viewModel.setWantToRead(!onShelf) },
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
        val series = (state.series as? Loadable.Ready)?.value
        val detail = (state.detail as? Loadable.Ready)?.value
        when {
            state.series is Loadable.Failed && state.detail is Loadable.Failed ->
                ErrorState(
                    stringResource(R.string.error_generic),
                    onRetry = viewModel::reload,
                    modifier = Modifier.padding(padding),
                )

            series == null || detail == null -> LoadingState(Modifier.padding(padding))

            else -> SeriesContent(
                series = series,
                detail = detail,
                resume = state.resume,
                metadata = state.metadata,
                urls = urls,
                onRead = onRead,
                onOpenTile = { tile -> if (tile.volumeId != null) openVolume = tile else onRead(tile.chapterId) },
                seriesDownload = DownloadControl(
                    read = { seriesDownloadBadge(downloadEntries.filter { it.seriesId == series.id }, series.pages) },
                    onToggle = {
                        when (seriesDownloadBadge(downloadEntries.filter { it.seriesId == series.id }, series.pages)) {
                            DownloadBadge.Done, is DownloadBadge.Active -> downloads.removeSeries(series.id)
                            else -> startDownloads(requestsForDetail(series, detail, volumeLabel, chapterLabel, onlyUnread = false))
                        }
                    },
                ),
                downloadBadge = { tile -> summarizeDownloads(tile.chapters.map { downloadsByChapter[it.id] }) },
                onToggleDownload = { tile ->
                    val ids = tile.chapters.map { it.id }
                    when (summarizeDownloads(tile.chapters.map { downloadsByChapter[it.id] })) {
                        DownloadBadge.Done, is DownloadBadge.Active -> downloads.remove(ids)
                        else -> startDownloads(
                            tile.chapters.map { chapterRequest(series, it, tile.titleFor(it, chapterLabel), tile.volumeOrder) },
                        )
                    }
                },
                onMarkTile = { tile, read ->
                    if (tile.volumeId != null) viewModel.markVolume(tile.volumeId, read)
                    else viewModel.markChapter(tile.chapterId, read)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
    openVolume?.let { tile ->
        val marks by viewModel.innerMarks.collectAsStateWithLifecycle()
        val secondsPerPage by viewModel.secondsPerPage.collectAsStateWithLifecycle()
        val waitingToSync by viewModel.waitingToSync.collectAsStateWithLifecycle()
        VolumeSheet(
            tile = tile,
            marks = marks,
            secondsPerPage = secondsPerPage,
            waitingToSync = waitingToSync,
            loadInner = viewModel::innerChaptersOf,
            onRead = onRead,
            onReadFrom = onReadFrom,
            onMarkFile = { chapterId, read, onFailed -> viewModel.markChapter(chapterId, read, onFailed) },
            onMarkInner = viewModel::markInner,
            downloads = downloadsByChapter,
            canDownload = (state.series as? Loadable.Ready)?.value != null,
            onToggleDownload = { file, part ->
                val owner = (state.series as? Loadable.Ready)?.value
                val entry = downloadsByChapter[file.id]
                val badge = if (part == null) summarizeDownloads(listOf(entry)) else partBadge(
                    entry,
                    part.from,
                    part.until,
                )
                when {
                    badge is DownloadBadge.Active && badge.failed ->
                        if (owner != null) {
                            startDownloads(
                                listOf(chapterRequest(owner, file, tile.titleFor(file, chapterLabel), tile.volumeOrder).copy(range = part)),
                            )
                        }
                    // done or in flight: asking again removes it; inside a file only its own pages go
                    badge == DownloadBadge.Done || badge is DownloadBadge.Active ->
                        if (part == null) downloads.remove(listOf(file.id)) else downloads.removePart(file.id, part)
                    else -> if (owner != null) {
                        startDownloads(
                            listOf(chapterRequest(owner, file, tile.titleFor(file, chapterLabel), tile.volumeOrder).copy(range = part)),
                        )
                    }
                }
            },
            onDownloadMany = { parts ->
                val owner = (state.series as? Loadable.Ready)?.value
                if (owner != null) {
                    startDownloads(
                        parts.map { (file, part) ->
                            chapterRequest(owner, file, tile.titleFor(file, chapterLabel), tile.volumeOrder).copy(range = part)
                        },
                    )
                }
            },
            onDismiss = { openVolume = null },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeriesContent(
    series: SeriesDto,
    detail: SeriesDetailDto,
    resume: ChapterDto?,
    metadata: SeriesMetadataDto?,
    urls: ServerUrls,
    onRead: (Int) -> Unit,
    onOpenTile: (Tile) -> Unit,
    onMarkTile: (Tile, Boolean) -> Unit,
    downloadBadge: (Tile) -> DownloadBadge,
    onToggleDownload: (Tile) -> Unit,
    seriesDownload: DownloadControl,
    modifier: Modifier = Modifier,
) {
    val volumeLabel = stringResource(R.string.series_volume)
    val chapterLabel = stringResource(R.string.series_chapter)
    val resources = LocalResources.current
    val pagesLabel = { pages: Int -> resources.getQuantityString(R.plurals.series_pages, pages, pages) }

    val volumes = detail.volumes.distinctBy { it.id }.map { it.toTile(urls, volumeLabel, pagesLabel) }
    // Kavita lists a chapter that is in no volume under both chapters and storylineChapters
    val chapters = (detail.chapters + detail.storylineChapters).distinctBy { it.id }
        .map { it.toTile(urls, chapterLabel, pagesLabel) }
    val specials = detail.specials.distinctBy { it.id }.map { it.toTile(urls, chapterLabel, pagesLabel) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LocalAppSettings.current.coverSize.minWidthDp.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp + LocalFloatingBarInset.current),
        horizontalArrangement = Arrangement.spacedBy(LocalAppSettings.current.gridSpacing.dp.dp),
        verticalArrangement = Arrangement.spacedBy(LocalAppSettings.current.gridSpacing.dp.dp + 4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Header(series, detail, resume, metadata, urls, onRead)
        }
        tileSection(
            R.string.series_volumes,
            volumes,
            onOpenTile,
            onMarkTile,
            downloadBadge,
            onToggleDownload,
            seriesDownload,
        )
        tileSection(
            R.string.series_chapters,
            chapters,
            onOpenTile,
            onMarkTile,
            downloadBadge,
            onToggleDownload,
            seriesDownload,
        )
        tileSection(
            R.string.series_specials,
            specials,
            onOpenTile,
            onMarkTile,
            downloadBadge,
            onToggleDownload,
            seriesDownload,
        )
    }
}

private fun LazyGridScope.tileSection(
    title: Int,
    tiles: List<Tile>,
    onOpen: (Tile) -> Unit,
    onMarkTile: (Tile, Boolean) -> Unit,
    downloadBadge: (Tile) -> DownloadBadge,
    onToggleDownload: (Tile) -> Unit,
    seriesDownload: DownloadControl,
) {
    if (tiles.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }, key = "title-$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier
                .animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(top = 8.dp)
                .revealOnOpen(1),
        )
    }
    itemsIndexed(tiles, key = { _, tile -> "$title-${tile.key}" }) { index, tile ->
        val badge = downloadBadge(tile)
        SeriesCard(
            modifier = Modifier
                .animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .revealOnOpen(index + 2),
            title = tile.title,
            subtitle = tile.subtitle,
            coverUrl = tile.coverUrl,
            progress = tile.progress,
            onClick = { onOpen(tile) },
            downloadBadge = badge,
            actions = readActions(
                progress = tile.progress,
                onMarkRead = { onMarkTile(tile, true) },
                onMarkUnread = { onMarkTile(tile, false) },
                download = DownloadControl(badge) { onToggleDownload(tile) },
                downloadSeries = seriesDownload,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Header(
    series: SeriesDto,
    detail: SeriesDetailDto,
    resume: ChapterDto?,
    metadata: SeriesMetadataDto?,
    urls: ServerUrls,
    onRead: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sideBySide = maxWidth >= 520.dp
        val coverWidth: Dp = if (sideBySide) 200.dp else 180.dp

        val cover = @Composable {
            CoverImage(
                url = urls.seriesCover(series.id),
                contentDescription = series.displayName(),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.width(coverWidth).openTargetCover(),
            )
        }
        val info = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = series.displayName(),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = listOfNotNull(series.localizedName, series.originalName)
                    .filter { it.isNotBlank() && it != series.name }
                    .distinct()
                    .joinToString(" · ")
                if (secondary.isNotEmpty()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    series.libraryName?.takeIf { it.isNotBlank() }?.let { InfoChip(it) }
                    InfoChip(stringResource(ContentFormat.from(series.format).labelRes()))
                    metadata?.releaseYear?.takeIf { it > 0 }?.let { InfoChip(it.toString()) }
                    metadata?.publicationStatus?.let { InfoChip(stringResource(publicationStatusRes(it))) }
                    metadata?.genres?.forEach { genre -> if (genre.title.isNotBlank()) InfoChip(genre.title) }
                }
                metadata?.writers?.map { it.name }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = stringResource(R.string.series_by, it.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                metadata?.summary?.let(::htmlToPlainText)?.takeIf { it.isNotBlank() }?.let { ExpandableSummary(it) }
                // the changes up here come and go with motion, everything below follows the header's height
                val motion = MaterialTheme.motionScheme
                val progress = series.progress()
                // The numbers stay while the block fades out, so it does not show zeros on its way out.
                val lastProgress = remember { ProgressSnapshot() }
                if (progress != null) lastProgress.set(progress, series.pagesRead, series.pages)
                Column {
                    AnimatedVisibility(
                        visible = progress != null,
                        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
                    ) {
                        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearWavyProgressIndicator(
                                progress = { lastProgress.fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.series_progress,
                                    lastProgress.pages,
                                    lastProgress.read,
                                    lastProgress.pages,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (resume != null) {
                        Button(
                            onClick = { onRead(resume.id) },
                            modifier = Modifier.animateContentSize(motion.defaultSpatialSpec()),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                            AnimatedContent(
                                targetState = series.pagesRead > 0,
                                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                                label = "read-button-label",
                            ) { continuing ->
                                Text(
                                    text = stringResource(if (continuing) R.string.series_continue else R.string.series_start),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                cover()
                info(Modifier.weight(1f).revealOnOpen(0))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                cover()
                info(Modifier.fillMaxWidth().revealOnOpen(0))
            }
        }
    }
}

private class ProgressSnapshot {
    var fraction = 0f
    var read = 0
    var pages = 0

    fun set(fraction: Float, read: Int, pages: Int) {
        this.fraction = fraction
        this.read = read
        this.pages = pages
    }
}

private fun Tile.titleFor(chapter: ChapterDto, chapterLabel: String): String =
    if (chapters.size > 1) "$title · $chapterLabel ${chapter.number.orEmpty()}".trim() else title

private fun VolumeDto.toTile(urls: ServerUrls, label: String, pagesLabel: (Int) -> String): Tile {
    val target = chapters.firstOrNull { it.pagesRead < it.pages } ?: chapters.firstOrNull()
    return Tile(
        key = "v$id",
        title = numberedTitle(label, name),
        subtitle = pagesLabel(pages),
        coverUrl = urls.volumeCover(id),
        progress = if (pages > 0 && pagesRead > 0) (pagesRead.toFloat() / pages).coerceIn(0f, 1f) else null,
        chapterId = target?.id ?: 0,
        volumeId = id,
        chapters = chapters,
        volumeOrder = minNumber,
    )
}

private fun ChapterDto.toTile(urls: ServerUrls, label: String, pagesLabel: (Int) -> String): Tile {
    val title = when {
        isSpecial -> titleName?.takeIf { it.isNotBlank() } ?: title.orEmpty()
        else -> numberedTitle(label, number)
    }
    return Tile(
        key = "c$id",
        title = title,
        subtitle = pagesLabel(pages),
        coverUrl = urls.chapterCover(id),
        progress = if (pages > 0 && pagesRead > 0) (pagesRead.toFloat() / pages).coerceIn(0f, 1f) else null,
        chapterId = id,
        chapters = listOf(this),
    )
}

private fun ContentFormat.labelRes(): Int = when (this) {
    ContentFormat.Epub -> R.string.format_epub
    ContentFormat.Pdf -> R.string.format_pdf
    ContentFormat.Image, ContentFormat.Archive -> R.string.format_comic
    ContentFormat.Unknown -> R.string.format_unknown
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ExpandableSummary(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .animateContentSize()
            .clickable { expanded = !expanded },
    )
}

internal fun htmlToPlainText(html: String): String =
    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

private fun publicationStatusRes(status: Int): Int = when (status) {
    1 -> R.string.status_hiatus
    2 -> R.string.status_completed
    3 -> R.string.status_cancelled
    4 -> R.string.status_ended
    else -> R.string.status_ongoing
}
