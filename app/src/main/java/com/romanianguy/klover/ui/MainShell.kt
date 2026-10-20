package com.romanianguy.klover.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.romanianguy.klover.R
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.update.UpdateChecker
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.LocalOpenTransition
import com.romanianguy.klover.ui.common.rememberBookHinge
import com.romanianguy.klover.ui.downloads.DownloadsScreen
import com.romanianguy.klover.ui.events.SeasonalDecor
import com.romanianguy.klover.ui.events.eventPerch
import com.romanianguy.klover.ui.garden.GardenScreen
import com.romanianguy.klover.ui.history.HistoryScreen
import com.romanianguy.klover.ui.home.HomeScreen
import com.romanianguy.klover.ui.library.LibraryScreen
import com.romanianguy.klover.ui.navigation.BookContentsPage
import com.romanianguy.klover.ui.navigation.DownloadsKey
import com.romanianguy.klover.ui.navigation.FloatingBarFootprint
import com.romanianguy.klover.ui.navigation.FloatingNavBar
import com.romanianguy.klover.ui.navigation.GardenKey
import com.romanianguy.klover.ui.navigation.HistoryKey
import com.romanianguy.klover.ui.navigation.HomeKey
import com.romanianguy.klover.ui.navigation.LibraryKey
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.navigation.ReaderKey
import com.romanianguy.klover.ui.navigation.SeriesKey
import com.romanianguy.klover.ui.navigation.SettingsKey
import com.romanianguy.klover.ui.navigation.StatsKey
import com.romanianguy.klover.ui.navigation.TopLevelDestination
import com.romanianguy.klover.ui.navigation.WantToReadKey
import com.romanianguy.klover.ui.navigation.rememberAppNavigator
import com.romanianguy.klover.ui.profiles.LocalProfilePicker
import com.romanianguy.klover.ui.reader.ReaderScreen
import com.romanianguy.klover.ui.series.SeriesScreen
import com.romanianguy.klover.ui.settings.SettingsScreen
import com.romanianguy.klover.ui.stats.StatsScreen
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainShell(startTab: TopLevelDestination = TopLevelDestination.Home) {
    val navigator = rememberAppNavigator(startTab)
    val activity = LocalActivity.current
    val openTransition = LocalOpenTransition.current

    // two-pane: a new selection replaces the detail pane
    fun openSeries(id: Int) {
        openTransition?.markOpened(id)
        if (navigator.top is SeriesKey) navigator.replaceTop(SeriesKey(id)) else navigator.push(SeriesKey(id))
    }

    fun goBack() {
        if (openTransition?.isBusy == true) return
        val top = navigator.top
        if (top is SeriesKey && openTransition != null && openTransition.canClose(top.seriesId)) {
            openTransition.close { navigator.pop() }
        } else if (!navigator.pop()) {
            activity?.finish()
        }
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    val launch by LaunchRequests.pending.collectAsStateWithLifecycle()
    val activeAccount by koinInject<ProfileStore>().activeKey.collectAsStateWithLifecycle()
    LaunchedEffect(launch, activeAccount) {
        val request = launch ?: return@LaunchedEffect
        if (activeAccount == null) return@LaunchedEffect
        LaunchRequests.pending.value = null
        if (request.account == activeAccount) navigator.push(ReaderKey(request.chapterId))
    }

    // once the app is up and the setting is on, ask whether a newer version is out (at most every twelve hours)
    val updateChecks = LocalAppSettings.current.updateCheck
    val updates = koinInject<UpdateChecker>()
    LaunchedEffect(updateChecks) {
        if (updateChecks) updates.checkIfDue()
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val inReader = navigator.top is ReaderKey
    val compact = !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // book posture (under 170 degrees or half open): contents on the left, the screen on the right, nothing across
    // the fold. Needs wide enough pages
    val bookHinge = rememberBookHinge()
    val bookMode = bookHinge != null && !inReader && !compact
    val openProfilePicker = LocalProfilePicker.current
    // the contents page slides in with a springy settle when folding, and out when flat
    val bookProgress by animateFloatAsState(
        targetValue = if (bookMode) 1f else 0f,
        animationSpec = if (LocalAppSettings.current.motionStyle == MotionStyle.Reduced) {
            tween(150)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 160f)
        },
        label = "book-progress",
    )
    // last measured, so the pages can slide out after the hinge is gone
    val lastHinge = remember { androidx.compose.runtime.mutableStateOf<Rect?>(null) }
    if (bookHinge != null) lastHinge.value = bookHinge
    val suiteType = when {
        inReader || compact || bookMode || bookProgress > 0.001f -> NavigationSuiteType.None
        else -> NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    }

    val density = LocalDensity.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val systemBarInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val showFloatingBar = compact && !inReader

    NavigationSuiteScaffold(
        layoutType = suiteType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = navigator.currentTab == destination
                item(
                    selected = selected,
                    onClick = { navigator.select(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        // camera cutout and rotated side bar: one padding so content clears them like the top bars do
        val horizontalSides = if (suiteType.toString().contains("Rail")) WindowInsetsSides.End else WindowInsetsSides.Horizontal
        // with a book open the contents page is left alone and the decorations go on the screen
        val screenStart = with(density) { ((bookHinge ?: lastHinge.value)?.right ?: 0f).toDp() } * bookProgress.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .then(if (inReader) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(horizontalSides))),
        ) {
            CompositionLocalProvider(
                LocalFloatingBarInset provides if (showFloatingBar) FloatingBarFootprint + systemBarInset else 0.dp,
            ) {
                val pages = bookHinge ?: lastHinge.value
                val fullLeft = with(density) { (pages?.left ?: 0f).toDp() }
                val fullGap = with(density) { (pages?.width ?: 0f).toDp() }
                val shown = bookProgress.coerceIn(0f, 1f)
                val bookLeft = fullLeft * shown
                val bookGap = fullGap * shown
                Row(Modifier.fillMaxSize()) {
                    if (shown > 0.001f) {
                        Box(Modifier.width(bookLeft).fillMaxHeight().clipToBounds()) {
                            BookContentsPage(
                                current = navigator.currentTab,
                                onSelect = navigator::select,
                                onContinue = { navigator.push(ReaderKey(it)) },
                                onSwitchProfile = openProfilePicker,
                                // keeps its full width while sliding so nothing re-flows
                                modifier = Modifier
                                    .requiredWidth(fullLeft)
                                    .graphicsLayer {
                                        translationX = -(1f - shown) * fullLeft.toPx()
                                        alpha = shown.coerceIn(0f, 1f)
                                    },
                            )
                        }
                        Spacer(Modifier.width(bookGap))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(
                    LocalProfilePicker provides if (bookMode) null else openProfilePicker,
                ) {
                NavDisplay(
                    backStack = navigator.backStack,
                    // Every tab root is in the list, so NavDisplay always intercepts Back; at the Home root we close
                    // the app.
                    onBack = { goBack() },
                    sceneStrategies = if (bookMode) emptyList() else listOf(listDetailStrategy),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<HomeKey> {
                            HomeScreen(
                                onOpenSeries = { openSeries(it) },
                                onOpenStats = { navigator.push(StatsKey) },
                                onContinue = { navigator.push(ReaderKey(it)) },
                                onOpenHistory = { navigator.push(HistoryKey) },
                                onOpenGarden = { navigator.push(GardenKey) },
                            )
                        }
                        entry<LibraryKey>(
                            metadata = ListDetailSceneStrategy.listPane(
                                detailPlaceholder = { SelectSeriesPlaceholder() },
                            ),
                        ) {
                            LibraryScreen(
                                onOpenSeries = { id -> openSeries(id) },
                            )
                        }
                        entry<SeriesKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                            SeriesScreen(
                                seriesId = key.seriesId,
                                onBack = { goBack() },
                                onRead = { chapterId -> if (chapterId > 0) navigator.push(ReaderKey(chapterId)) },
                                onReadFrom = { chapterId, page -> if (chapterId > 0) navigator.push(ReaderKey(chapterId, page)) },
                            )
                        }
                        entry<WantToReadKey>(
                            metadata = ListDetailSceneStrategy.listPane(
                                detailPlaceholder = { SelectSeriesPlaceholder() },
                            ),
                        ) {
                            LibraryScreen(
                                wantToRead = true,
                                onOpenSeries = { id -> openSeries(id) },
                            )
                        }
                        entry<DownloadsKey> {
                            DownloadsScreen(
                                onRead = { chapterId -> navigator.push(ReaderKey(chapterId)) },
                                onOpenSeries = { openSeries(it) },
                            )
                        }
                        entry<SettingsKey> { SettingsScreen() }
                        entry<StatsKey> { StatsScreen(onBack = { goBack() }, onOpenHistory = { navigator.push(HistoryKey) }) }
                        entry<GardenKey> { GardenScreen(onBack = { goBack() }) }
                        entry<HistoryKey> {
                            HistoryScreen(
                                onBack = { goBack() },
                                onOpen = { chapterId, page -> navigator.push(ReaderKey(chapterId, page)) },
                            )
                        }
                        entry<ReaderKey> { key ->
                            ReaderScreen(
                                chapterId = key.chapterId,
                                startPage = key.startPage,
                                onBack = { navigator.pop() },
                                // Moving to another chapter replaces the reader instead of stacking readers.
                                onOpenChapter = { navigator.replaceTop(ReaderKey(it)) },
                            )
                        }
                    },
                )
                }
                    }
                }
            }

            if (!inReader) {
                SeasonalDecor(
                    bottomInset = if (showFloatingBar) FloatingBarFootprint + systemBarInset else systemBarInset,
                    startX = screenStart,
                    perched = showFloatingBar,
                )
            }

            AnimatedVisibility(
                visible = showFloatingBar && !keyboardOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            ) {
                FloatingNavBar(
                    current = navigator.currentTab,
                    onSelect = navigator::select,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 16.dp)
                        .eventPerch(),
                )
            }
        }
    }
}

@Composable
private fun SelectSeriesPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.library_select_series),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
