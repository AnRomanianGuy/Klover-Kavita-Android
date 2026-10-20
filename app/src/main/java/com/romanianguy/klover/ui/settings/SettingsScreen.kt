package com.romanianguy.klover.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.BuildConfig
import com.romanianguy.klover.R
import com.romanianguy.klover.data.prefs.Accent
import com.romanianguy.klover.data.prefs.AppFont
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.BookFont
import com.romanianguy.klover.data.prefs.BookMargins
import com.romanianguy.klover.data.prefs.ContrastLevel
import com.romanianguy.klover.data.prefs.CornerStyle
import com.romanianguy.klover.data.prefs.CoverSize
import com.romanianguy.klover.data.prefs.DoubleTapZoom
import com.romanianguy.klover.data.prefs.GridSpacing
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.prefs.NavLabels
import com.romanianguy.klover.data.prefs.PageMode
import com.romanianguy.klover.data.prefs.PageTurn
import com.romanianguy.klover.data.prefs.ParagraphSpacing
import com.romanianguy.klover.data.prefs.PrefKeys
import com.romanianguy.klover.data.prefs.ReaderBackground
import com.romanianguy.klover.data.prefs.ReaderOrientation
import com.romanianguy.klover.data.prefs.ReadingDirection
import com.romanianguy.klover.data.prefs.ReadingStyle
import com.romanianguy.klover.data.prefs.StartTab
import com.romanianguy.klover.data.prefs.TapZoneSize
import com.romanianguy.klover.data.prefs.TextScale
import com.romanianguy.klover.data.prefs.ThemeMode
import com.romanianguy.klover.data.repo.LibrarySort
import com.romanianguy.klover.ui.about.AboutTab
import com.romanianguy.klover.ui.common.ConnectedChoiceGroup
import com.romanianguy.klover.ui.common.reveal
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.profiles.ProfileButton
import com.romanianguy.klover.ui.profiles.ProfileSettingsSection
import com.romanianguy.klover.ui.reader.book.BookThemePicker
import com.romanianguy.klover.ui.reader.book.composeFamily
import com.romanianguy.klover.ui.reader.book.palette
import com.romanianguy.klover.ui.theme.accentColorScheme
import com.romanianguy.klover.ui.theme.hueSeed
import com.romanianguy.klover.ui.theme.isAppDark
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate

