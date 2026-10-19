package com.romanianguy.klover.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.repo.LibrarySort
import com.romanianguy.klover.ui.common.ChoiceButtonRow
import com.romanianguy.klover.ui.common.ErrorState
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.SeriesCard
import com.romanianguy.klover.ui.common.displayName
import com.romanianguy.klover.ui.common.progress
import com.romanianguy.klover.ui.common.readActions
import com.romanianguy.klover.ui.common.rememberSeriesDownloadControls
import com.romanianguy.klover.ui.common.rememberWantToReadControls
import com.romanianguy.klover.ui.common.reveal
import com.romanianguy.klover.ui.common.toDownloadInfo
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.profiles.ProfileButton
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onOpenSeries: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // show the user's Want to read shelf instead of the whole library
    wantToRead: Boolean = false,
    viewModel: LibraryViewModel = koinViewModel(key = if (wantToRead) "want-to-read" else "library") {
        parametersOf(wantToRead)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val urls = koinInject<ServerUrls>()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val wantToReadFor = rememberWantToReadControls { id, added -> viewModel.onWantToReadChanged(id, added) }
    val seriesDownloadFor = rememberSeriesDownloadControls()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    if (showFilters) {
        FilterSheet(
            filters = state.filters,
            genres = state.genres,
            onDismiss = { showFilters = false },
            onToggleRef = viewModel::toggleRef,
            onToggleStatus = viewModel::toggleStatus,
            onUnreadOnly = viewModel::setUnreadOnly,
            onMinRating = viewModel::setMinRating,
            onClear = viewModel::clearFilters,
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (wantToRead) R.string.nav_want_to_read else R.string.library_title)) },
                actions = {
                    SortMenu(state.sort, viewModel::selectSort)
                    ProfileButton()
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SearchSuggestions(
                suggestions = state.suggestions.filterNot { it in state.filters.refs },
                onPick = viewModel::applySuggestion,
            )
            FilterBar(
                filters = state.filters,
                genres = state.genres,
                onOpen = { showFilters = true },
                onToggleRef = viewModel::toggleRef,
                onToggleStatus = viewModel::toggleStatus,
                onUnreadOnly = viewModel::setUnreadOnly,
                onMinRating = viewModel::setMinRating,
            )
            (state.libraries as? Loadable.Ready)?.value?.takeIf { it.size > 1 }?.let { libraries ->
                // null stands for "All libraries".
                ChoiceButtonRow(
                    options = listOf<Int?>(null) + libraries.map { it.id },
                    selected = state.libraryId,
                    onSelect = viewModel::selectLibrary,
                    key = { it ?: -1 },
                ) { id ->
                    Text(
                        if (id == null) stringResource(R.string.library_all)
                        else libraries.firstOrNull { it.id == id }?.name.orEmpty(),
                    )
                }
            }

            val libraryIds = (state.libraries as? Loadable.Ready)?.value?.map { it.id }.orEmpty()
            val body = Body(
                kind = when {
                    state.loadingFirstPage -> BodyKind.Loading
                    state.error != null && state.items.isEmpty() -> BodyKind.Error
                    state.items.isEmpty() -> BodyKind.Empty
                    else -> BodyKind.Grid
                },
                libraryId = state.libraryId,
                // Position of the chosen button in the row (0 = All): decides which way the content slides.
                position = state.libraryId?.let { id -> libraryIds.indexOf(id) + 1 } ?: 0,
                items = state.items,
                loadingMore = state.loadingMore,
            )
            val reducedMotion = LocalAppSettings.current.motionStyle == MotionStyle.Reduced
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

            // Choosing a category slides the old shelf away towards the button's side and the new one in behind it.
            // The snapshot (not the live state) is what each side draws, so the leaving shelf keeps its own books.
            AnimatedContent(
                targetState = body,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it.kind to it.libraryId },
                transitionSpec = {
                    val direction = when {
                        reducedMotion -> 0
                        targetState.position > initialState.position -> 1
                        targetState.position < initialState.position -> -1
                        else -> 0
                    }
                    (slideInHorizontally(spatial) { it / 5 * direction } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 5 * direction } + fadeOut(effects)) using
                        SizeTransform(clip = false)
                },
                label = "library-body",
            ) { shown ->
                Box(Modifier.fillMaxSize()) {
                    when (shown.kind) {
                        BodyKind.Loading -> LoadingState()
                        BodyKind.Error -> ErrorState(stringResource(R.string.error_generic), onRetry = viewModel::retry)
                        BodyKind.Empty -> {
                            // Nothing found is not the same as nothing there, and neither is "offline".
                            val searching = !state.filters.isEmpty || state.query.isNotBlank()
                            ErrorState(
                                message = when {
                                    !state.filters.isEmpty -> stringResource(R.string.filters_empty)
                                    state.query.isNotBlank() -> stringResource(
                                        R.string.library_no_matches,
                                        state.query.trim(),
                                    )
                                    wantToRead -> stringResource(R.string.want_to_read_empty)
                                    else -> stringResource(R.string.library_empty)
                                },
                                onRetry = null,
                                icon = when {
                                    searching -> Icons.Outlined.SearchOff
                                    wantToRead -> Icons.Outlined.BookmarkBorder
                                    else -> Icons.AutoMirrored.Outlined.LibraryBooks
                                },
                            )
                        }

                        BodyKind.Grid -> {
                            val gridState = rememberLazyGridState()
                            // Ask for the next page once the user is within a few rows of the end.
                            LaunchedEffect(gridState, shown.items.size) {
                                snapshotFlow {
                                    val info = gridState.layoutInfo
                                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 12
                                }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
                            }
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = LocalAppSettings.current.coverSize.minWidthDp.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 24.dp + LocalFloatingBarInset.current,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(LocalAppSettings.current.gridSpacing.dp.dp),
                                verticalArrangement = Arrangement.spacedBy(LocalAppSettings.current.gridSpacing.dp.dp + 4.dp),
                            ) {
                                itemsIndexed(shown.items, key = { _, series -> series.id }) { index, series ->
                                    SeriesCard(
                                        // A series that leaves (off the shelf, filtered out) or arrives lets the
                                        // others glide.
                                        modifier = Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                                            .let { if (index < 12) it.reveal(index) else it },
                                        title = series.displayName(),
                                        subtitle = series.libraryName,
                                        coverUrl = urls.seriesCover(series.id),
                                        progress = series.progress(),
                                        onClick = { onOpenSeries(series.id) },
                                        opensSeries = true,
                                        openKey = "library-${series.id}",
                                        downloadBadge = seriesDownloadFor.badge(series.toDownloadInfo()),
                                        actions = readActions(
                                            progress = series.progress(),
                                            onMarkRead = { viewModel.markSeries(series.id, true) },
                                            onMarkUnread = { viewModel.markSeries(series.id, false) },
                                            wantToRead = wantToReadFor(series.id),
                                            downloadSeries = seriesDownloadFor(series.toDownloadInfo()),
                                        ),
                                    )
                                }
                                if (shown.loadingMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { LoadingIndicator() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class BodyKind { Loading, Error, Empty, Grid }

private data class Body(
    val kind: BodyKind,
    val libraryId: Int?,
    val position: Int,
    val items: List<SeriesDto>,
    val loadingMore: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.library_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SortMenu(selected: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.library_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(sort.labelRes()),
                            color = if (sort == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Name -> R.string.sort_name
    LibrarySort.RecentlyAdded -> R.string.sort_recently_added
    LibrarySort.RecentlyUpdated -> R.string.sort_recently_updated
    LibrarySort.ReleaseYear -> R.string.sort_release_year
}
