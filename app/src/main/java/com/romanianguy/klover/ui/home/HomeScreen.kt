package com.romanianguy.klover.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.history.LastReadStore
import com.romanianguy.klover.data.model.GroupedSeriesDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.ui.common.BadgeLayer
import com.romanianguy.klover.ui.common.CoverImage
import com.romanianguy.klover.ui.common.DownloadBadgeIcon
import com.romanianguy.klover.ui.common.ErrorState
import com.romanianguy.klover.ui.common.LiveBounds
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.LocalOpenTransition
import com.romanianguy.klover.ui.common.LocalPeekController
import com.romanianguy.klover.ui.common.OpenRequest
import com.romanianguy.klover.ui.common.PeekRequest
import com.romanianguy.klover.ui.common.ReadActions
import com.romanianguy.klover.ui.common.SectionHeader
import com.romanianguy.klover.ui.common.SeriesCard
import com.romanianguy.klover.ui.common.SeriesDownloadInfo
import com.romanianguy.klover.ui.common.SeriesDownloads
import com.romanianguy.klover.ui.common.WantToReadControl
import com.romanianguy.klover.ui.common.bouncyClickable
import com.romanianguy.klover.ui.common.displayName
import com.romanianguy.klover.ui.common.flightEnabled
import com.romanianguy.klover.ui.common.originCoverAlpha
import com.romanianguy.klover.ui.common.progress
import com.romanianguy.klover.ui.common.readActions
import com.romanianguy.klover.ui.common.rememberSeriesDownloadControls
import com.romanianguy.klover.ui.common.rememberWantToReadControls
import com.romanianguy.klover.ui.common.reveal
import com.romanianguy.klover.ui.common.themeCornerDp
import com.romanianguy.klover.ui.common.toDownloadInfo
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.profiles.ProfileButton
import com.romanianguy.klover.ui.stats.ContinueCard
import com.romanianguy.klover.ui.stats.StatsCard
import com.romanianguy.klover.ui.stats.rememberStatsSummary
import com.romanianguy.klover.ui.update.UpdateCard
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenSeries: (Int) -> Unit,
    onOpenStats: () -> Unit = {},
    onContinue: (chapterId: Int) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenGarden: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val lastRead by koinInject<LastReadStore>().data.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val urls = koinInject<ServerUrls>()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val wantToReadFor = rememberWantToReadControls()
    val seriesDownloadFor = rememberSeriesDownloadControls()
    val statsSummary = rememberStatsSummary()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                subtitle = { Text(stringResource(R.string.home_subtitle)) },
                actions = {
                    // The garden, badges and weekly goal; off with the switch in Settings.
                    if (LocalAppSettings.current.garden) {
                        IconButton(onClick = onOpenGarden) {
                            Icon(
                                Icons.Outlined.Eco,
                                contentDescription = stringResource(R.string.garden_title),
                            )
                        }
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = stringResource(R.string.history_title),
                        )
                    }
                    ProfileButton()
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val homeSettings = LocalAppSettings.current
            val everythingFailed = listOf(state.onDeck, state.recentlyUpdated, state.recentlyAdded)
                .all { it is Loadable.Failed }
            if (everythingFailed) {
                ErrorState(stringResource(R.string.error_generic), onRetry = viewModel::refresh)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingBarInset.current),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "update") {
                        UpdateCard(Modifier.padding(horizontal = 16.dp))
                    }
                    if (homeSettings.incognito) {
                        item(key = "incognito") {
                            Surface(
                                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null)
                                    Text(
                                        stringResource(R.string.incognito_banner),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    if (homeSettings.homeStats && statsSummary.hasData) {
                        item(key = "stats") {
                            StatsCard(
                                summary = statsSummary,
                                onClick = onOpenStats,
                                continueWith = lastRead.takeIf { it.exists },
                                onContinue = onContinue,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else if (lastRead.exists && !homeSettings.incognito) {
                        // No statistics to show yet, but there is something to carry on with.
                        item(key = "continue") {
                            ContinueCard(lastRead, onContinue, Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    if (homeSettings.homeContinue) section(
                        title = R.string.home_on_deck,
                        data = state.onDeck,
                        isEmpty = { it.isEmpty() },
                    ) { items ->
                        OnDeckCarousel(
                            items,
                            urls,
                            onOpenSeries,
                            viewModel::markSeries,
                            wantToReadFor,
                            seriesDownloadFor,
                        )
                    }
                    if (homeSettings.homeUpdated) section(
                        title = R.string.home_recently_updated,
                        data = state.recentlyUpdated,
                        isEmpty = { it.isEmpty() },
                    ) { items ->
                        UpdatedRail(items, urls, onOpenSeries, viewModel::markSeries, wantToReadFor, seriesDownloadFor)
                    }
                    if (homeSettings.homeAdded) section(
                        title = R.string.home_recently_added,
                        data = state.recentlyAdded,
                        isEmpty = { it.isEmpty() },
                    ) { items ->
                        AddedRail(items, urls, onOpenSeries, viewModel::markSeries, wantToReadFor, seriesDownloadFor)
                    }
                }
            }
        }
    }
}

private fun <T> LazyListScope.section(
    title: Int,
    data: Loadable<List<T>>,
    isEmpty: (List<T>) -> Boolean,
    content: @Composable (List<T>) -> Unit,
) {
    when (data) {
        is Loadable.Loading -> item(key = "$title-loading") {
            SectionHeader(stringResource(title))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingState() }
        }

        is Loadable.Failed -> Unit // a failed rail simply stays hidden; pull to refresh retries it
        is Loadable.Ready -> if (!isEmpty(data.value)) item(key = "$title-content") {
            Column {
                SectionHeader(stringResource(title))
                content(data.value)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnDeckCarousel(
    items: List<SeriesDto>,
    urls: ServerUrls,
    onOpenSeries: (Int) -> Unit,
    onMarkSeries: (Int, Boolean) -> Unit,
    wantToReadFor: (Int) -> WantToReadControl,
    seriesDownloadFor: SeriesDownloads,
) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { items.size },
        preferredItemWidth = 220.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) { index ->
        val series = items[index]
        // The carousel hands out cards by position. Keyed by the series, the card in a slot is a new one when the
        // series
        // in it changes (this one was marked unread and left "Continue reading"): the old one is disposed, which is
        // how the
        // quick view learns its card is gone and fades the cover out instead of flying it onto the next series.
        androidx.compose.runtime.key(series.id) {
        val peek = LocalPeekController.current
        val key = remember { Any() }
        val pressSource = remember { MutableInteractionSource() }
        val pressedNow = pressSource.collectIsPressedAsState()
        val liveBounds = remember { LiveBounds { pressedNow.value } }
        val openTransition = LocalOpenTransition.current
        val originKey = "home-ondeck-${series.id}"
        val cardCorner = themeCornerDp()
        val lifted = peek?.activeKey == key || openTransition?.hiddenKey == originKey
        val coverAlpha = originCoverAlpha(lifted, openTransition)
        val flight = LocalAppSettings.current.flightEnabled()
        DisposableEffect(peek, key) { onDispose { peek?.cardGone(key) } }
        DisposableEffect(openTransition, originKey, liveBounds) {
            openTransition?.registerCard(originKey, liveBounds)
            onDispose { openTransition?.unregisterCard(originKey, liveBounds) }
        }
        Box(
            modifier = Modifier
                .height(300.dp)
                .maskClip(MaterialTheme.shapes.extraLarge)
                .onGloballyPositioned { liveBounds.record(it) }
                .bouncyClickable(
                    holdToExpand = peek != null && LocalAppSettings.current.holdToPeek,
                    interactionSource = pressSource,
                    onLongClick = peek?.takeIf { LocalAppSettings.current.holdToPeek }?.let { controller ->
                        {
                            controller.show(
                                PeekRequest(
                                    key = key,
                                    origin = liveBounds.rect,
                                    originNow = { liveBounds.current() },
                                    cornerRadius = cardCorner,
                                    coverUrl = urls.seriesCover(series.id),
                                    title = series.displayName(),
                                    subtitle = series.libraryName,
                                    actions = readActions(
                                        progress = series.progress(),
                                        onMarkRead = { onMarkSeries(series.id, true) },
                                        onMarkUnread = { onMarkSeries(series.id, false) },
                                        wantToRead = wantToReadFor(series.id),
                    downloadSeries = seriesDownloadFor(series.toDownloadInfo()),
                                    ),
                                    onOpen = { onOpenSeries(series.id) },
                                    openFlight = if (openTransition != null && flight) {
                                        { from, corner ->
                                            if (openTransition.isBusy) {
                                                onOpenSeries(series.id)
                                            } else {
                                                openTransition.open(
                                                    OpenRequest(
                                                        originKey,
                                                        liveBounds.current(),
                                                        urls.seriesCover(series.id),
                                                        cardCorner,
                                                        { onOpenSeries(series.id) },
                                                        series.displayName(),
                                                    )
                                                        .also {
                                                            it.startRect = from
                                                            it.startCorner = corner
                                                        },
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                ),
                            )
                        }
                    },
                ) {
                    if (openTransition != null && flight) {
                        openTransition.open(
                            OpenRequest(
                                originKey,
                                liveBounds.current(),
                                urls.seriesCover(series.id),
                                cardCorner,
                                { onOpenSeries(series.id) },
                                series.displayName(),
                            ),
                        )
                    } else {
                        onOpenSeries(series.id)
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha() }) {
            CoverImage(
                url = urls.seriesCover(series.id),
                contentDescription = series.displayName(),
                shape = MaterialTheme.shapes.extraLarge,
                aspectRatio = null,
                modifier = Modifier.fillMaxSize(),
            )
            }
            // The title, the reading progress and the badge are not part of the cover that is lifted: they fade on
            // their own as it lifts and ease back once it has landed, instead of blinking with the cover.
            BadgeLayer(lifted) {
            val drawInfo = carouselItemDrawInfo
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    // A card squeezed at the edge of the carousel cuts its title in half; it fades out as the card
                    // narrows.
                    .graphicsLayer {
                        val fullness = if (drawInfo.maxSize > 0f) drawInfo.size / drawInfo.maxSize else 1f
                        alpha = ((fullness - 0.7f) / 0.25f).coerceIn(0f, 1f)
                    }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                    .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
            ) {
                Text(
                    text = series.displayName(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (series.pages > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { series.pagesRead.toFloat() / series.pages },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        drawStopIndicator = {},
                    )
                }
            }
            DownloadBadgeIcon(
                if (LocalAppSettings.current.downloadBadges) seriesDownloadFor.badge(series.toDownloadInfo()) else DownloadBadge.None,
            )
            }
        }
        }
    }
}

@Composable
private fun UpdatedRail(
    items: List<GroupedSeriesDto>,
    urls: ServerUrls,
    onOpenSeries: (Int) -> Unit,
    onMarkSeries: (Int, Boolean) -> Unit,
    wantToReadFor: (Int) -> WantToReadControl,
    seriesDownloadFor: SeriesDownloads,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, it -> "${it.seriesId}-${it.chapterId}-${it.volumeId}" }) { index, item ->
            SeriesCard(
                title = item.seriesName,
                subtitle = pluralStringResource(R.plurals.home_new_chapters, item.count, item.count),
                coverUrl = urls.seriesCover(item.seriesId),
                onClick = { onOpenSeries(item.seriesId) },
                opensSeries = true,
                openKey = "home-updated-${item.seriesId}",
                downloadBadge = seriesDownloadFor.badge(SeriesDownloadInfo(item.seriesId, item.seriesName, item.libraryId, item.format, 0)),
                // This rail does not know the read state, so both actions are offered.
                actions = ReadActions(
                    isRead = false,
                    isUnread = false,
                    onMarkRead = { onMarkSeries(item.seriesId, true) },
                    onMarkUnread = { onMarkSeries(item.seriesId, false) },
                    wantToRead = wantToReadFor(item.seriesId),
                    downloadSeries = seriesDownloadFor(SeriesDownloadInfo(item.seriesId, item.seriesName, item.libraryId, item.format, 0)),
                ),
                modifier = Modifier.animateItem().width(128.dp).let { if (index < 6) it.reveal(index) else it },
            )
        }
    }
}

@Composable
private fun AddedRail(
    items: List<SeriesDto>,
    urls: ServerUrls,
    onOpenSeries: (Int) -> Unit,
    onMarkSeries: (Int, Boolean) -> Unit,
    wantToReadFor: (Int) -> WantToReadControl,
    seriesDownloadFor: SeriesDownloads,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, series -> series.id }) { index, series ->
            SeriesCard(
                title = series.displayName(),
                subtitle = series.libraryName,
                coverUrl = urls.seriesCover(series.id),
                progress = series.progress(),
                onClick = { onOpenSeries(series.id) },
                opensSeries = true,
                openKey = "home-added-${series.id}",
                downloadBadge = seriesDownloadFor.badge(series.toDownloadInfo()),
                actions = readActions(
                    progress = series.progress(),
                    onMarkRead = { onMarkSeries(series.id, true) },
                    onMarkUnread = { onMarkSeries(series.id, false) },
                    wantToRead = wantToReadFor(series.id),
                    downloadSeries = seriesDownloadFor(series.toDownloadInfo()),
                ),
                modifier = Modifier.animateItem().width(128.dp).let { if (index < 6) it.reveal(index) else it },
            )
        }
    }
}