private val TabTitles = listOf(
    R.string.settings_appearance,
    R.string.settings_tab_reader,
    R.string.settings_account,
    R.string.about_title,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState { TabTitles.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                actions = { ProfileButton() },
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
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                TabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        // One line, a little smaller: "Appearance" would otherwise break in the middle of the word on
                        // a narrow screen.
                        text = {
                            Text(
                                stringResource(title),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            // Swiping between the tabs is the same gesture as the pager, so both stay in sync.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp + LocalFloatingBarInset.current),
                    ) {
                        when (page) {
                            0 -> AppearanceTab(state.settings, viewModel)
                            1 -> ReaderTab(state.settings, viewModel)
                            2 -> AccountTab(state, viewModel)
                            else -> AboutTab()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceTab(settings: AppSettings, vm: SettingsViewModel) {
    SettingsGroup(R.string.settings_theme, Modifier.reveal(0)) {
        ChoiceSetting(null) {
            ConnectedChoiceGroup(ThemeMode.entries, settings.themeMode, { vm.setEnum(PrefKeys.THEME, it) }) {
                Text(stringResource(it.labelRes()))
            }
        }
        ChoiceSetting(R.string.settings_accent) {
            AccentSwatches(settings, onSelect = { vm.setEnum(PrefKeys.ACCENT, it) })
        }
        if (settings.accent == Accent.Custom) {
            HueSetting(settings.accentHue) { vm.set(PrefKeys.ACCENT_HUE, it) }
        }
        ChoiceSetting(R.string.settings_contrast) {
            ConnectedChoiceGroup(ContrastLevel.entries, settings.contrast, { vm.setEnum(PrefKeys.CONTRAST, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        SwitchSetting(
            title = R.string.settings_amoled,
            hint = R.string.settings_amoled_hint,
            checked = settings.amoled,
            onChange = { vm.set(PrefKeys.AMOLED, it) },
        )
    }

    SettingsGroup(R.string.settings_look, Modifier.reveal(1)) {
        ChoiceSetting(R.string.settings_corners) {
            ConnectedChoiceGroup(CornerStyle.entries, settings.cornerStyle, { vm.setEnum(PrefKeys.CORNER_STYLE, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        ChoiceSetting(R.string.settings_font) {
            ConnectedChoiceGroup(AppFont.entries, settings.appFont, { vm.setEnum(PrefKeys.APP_FONT, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        ChoiceSetting(R.string.settings_text_size) {
            ConnectedChoiceGroup(TextScale.entries, settings.textScale, { vm.setEnum(PrefKeys.TEXT_SCALE, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        SwitchSetting(
            title = R.string.settings_seasonal,
            hint = R.string.settings_seasonal_hint,
            checked = settings.seasonalEvents,
            onChange = { vm.set(PrefKeys.SEASONAL_EVENTS, it) },
        )
        ChoiceSetting(R.string.settings_nav_labels) {
            ConnectedChoiceGroup(NavLabels.entries, settings.navLabels, { vm.setEnum(PrefKeys.NAV_LABELS, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
    }

    SettingsGroup(R.string.settings_library_section, Modifier.reveal(2)) {
        ChoiceSetting(R.string.settings_cover_size) {
            ConnectedChoiceGroup(CoverSize.entries, settings.coverSize, { vm.setEnum(PrefKeys.COVER_SIZE, it) }) {
                Text(stringResource(it.labelRes()))
            }
        }
        SwitchSetting(
            title = R.string.settings_show_progress,
            hint = R.string.settings_show_progress_hint,
            checked = settings.showProgress,
            onChange = { vm.set(PrefKeys.SHOW_PROGRESS, it) },
        )
        ChoiceSetting(R.string.settings_grid_spacing) {
            ConnectedChoiceGroup(GridSpacing.entries, settings.gridSpacing, { vm.setEnum(PrefKeys.GRID_SPACING, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        SwitchSetting(
            title = R.string.settings_text_covers,
            hint = R.string.settings_text_covers_hint,
            checked = settings.textCoverPlaceholder,
            onChange = { vm.set(PrefKeys.TEXT_COVER_PLACEHOLDER, it) },
        )
        SwitchSetting(
            title = R.string.settings_subtitles,
            hint = R.string.settings_subtitles_hint,
            checked = settings.showSubtitles,
            onChange = { vm.set(PrefKeys.SHOW_SUBTITLES, it) },
        )
        ChoiceSetting(R.string.settings_sort) {
            ConnectedChoiceGroup(LibrarySort.entries, settings.librarySort, { vm.setEnum(PrefKeys.LIBRARY_SORT, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        SwitchSetting(
            title = R.string.settings_download_badges,
            hint = R.string.settings_download_badges_hint,
            checked = settings.downloadBadges,
            onChange = { vm.set(PrefKeys.DOWNLOAD_BADGES, it) },
        )
        ChoiceSetting(R.string.settings_start_tab) {
            ConnectedChoiceGroup(StartTab.entries, settings.startTab, { vm.setEnum(PrefKeys.START_TAB, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
    }

    SettingsGroup(R.string.settings_home_section, Modifier.reveal(3)) {
        SwitchSetting(
            R.string.settings_home_continue,
            checked = settings.homeContinue,
            onChange = { vm.set(PrefKeys.HOME_CONTINUE, it) },
        )
        SwitchSetting(
            R.string.settings_home_updated,
            checked = settings.homeUpdated,
            onChange = { vm.set(PrefKeys.HOME_UPDATED, it) },
        )
        SwitchSetting(
            R.string.settings_home_added,
            checked = settings.homeAdded,
            onChange = { vm.set(PrefKeys.HOME_ADDED, it) },
        )
        SwitchSetting(
            R.string.settings_home_stats,
            checked = settings.homeStats,
            onChange = { vm.set(PrefKeys.HOME_STATS, it) },
        )
    }

    SettingsGroup(R.string.settings_feel_section, Modifier.reveal(4)) {
        ChoiceSetting(R.string.settings_motion) {
            ConnectedChoiceGroup(MotionStyle.entries, settings.motionStyle, { vm.setEnum(PrefKeys.MOTION, it) }) {
                Text(stringResource(it.labelRes()))
            }
        }
        SwitchSetting(
            title = R.string.settings_cover_flight,
            hint = R.string.settings_cover_flight_hint,
            checked = settings.coverFlight,
            enabled = settings.motionStyle != MotionStyle.Reduced,
            onChange = { vm.set(PrefKeys.COVER_FLIGHT, it) },
        )
        SwitchSetting(
            title = R.string.settings_peek_blur,
            hint = R.string.settings_peek_blur_hint,
            checked = settings.peekBlur,
            enabled = settings.holdToPeek,
            onChange = { vm.set(PrefKeys.PEEK_BLUR, it) },
        )
        SwitchSetting(
            title = R.string.settings_hold_to_peek,
            hint = R.string.settings_hold_to_peek_hint,
            checked = settings.holdToPeek,
            onChange = { vm.set(PrefKeys.HOLD_TO_PEEK, it) },
        )
        SwitchSetting(
            title = R.string.settings_haptics,
            checked = settings.haptics,
            onChange = { vm.set(PrefKeys.HAPTICS, it) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(settings: AppSettings, onSelect: (Accent) -> Unit) {
    val dark = isAppDark(settings)
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Accent.entries.forEach { accent ->
            val scheme = remember(accent, dark, if (accent == Accent.Custom) settings.accentHue else 0f) {
                when (val seed = if (accent == Accent.Custom) hueSeed(settings.accentHue) else accent.seed) {
                    null -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    else -> accentColorScheme(seed, dark)
                }
            }
            val selected = settings.accent == accent
            val grow by animateFloatAsState(
                if (selected) 1f else 0f,
                MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "swatch",
            )
            val ring by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "swatchRing",
            )
            val description = stringResource(accent.labelRes())
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer { scaleX = 1f + 0.08f * grow; scaleY = scaleX }
                    .border(BorderStroke(3.dp, ring), CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (accent == Accent.Custom && settings.accent != Accent.Custom) {
                            // Not chosen yet: a rainbow, to say "pick any colour".
                            Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                            )
                        } else if (accent == Accent.Wallpaper) {
                            Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary))
                        } else {
                            Brush.linearGradient(listOf(scheme.primary, scheme.primaryContainer))
                        },
                    )
                    .toggleable(selected, role = Role.RadioButton, onValueChange = { onSelect(accent) }),
                contentAlignment = Alignment.Center,
            ) {
                if (accent == Accent.Wallpaper && !selected) {
                    Icon(
                        Icons.Filled.Wallpaper,
                        contentDescription = description,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = description,
                        tint = scheme.onPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { scaleX = grow; scaleY = grow },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTab(settings: AppSettings, vm: SettingsViewModel) {
    val systemLabel = stringResource(R.string.reader_brightness_system)
    SettingsGroup(R.string.settings_reader_pages, Modifier.reveal(0)) {
        ChoiceSetting(R.string.reader_style) {
            ConnectedChoiceGroup(
                ReadingStyle.entries,
                settings.readingStyle,
                { vm.setEnum(PrefKeys.READING_STYLE, it) },
            ) {
                Text(
                    stringResource(
                        if (it == ReadingStyle.Pages) R.string.reader_style_pages else R.string.reader_style_webtoon,
                    ),
                )
            }
        }
        SwitchSetting(
            title = R.string.reader_auto_direction,
            hint = R.string.reader_auto_direction_hint,
            checked = settings.autoDirection,
            onChange = { vm.set(PrefKeys.AUTO_DIRECTION, it) },
        )
        ChoiceSetting(R.string.reader_direction) {
            ConnectedChoiceGroup(
                ReadingDirection.entries,
                settings.readingDirection,
                { vm.setEnum(PrefKeys.DIRECTION, it) },
            ) { Text(stringResource(if (it == ReadingDirection.LeftToRight) R.string.reader_ltr else R.string.reader_rtl)) }
        }
        ChoiceSetting(R.string.reader_page_mode) {
            ConnectedChoiceGroup(
                PageMode.entries,
                settings.pageMode,
                { vm.setEnum(PrefKeys.PAGE_MODE, it) },
            ) {
                Text(
                    stringResource(
                        when (it) {
                            PageMode.Auto -> R.string.reader_mode_auto
                            PageMode.Single -> R.string.reader_mode_single
                            PageMode.Double -> R.string.reader_mode_double
                        },
                    ),
                )
            }
        }
        SwitchSetting(
            title = R.string.reader_cover_alone,
            hint = R.string.reader_cover_alone_hint,
            checked = settings.coverAlone,
            onChange = { vm.set(PrefKeys.COVER_ALONE, it) },
        )
        ChoiceSetting(R.string.reader_background) {
            ConnectedChoiceGroup(
                ReaderBackground.entries,
                settings.readerBackground,
                { vm.setEnum(PrefKeys.READER_BACKGROUND, it) },
            ) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        ChoiceSetting(R.string.reader_page_turn) {
            ConnectedChoiceGroup(PageTurn.entries, settings.pageTurn, { vm.setEnum(PrefKeys.PAGE_TURN, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
    }

    SettingsGroup(R.string.settings_foldables, Modifier.reveal(1)) {
        SwitchSetting(
            title = R.string.settings_tabletop,
            hint = R.string.settings_tabletop_hint,
            checked = settings.tabletopMode,
            onChange = { vm.set(PrefKeys.TABLETOP_MODE, it) },
        )
    }

    SettingsGroup(R.string.settings_reader_behavior, Modifier.reveal(2)) {
        SwitchSetting(
            R.string.reader_tap_zones,
            checked = settings.tapZones,
            onChange = { vm.set(PrefKeys.TAP_ZONES, it) },
        )
        if (settings.tapZones) {
            ChoiceSetting(R.string.reader_tap_size) {
                ConnectedChoiceGroup(
                    TapZoneSize.entries,
                    settings.tapZoneSize,
                    { vm.setEnum(PrefKeys.TAP_ZONE_SIZE, it) },
                ) {
                    Text(stringResource(it.labelRes()), maxLines = 1)
                }
            }
        }
        SwitchSetting(
            title = R.string.reader_invert_tap,
            checked = settings.invertTapZones,
            enabled = settings.tapZones,
            onChange = { vm.set(PrefKeys.INVERT_TAP_ZONES, it) },
        )
        SwitchSetting(
            R.string.reader_volume_keys,
            checked = settings.volumeKeys,
            onChange = { vm.set(PrefKeys.VOLUME_KEYS, it) },
        )
        SwitchSetting(
            R.string.reader_show_page_number,
            checked = settings.showPageNumber,
            onChange = { vm.set(PrefKeys.SHOW_PAGE_NUMBER, it) },
        )
        SwitchSetting(
            R.string.reader_keep_awake,
            checked = settings.keepScreenOn,
            onChange = { vm.set(PrefKeys.KEEP_SCREEN_ON, it) },
        )
        SwitchSetting(
            title = R.string.settings_update_check,
            hint = R.string.settings_update_check_hint,
            checked = settings.updateCheck,
            onChange = { vm.set(PrefKeys.UPDATE_CHECK, it) },
        )
        SwitchSetting(
            title = R.string.settings_chapter_lookup,
            hint = R.string.settings_chapter_lookup_hint,
            checked = settings.chapterLookup,
            onChange = { vm.set(PrefKeys.CHAPTER_LOOKUP, it) },
        )
        SwitchSetting(
            R.string.reader_immersive,
            checked = settings.immersive,
            onChange = { vm.set(PrefKeys.IMMERSIVE, it) },
        )
        SliderSetting(
            R.string.reader_brightness, settings.readerBrightness, 0f..1f, PrefKeys.READER_BRIGHTNESS, vm,
        ) { if (it < 0.05f) systemLabel else "${(it * 100).toInt()}%" }
        ChoiceSetting(R.string.reader_orientation) {
            ConnectedChoiceGroup(
                ReaderOrientation.entries,
                settings.readerOrientation,
                { vm.setEnum(PrefKeys.READER_ORIENTATION, it) },
            ) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        ChoiceSetting(R.string.reader_preload) {
            ConnectedChoiceGroup(
                listOf(1, 2, 3, 4),
                settings.preloadScreens,
                { vm.set(PrefKeys.PRELOAD_SCREENS, it) },
            ) { Text("$it") }
        }
        ChoiceSetting(R.string.reader_double_tap) {
            ConnectedChoiceGroup(
                DoubleTapZoom.entries,
                settings.doubleTapZoom,
                { vm.setEnum(PrefKeys.DOUBLE_TAP_ZOOM, it) },
            ) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
    }

    SettingsGroup(R.string.settings_read_aloud, Modifier.reveal(3)) {
        SliderSetting(
            R.string.settings_read_aloud_speed,
            settings.ttsSpeed,
            0.6f..2f,
            PrefKeys.TTS_SPEED,
            vm,
        ) { "%.2fx".format(it) }
        SwitchSetting(
            title = R.string.settings_read_aloud_follow,
            hint = R.string.settings_read_aloud_follow_hint,
            checked = settings.ttsFollow,
            onChange = { vm.set(PrefKeys.TTS_FOLLOW, it) },
        )
        SwitchSetting(
            R.string.settings_read_aloud_next,
            checked = settings.ttsAutoNext,
            onChange = { vm.set(PrefKeys.TTS_AUTO_NEXT, it) },
        )
    }

    SettingsGroup(R.string.settings_reader_books, Modifier.reveal(4)) {
        BookPreview(settings)
        SliderSetting(
            R.string.book_text_size,
            settings.bookFontScale,
            0.7f..2.2f,
            PrefKeys.FONT_SCALE,
            vm,
        ) { "${(it * 100).toInt()}%" }
        SliderSetting(
            R.string.book_line_spacing,
            settings.bookLineSpacing,
            1.2f..2.2f,
            PrefKeys.BOOK_LINE_SPACING,
            vm,
        ) { "%.1f".format(it) }
        ChoiceSetting(R.string.book_font) {
            ConnectedChoiceGroup(BookFont.entries, settings.bookFont, { vm.setEnum(PrefKeys.BOOK_FONT, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        ChoiceSetting(R.string.book_margins) {
            ConnectedChoiceGroup(BookMargins.entries, settings.bookMargins, { vm.setEnum(PrefKeys.BOOK_MARGINS, it) }) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        SwitchSetting(
            R.string.book_justify,
            checked = settings.bookJustify,
            onChange = { vm.set(PrefKeys.BOOK_JUSTIFY, it) },
        )
        SwitchSetting(
            R.string.book_hyphenate,
            checked = settings.bookHyphenate,
            onChange = { vm.set(PrefKeys.BOOK_HYPHENATE, it) },
        )
        SwitchSetting(
            R.string.book_styles,
            hint = R.string.book_styles_hint,
            checked = settings.bookStyles,
            onChange = { vm.set(PrefKeys.BOOK_STYLES, it) },
        )
        SwitchSetting(
            R.string.book_indent,
            checked = settings.bookIndent,
            onChange = { vm.set(PrefKeys.BOOK_INDENT, it) },
        )
        ChoiceSetting(R.string.book_paragraphs) {
            ConnectedChoiceGroup(
                ParagraphSpacing.entries,
                settings.bookParagraphs,
                { vm.setEnum(PrefKeys.BOOK_PARAGRAPHS, it) },
            ) {
                Text(stringResource(it.labelRes()), maxLines = 1)
            }
        }
        ChoiceSetting(R.string.book_theme) {
            BookThemePicker(settings.bookTheme, onSelect = { vm.setEnum(PrefKeys.BOOK_THEME, it) })
        }
    }
}

@Composable
private fun HueSetting(hue: Float, onChange: (Float) -> Unit) {
    var draft by remember(hue) { mutableFloatStateOf(hue) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.settings_accent_hue), style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 0.62f, 0.78f) })),
            )
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { onChange(draft) },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    thumbColor = Color.hsv(draft, 0.62f, 0.78f),
                ),
            )
        }
    }
}

// live preview of the book options, so a change is visible without opening a book
@Composable
private fun BookPreview(settings: AppSettings) {
    val palette = settings.bookTheme.palette()
    val base = 16.sp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = palette.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = stringResource(R.string.book_preview),
            modifier = Modifier.padding(horizontal = (settings.bookMargins.sidePadDp).dp, vertical = 16.dp),
            color = palette.text,
            fontFamily = settings.bookFont.composeFamily(),
            fontSize = base * settings.bookFontScale,
            lineHeight = base * settings.bookFontScale * settings.bookLineSpacing,
            textAlign = if (settings.bookJustify) TextAlign.Justify else TextAlign.Start,
        )
    }
}

@Composable
private fun AccountTab(state: SettingsUiState, vm: SettingsViewModel) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    val backupResult by vm.backupResult.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    SettingsGroup(R.string.settings_account, Modifier.reveal(0)) {
        ProfileSettingsSection()
        state.session?.let { session ->
            ListItem(
                leadingContent = { Icon(Icons.Filled.Dns, contentDescription = null) },
                overlineContent = { Text(stringResource(R.string.settings_server)) },
                headlineContent = { Text(session.serverUrl) },
                colors = rowColors(),
            )
            if (session.username.isNotBlank()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                    overlineContent = { Text(stringResource(R.string.settings_signed_in_as)) },
                    headlineContent = { Text(session.username) },
                    colors = rowColors(),
                )
            }
        }
        ListItem(
            modifier = Modifier.clickable { confirmSignOut = true },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            headlineContent = { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) },
            colors = rowColors(),
        )

        SwitchSetting(
            title = R.string.settings_queue_marks,
            hint = R.string.settings_queue_marks_hint,
            checked = state.settings.queueMarks,
            onChange = { vm.set(PrefKeys.QUEUE_MARKS, it) },
        )
        SwitchSetting(
            title = R.string.settings_garden,
            hint = R.string.settings_garden_hint,
            checked = state.settings.garden,
            onChange = { vm.set(PrefKeys.GARDEN, it) },
        )
        if (state.settings.garden) {
            ChoiceSetting(R.string.settings_weekly_goal) {
                ConnectedChoiceGroup(
                    listOf(0, 60, 120, 300),
                    state.settings.weeklyGoalMinutes,
                    { vm.set(PrefKeys.WEEKLY_GOAL, it) },
                ) {
                    Text(if (it == 0) stringResource(R.string.settings_goal_off) else stringResource(R.string.settings_goal_minutes, it))
                }
            }
        }
        SwitchSetting(
            title = R.string.settings_incognito,
            hint = R.string.settings_incognito_hint,
            checked = state.settings.incognito,
            onChange = { vm.set(PrefKeys.INCOGNITO, it) },
        )
    }

    SettingsGroup(R.string.settings_data_group, Modifier.reveal(1)) {
        SwitchSetting(
            title = R.string.settings_data_saver,
            hint = R.string.settings_data_saver_hint,
            checked = state.settings.dataSaver,
            onChange = { vm.set(PrefKeys.DATA_SAVER, it) },
        )
    }

    SettingsGroup(R.string.settings_downloads, Modifier.reveal(2)) {
        SwitchSetting(
            title = R.string.settings_download_wifi,
            hint = R.string.settings_download_wifi_hint,
            checked = state.settings.downloadWifiOnly,
            onChange = { vm.set(PrefKeys.DOWNLOAD_WIFI_ONLY, it) },
        )
        ChoiceSetting(R.string.settings_download_parallel) {
            ConnectedChoiceGroup(
                listOf(1, 2, 3),
                state.settings.downloadParallel,
                { vm.set(PrefKeys.DOWNLOAD_PARALLEL, it) },
            ) {
                Text("$it")
            }
        }
        SwitchSetting(
            title = R.string.settings_auto_delete,
            hint = R.string.settings_auto_delete_hint,
            checked = state.settings.autoDeleteRead,
            onChange = { vm.set(PrefKeys.AUTO_DELETE_READ, it) },
        )

        SwitchSetting(
            title = R.string.settings_smart_downloads,
            hint = R.string.settings_smart_downloads_hint,
            checked = state.settings.smartDownloads,
            onChange = { vm.set(PrefKeys.SMART_DOWNLOADS, it) },
        )
        androidx.compose.animation.AnimatedVisibility(visible = state.settings.smartDownloads) {
            Column {
                ChoiceSetting(R.string.settings_smart_ahead) {
                    ConnectedChoiceGroup(
                        listOf(1, 2, 3, 5),
                        state.settings.smartAhead,
                        { vm.set(PrefKeys.SMART_AHEAD, it) },
                    ) {
                        Text("$it")
                    }
                }
                SwitchSetting(
                    title = R.string.settings_smart_cleanup,
                    hint = R.string.settings_smart_cleanup_hint,
                    checked = state.settings.smartCleanup,
                    onChange = { vm.set(PrefKeys.SMART_CLEANUP, it) },
                )
            }
        }
    }

    SettingsGroup(R.string.settings_storage, Modifier.reveal(2)) {
        val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
        val context = LocalContext.current
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_storage_cache)) },
            supportingContent = { Text(Formatter.formatShortFileSize(context, cacheBytes)) },
            trailingContent = {
                OutlinedButton(onClick = vm::clearCache, enabled = cacheBytes > 0) { Text(stringResource(R.string.settings_storage_clear)) }
            },
            colors = rowColors(),
        )
    }

    SettingsGroup(R.string.settings_backup, Modifier.reveal(3)) {
        ListItem(
            modifier = Modifier.clickable { exportLauncher.launch("klover-backup-${LocalDate.now()}.json") },
            leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_export_hint)) },
            colors = rowColors(),
        )
        ListItem(
            modifier = Modifier.clickable { confirmImport = true },
            leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.settings_backup_import)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_import_hint)) },
            colors = rowColors(),
        )
    }

    SettingsGroup(R.string.settings_about, Modifier.reveal(4)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            colors = rowColors(),
        )
        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.settings_reset)) }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.settings_backup_import_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_body)) },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; importLauncher.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.settings_backup_import_choose))
                }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    backupResult?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissBackupResult,
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = vm::dismissBackupResult) { Text(stringResource(R.string.backup_done)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetAll() }) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SettingsGroup(res: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(res),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun ChoiceSetting(title: Int?, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        content()
    }
}

@Composable
private fun SwitchSetting(
    title: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    hint: Int? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = hint?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = rowColors(),
    )
}

// writes only when the finger lifts, so dragging stays smooth and does not hammer the disk
@Composable
private fun SliderSetting(
    title: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    key: Preferences.Key<Float>,
    vm: SettingsViewModel,
    format: (Float) -> String,
) {
    var dragging by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "${stringResource(title)} - ${format(dragging)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { vm.set(key, dragging) },
            valueRange = range,
        )
    }
}

private fun LibrarySort.labelRes() = when (this) {
    LibrarySort.Name -> R.string.sort_name
    LibrarySort.RecentlyAdded -> R.string.sort_added
    LibrarySort.RecentlyUpdated -> R.string.sort_updated
    LibrarySort.ReleaseYear -> R.string.sort_year
}

private fun TapZoneSize.labelRes() = when (this) {
    TapZoneSize.Narrow -> R.string.tap_size_narrow
    TapZoneSize.Normal -> R.string.tap_size_normal
    TapZoneSize.Wide -> R.string.tap_size_wide
}

private fun ContrastLevel.labelRes() = when (this) {
    ContrastLevel.Standard -> R.string.contrast_standard
    ContrastLevel.Medium -> R.string.contrast_medium
    ContrastLevel.High -> R.string.contrast_high
}

private fun CornerStyle.labelRes() = when (this) {
    CornerStyle.Sharp -> R.string.corners_sharp
    CornerStyle.Standard -> R.string.corners_standard
    CornerStyle.Round -> R.string.corners_round
}

private fun AppFont.labelRes() = when (this) {
    AppFont.Default -> R.string.font_default
    AppFont.Serif -> R.string.font_serif
    AppFont.Mono -> R.string.font_mono
}

private fun TextScale.labelRes() = when (this) {
    TextScale.Small -> R.string.text_small
    TextScale.Default -> R.string.text_default
    TextScale.Large -> R.string.text_large
    TextScale.Huge -> R.string.text_huge
}

private fun NavLabels.labelRes() = when (this) {
    NavLabels.Selected -> R.string.nav_labels_selected
    NavLabels.Always -> R.string.nav_labels_always
    NavLabels.Never -> R.string.nav_labels_never
}

private fun GridSpacing.labelRes() = when (this) {
    GridSpacing.Compact -> R.string.spacing_compact
    GridSpacing.Normal -> R.string.spacing_normal
    GridSpacing.Relaxed -> R.string.spacing_relaxed
}

private fun ReaderOrientation.labelRes() = when (this) {
    ReaderOrientation.Auto -> R.string.orientation_auto
    ReaderOrientation.Portrait -> R.string.orientation_portrait
    ReaderOrientation.Landscape -> R.string.orientation_landscape
}

private fun DoubleTapZoom.labelRes() = when (this) {
    DoubleTapZoom.Gentle -> R.string.zoom_gentle
    DoubleTapZoom.Normal -> R.string.zoom_normal
    DoubleTapZoom.Close -> R.string.zoom_close
}

private fun ParagraphSpacing.labelRes() = when (this) {
    ParagraphSpacing.Book -> R.string.paragraphs_book
    ParagraphSpacing.Tight -> R.string.paragraphs_tight
    ParagraphSpacing.Normal -> R.string.paragraphs_normal
    ParagraphSpacing.Wide -> R.string.paragraphs_wide
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

private fun Accent.labelRes() = when (this) {
    Accent.Wallpaper -> R.string.accent_wallpaper
    Accent.Custom -> R.string.accent_custom
    Accent.Clover -> R.string.accent_clover
    Accent.Ocean -> R.string.accent_ocean
    Accent.Violet -> R.string.accent_violet
    Accent.Rose -> R.string.accent_rose
    Accent.Amber -> R.string.accent_amber
    Accent.Teal -> R.string.accent_teal
    Accent.Graphite -> R.string.accent_graphite
}

private fun CoverSize.labelRes() = when (this) {
    CoverSize.Small -> R.string.cover_small
    CoverSize.Medium -> R.string.cover_medium
    CoverSize.Large -> R.string.cover_large
}

private fun StartTab.labelRes() = when (this) {
    StartTab.Home -> R.string.nav_home
    StartTab.Library -> R.string.nav_library
    StartTab.WantToRead -> R.string.nav_want_to_read
}

private fun MotionStyle.labelRes() = when (this) {
    MotionStyle.Bouncy -> R.string.motion_bouncy
    MotionStyle.Calm -> R.string.motion_calm
    MotionStyle.Reduced -> R.string.motion_reduced
}

private fun ReaderBackground.labelRes() = when (this) {
    ReaderBackground.Black -> R.string.reader_bg_black
    ReaderBackground.Dark -> R.string.reader_bg_dark
    ReaderBackground.Paper -> R.string.reader_bg_paper
    ReaderBackground.Theme -> R.string.reader_bg_theme
}

private fun PageTurn.labelRes() = when (this) {
    PageTurn.Slide -> R.string.reader_turn_slide
    PageTurn.Fade -> R.string.reader_turn_fade
    PageTurn.Instant -> R.string.reader_turn_instant
    PageTurn.Flip -> R.string.reader_turn_flip
}

private fun BookFont.labelRes() = when (this) {
    BookFont.Serif -> R.string.book_font_serif
    BookFont.Sans -> R.string.book_font_sans
    BookFont.Mono -> R.string.book_font_mono
}

private fun BookMargins.labelRes() = when (this) {
    BookMargins.Narrow -> R.string.book_margins_narrow
    BookMargins.Normal -> R.string.book_margins_normal
    BookMargins.Wide -> R.string.book_margins_wide
}
