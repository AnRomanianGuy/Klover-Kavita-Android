package com.romanianguy.klover.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.romanianguy.klover.data.repo.LibrarySort
import com.romanianguy.klover.data.session.AccountStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val Context.settingsStore by preferencesDataStore(name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

enum class ThemeMode { System, Light, Dark }

enum class Accent(val seed: Int?) {
    Wallpaper(null),
    Custom(0xFF3B8D5A.toInt()),
    Clover(0xFF3B8D5A.toInt()),
    Ocean(0xFF1E6FB8.toInt()),
    Violet(0xFF6B4EC7.toInt()),
    Rose(0xFFC2456B.toInt()),
    Amber(0xFFB8741A.toInt()),
    Teal(0xFF11807F.toInt()),
    Graphite(0xFF5F6670.toInt()),
}

enum class CornerStyle(val scale: Float) { Sharp(0.35f), Standard(1f), Round(1.5f) }

enum class AppFont { Default, Serif, Mono }

enum class TextScale(val factor: Float) { Small(0.9f), Default(1f), Large(1.15f), Huge(1.3f) }

enum class ContrastLevel(val level: Double) { Standard(0.0), Medium(0.5), High(1.0) }

enum class NavLabels { Selected, Always, Never }

enum class GridSpacing(val dp: Int) { Compact(6), Normal(12), Relaxed(20) }

enum class CoverSize(val minWidthDp: Int) { Small(100), Medium(120), Large(160) }

enum class MotionStyle { Bouncy, Calm, Reduced }

enum class StartTab { Home, Library, WantToRead }

enum class TapZoneSize(val edge: Float) { Narrow(0.2f), Normal(0.3f), Wide(0.4f) }

enum class ReadingDirection { LeftToRight, RightToLeft }

enum class ReadingStyle { Pages, Webtoon }

enum class PageMode { Auto, Single, Double }

enum class ReaderOrientation { Auto, Portrait, Landscape }

enum class DoubleTapZoom(val scale: Float) { Gentle(2f), Normal(2.5f), Close(3.5f) }

enum class ParagraphSpacing(val em: Float?) { Book(null), Tight(0.3f), Normal(0.8f), Wide(1.4f) }

enum class ReaderBackground { Black, Dark, Paper, Theme }

enum class PageTurn { Slide, Fade, Instant, Flip }

enum class BookFont { Serif, Sans, Mono }

enum class BookTheme { App, Paper, Sepia, Dark, Black }

enum class BookMargins(val sidePadDp: Int) { Narrow(16), Normal(28), Wide(48) }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: Accent = Accent.Wallpaper,
    val amoled: Boolean = false,
    val accentHue: Float = 150f,
    val contrast: ContrastLevel = ContrastLevel.Standard,
    val cornerStyle: CornerStyle = CornerStyle.Standard,
    val appFont: AppFont = AppFont.Default,
    val textScale: TextScale = TextScale.Default,
    val navLabels: NavLabels = NavLabels.Selected,
    val gridSpacing: GridSpacing = GridSpacing.Normal,
    val showSubtitles: Boolean = true,
    val coverSize: CoverSize = CoverSize.Medium,
    val showProgress: Boolean = true,
    val motionStyle: MotionStyle = MotionStyle.Bouncy,
    val haptics: Boolean = true,
    val holdToPeek: Boolean = true,
    val startTab: StartTab = StartTab.Home,
    val coverFlight: Boolean = true,
    val peekBlur: Boolean = true,
    val downloadBadges: Boolean = true,
    val homeContinue: Boolean = true,
    val homeUpdated: Boolean = true,
    val homeAdded: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.Name,

    val readingDirection: ReadingDirection = ReadingDirection.LeftToRight,
    val readingStyle: ReadingStyle = ReadingStyle.Pages,
    val autoDirection: Boolean = true,
    val pageMode: PageMode = PageMode.Auto,
    val readerBackground: ReaderBackground = ReaderBackground.Black,
    val coverAlone: Boolean = true,
    val tapZones: Boolean = true,
    val tapZoneSize: TapZoneSize = TapZoneSize.Normal,
    val invertTapZones: Boolean = false,
    val pageTurn: PageTurn = PageTurn.Slide,
    val showPageNumber: Boolean = false,
    val keepScreenOn: Boolean = true,
    val immersive: Boolean = true,
    val volumeKeys: Boolean = false,
    val readerBrightness: Float = 0f,
    val readerOrientation: ReaderOrientation = ReaderOrientation.Auto,
    val preloadScreens: Int = 2,
    val doubleTapZoom: DoubleTapZoom = DoubleTapZoom.Normal,

    val bookFontScale: Float = 1f,
    val bookFont: BookFont = BookFont.Serif,
    val bookLineSpacing: Float = 1.6f,
    val bookMargins: BookMargins = BookMargins.Normal,
    val bookJustify: Boolean = true,
    val bookTheme: BookTheme = BookTheme.App,
    val tabletopMode: Boolean = true,
    val ttsSpeed: Float = 1f,
    val ttsFollow: Boolean = true,
    val ttsAutoNext: Boolean = true,
    val homeStats: Boolean = true,
    val incognito: Boolean = false,
    val bookParagraphs: ParagraphSpacing = ParagraphSpacing.Book,
    val bookIndent: Boolean = false,
    val bookHyphenate: Boolean = true,

    val downloadWifiOnly: Boolean = true,
    val downloadParallel: Int = 2,
    val autoDeleteRead: Boolean = false,
    val smartDownloads: Boolean = false,
    val chapterLookup: Boolean = true,
    val smartAhead: Int = 3,
    val smartCleanup: Boolean = true,

    val queueMarks: Boolean = true,
    val garden: Boolean = true,
    val textCoverPlaceholder: Boolean = true,
    val dataSaver: Boolean = false,
    val weeklyGoalMinutes: Int = 120,
    val autoScrollSpeed: Int = 2,
)

object PrefKeys {
    val THEME = stringPreferencesKey("theme_mode")
    val ACCENT = stringPreferencesKey("accent")
    val DYNAMIC_LEGACY = booleanPreferencesKey("dynamic_color")
    val AMOLED = booleanPreferencesKey("amoled")
    val ACCENT_HUE = floatPreferencesKey("accent_hue")
    val CONTRAST = stringPreferencesKey("contrast")
    val CORNER_STYLE = stringPreferencesKey("corner_style")
    val APP_FONT = stringPreferencesKey("app_font")
    val TEXT_SCALE = stringPreferencesKey("text_scale")
    val NAV_LABELS = stringPreferencesKey("nav_labels")
    val GRID_SPACING = stringPreferencesKey("grid_spacing")
    val SHOW_SUBTITLES = booleanPreferencesKey("show_subtitles")
    val COVER_SIZE = stringPreferencesKey("cover_size")
    val SHOW_PROGRESS = booleanPreferencesKey("show_progress")
    val MOTION = stringPreferencesKey("motion_style")
    val HAPTICS = booleanPreferencesKey("haptics")
    val HOLD_TO_PEEK = booleanPreferencesKey("hold_to_peek")
    val START_TAB = stringPreferencesKey("start_tab")
    val COVER_FLIGHT = booleanPreferencesKey("cover_flight")
    val PEEK_BLUR = booleanPreferencesKey("peek_blur")
    val DOWNLOAD_BADGES = booleanPreferencesKey("download_badges")
    val HOME_CONTINUE = booleanPreferencesKey("home_continue")
    val HOME_UPDATED = booleanPreferencesKey("home_updated")
    val HOME_ADDED = booleanPreferencesKey("home_added")
    val LIBRARY_SORT = stringPreferencesKey("library_sort")
    val TAP_ZONE_SIZE = stringPreferencesKey("tap_zone_size")
    val DOWNLOAD_PARALLEL = intPreferencesKey("download_parallel")
    val SMART_DOWNLOADS = booleanPreferencesKey("smart_downloads")
    val CHAPTER_LOOKUP = booleanPreferencesKey("chapter_lookup")
    val QUEUE_MARKS = booleanPreferencesKey("queue_marks")
    val TEXT_COVER_PLACEHOLDER = booleanPreferencesKey("text_cover_placeholder")
    val DATA_SAVER = booleanPreferencesKey("data_saver")
    val GARDEN = booleanPreferencesKey("garden")
    val WEEKLY_GOAL = intPreferencesKey("weekly_goal_minutes")
    val AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed")
    val SMART_AHEAD = intPreferencesKey("smart_ahead")
    val SMART_CLEANUP = booleanPreferencesKey("smart_cleanup")
    val AUTO_DELETE_READ = booleanPreferencesKey("auto_delete_read")

    val DIRECTION = stringPreferencesKey("reading_direction")
    val READING_STYLE = stringPreferencesKey("reading_style")
    val AUTO_DIRECTION = booleanPreferencesKey("auto_direction")
    val PAGE_MODE = stringPreferencesKey("page_mode")
    val READER_BACKGROUND = stringPreferencesKey("reader_background")
    val COVER_ALONE = booleanPreferencesKey("cover_alone")
    val TAP_ZONES = booleanPreferencesKey("tap_zones")
    val INVERT_TAP_ZONES = booleanPreferencesKey("invert_tap_zones")
    val PAGE_TURN = stringPreferencesKey("page_turn")
    val SHOW_PAGE_NUMBER = booleanPreferencesKey("show_page_number")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val IMMERSIVE = booleanPreferencesKey("immersive")
    val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
    val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
    val READER_ORIENTATION = stringPreferencesKey("reader_orientation")
    val PRELOAD_SCREENS = intPreferencesKey("preload_screens")
    val DOUBLE_TAP_ZOOM = stringPreferencesKey("double_tap_zoom")
    val BOOK_PARAGRAPHS = stringPreferencesKey("book_paragraphs")
    val BOOK_INDENT = booleanPreferencesKey("book_indent")
    val BOOK_HYPHENATE = booleanPreferencesKey("book_hyphenate")

    val FONT_SCALE = floatPreferencesKey("book_font_scale")
    val BOOK_FONT = stringPreferencesKey("book_font")
    val BOOK_LINE_SPACING = floatPreferencesKey("book_line_spacing")
    val BOOK_MARGINS = stringPreferencesKey("book_margins")
    val BOOK_JUSTIFY = booleanPreferencesKey("book_justify")
    val BOOK_THEME = stringPreferencesKey("book_theme")
    val TABLETOP_MODE = booleanPreferencesKey("tabletop_mode")
    val TTS_SPEED = floatPreferencesKey("tts_speed")
    val TTS_FOLLOW = booleanPreferencesKey("tts_follow")
    val TTS_AUTO_NEXT = booleanPreferencesKey("tts_auto_next")
    val HOME_STATS = booleanPreferencesKey("home_stats")
    val INCOGNITO = booleanPreferencesKey("incognito")

    val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")

    val LAST_SERVER = stringPreferencesKey("last_server")
}

private inline fun <reified E : Enum<E>> Preferences.enumOf(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    // old installs only had a "dynamic color" switch
    val legacyAccent = if (this[PrefKeys.DYNAMIC_LEGACY] == false) Accent.Clover else Accent.Wallpaper
    return AppSettings(
        themeMode = enumOf(PrefKeys.THEME, defaults.themeMode),
        accent = enumOf(PrefKeys.ACCENT, legacyAccent),
        amoled = this[PrefKeys.AMOLED] ?: defaults.amoled,
        accentHue = (this[PrefKeys.ACCENT_HUE] ?: defaults.accentHue).coerceIn(0f, 360f),
        contrast = enumOf(PrefKeys.CONTRAST, defaults.contrast),
        cornerStyle = enumOf(PrefKeys.CORNER_STYLE, defaults.cornerStyle),
        appFont = enumOf(PrefKeys.APP_FONT, defaults.appFont),
        textScale = enumOf(PrefKeys.TEXT_SCALE, defaults.textScale),
        navLabels = enumOf(PrefKeys.NAV_LABELS, defaults.navLabels),
        gridSpacing = enumOf(PrefKeys.GRID_SPACING, defaults.gridSpacing),
        showSubtitles = this[PrefKeys.SHOW_SUBTITLES] ?: defaults.showSubtitles,
        coverSize = enumOf(PrefKeys.COVER_SIZE, defaults.coverSize),
        showProgress = this[PrefKeys.SHOW_PROGRESS] ?: defaults.showProgress,
        motionStyle = enumOf(PrefKeys.MOTION, defaults.motionStyle),
        haptics = this[PrefKeys.HAPTICS] ?: defaults.haptics,
        holdToPeek = this[PrefKeys.HOLD_TO_PEEK] ?: defaults.holdToPeek,
        startTab = enumOf(PrefKeys.START_TAB, defaults.startTab),
        coverFlight = this[PrefKeys.COVER_FLIGHT] ?: defaults.coverFlight,
        peekBlur = this[PrefKeys.PEEK_BLUR] ?: defaults.peekBlur,
        downloadBadges = this[PrefKeys.DOWNLOAD_BADGES] ?: defaults.downloadBadges,
        homeContinue = this[PrefKeys.HOME_CONTINUE] ?: defaults.homeContinue,
        homeUpdated = this[PrefKeys.HOME_UPDATED] ?: defaults.homeUpdated,
        homeAdded = this[PrefKeys.HOME_ADDED] ?: defaults.homeAdded,
        librarySort = enumOf(PrefKeys.LIBRARY_SORT, defaults.librarySort),

        readingDirection = enumOf(PrefKeys.DIRECTION, defaults.readingDirection),
        readingStyle = enumOf(PrefKeys.READING_STYLE, defaults.readingStyle),
        autoDirection = this[PrefKeys.AUTO_DIRECTION] ?: defaults.autoDirection,
        pageMode = enumOf(PrefKeys.PAGE_MODE, defaults.pageMode),
        readerBackground = enumOf(PrefKeys.READER_BACKGROUND, defaults.readerBackground),
        coverAlone = this[PrefKeys.COVER_ALONE] ?: defaults.coverAlone,
        tapZones = this[PrefKeys.TAP_ZONES] ?: defaults.tapZones,
        tapZoneSize = enumOf(PrefKeys.TAP_ZONE_SIZE, defaults.tapZoneSize),
        invertTapZones = this[PrefKeys.INVERT_TAP_ZONES] ?: defaults.invertTapZones,
        pageTurn = enumOf(PrefKeys.PAGE_TURN, defaults.pageTurn),
        showPageNumber = this[PrefKeys.SHOW_PAGE_NUMBER] ?: defaults.showPageNumber,
        keepScreenOn = this[PrefKeys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        immersive = this[PrefKeys.IMMERSIVE] ?: defaults.immersive,
        volumeKeys = this[PrefKeys.VOLUME_KEYS] ?: defaults.volumeKeys,
        readerBrightness = (this[PrefKeys.READER_BRIGHTNESS] ?: defaults.readerBrightness).coerceIn(0f, 1f),
        readerOrientation = enumOf(PrefKeys.READER_ORIENTATION, defaults.readerOrientation),
        preloadScreens = (this[PrefKeys.PRELOAD_SCREENS] ?: defaults.preloadScreens).coerceIn(1, 4),
        doubleTapZoom = enumOf(PrefKeys.DOUBLE_TAP_ZOOM, defaults.doubleTapZoom),

        bookFontScale = (this[PrefKeys.FONT_SCALE] ?: defaults.bookFontScale).coerceIn(0.7f, 2.2f),
        bookFont = enumOf(PrefKeys.BOOK_FONT, defaults.bookFont),
        bookLineSpacing = (this[PrefKeys.BOOK_LINE_SPACING] ?: defaults.bookLineSpacing).coerceIn(1.2f, 2.2f),
        bookMargins = enumOf(PrefKeys.BOOK_MARGINS, defaults.bookMargins),
        bookJustify = this[PrefKeys.BOOK_JUSTIFY] ?: defaults.bookJustify,
        bookTheme = enumOf(PrefKeys.BOOK_THEME, defaults.bookTheme),
        tabletopMode = this[PrefKeys.TABLETOP_MODE] ?: defaults.tabletopMode,
        ttsSpeed = (this[PrefKeys.TTS_SPEED] ?: defaults.ttsSpeed).coerceIn(0.6f, 2f),
        ttsFollow = this[PrefKeys.TTS_FOLLOW] ?: defaults.ttsFollow,
        ttsAutoNext = this[PrefKeys.TTS_AUTO_NEXT] ?: defaults.ttsAutoNext,
        homeStats = this[PrefKeys.HOME_STATS] ?: defaults.homeStats,
        incognito = this[PrefKeys.INCOGNITO] ?: defaults.incognito,
        bookParagraphs = enumOf(PrefKeys.BOOK_PARAGRAPHS, defaults.bookParagraphs),
        bookIndent = this[PrefKeys.BOOK_INDENT] ?: defaults.bookIndent,
        bookHyphenate = this[PrefKeys.BOOK_HYPHENATE] ?: defaults.bookHyphenate,

        downloadWifiOnly = this[PrefKeys.DOWNLOAD_WIFI_ONLY] ?: defaults.downloadWifiOnly,
        downloadParallel = (this[PrefKeys.DOWNLOAD_PARALLEL] ?: defaults.downloadParallel).coerceIn(1, 3),
        autoDeleteRead = this[PrefKeys.AUTO_DELETE_READ] ?: defaults.autoDeleteRead,
        smartDownloads = this[PrefKeys.SMART_DOWNLOADS] ?: defaults.smartDownloads,
        chapterLookup = this[PrefKeys.CHAPTER_LOOKUP] ?: defaults.chapterLookup,
        smartAhead = (this[PrefKeys.SMART_AHEAD] ?: defaults.smartAhead).coerceIn(1, 5),
        smartCleanup = this[PrefKeys.SMART_CLEANUP] ?: defaults.smartCleanup,
        queueMarks = this[PrefKeys.QUEUE_MARKS] ?: defaults.queueMarks,
        textCoverPlaceholder = this[PrefKeys.TEXT_COVER_PLACEHOLDER] ?: defaults.textCoverPlaceholder,
        dataSaver = this[PrefKeys.DATA_SAVER] ?: defaults.dataSaver,
        garden = this[PrefKeys.GARDEN] ?: defaults.garden,
        weeklyGoalMinutes = (this[PrefKeys.WEEKLY_GOAL] ?: defaults.weeklyGoalMinutes).coerceIn(0, 3000),
        autoScrollSpeed = (this[PrefKeys.AUTO_SCROLL_SPEED] ?: defaults.autoScrollSpeed).coerceIn(1, 3),
    )
}

// settings are per profile; a new profile starts as a copy of the last used one
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStore(
    private val context: Context,
    private val accounts: AccountStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val opened = ConcurrentHashMap<String, DataStore<Preferences>>()
    private val lock = Mutex()
    private val folder: File get() = File(context.filesDir, "datastore")

    private fun fileFor(key: String) = File(folder, "$PREFIX$key$SUFFIX")

    private companion object {
        const val PREFIX = "profile_settings_"
        const val SUFFIX = ".preferences_pb"
    }

    private suspend fun storeFor(key: String): DataStore<Preferences> = lock.withLock { openLocked(key) }

    private suspend fun openLocked(key: String): DataStore<Preferences> {
        opened[key]?.let { return it }
        val file = fileFor(key)
        val seed: Preferences? = if (!file.isFile) {
            val latestKey = folder.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
                ?.maxByOrNull { it.lastModified() }?.name?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)
            // must be the same store object the rest of the app uses
            if (latestKey != null && latestKey != key) openLocked(latestKey).data.first() else context.settingsStore.data.first()
        } else null
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { file },
        )
        if (seed != null) {
            store.edit { prefs ->
                for ((k, v) in seed.asMap()) {
                    // device-level data isn't copied
                    if (k.name == PrefKeys.LAST_SERVER.name) continue
                    @Suppress("UNCHECKED_CAST")
                    prefs[k as Preferences.Key<Any>] = v
                }
            }
        }
        opened[key] = store
        return store
    }

    private val current: Flow<DataStore<Preferences>> = accounts.key.flatMapLatest { key ->
        if (key == null) flowOf(context.settingsStore) else flow { emit(storeFor(key)) }
    }

    private suspend fun currentStore(): DataStore<Preferences> = accounts.key.value?.let { storeFor(it) } ?: context.settingsStore

    val settings: Flow<AppSettings> = current.flatMapLatest { it.data }.map { it.toAppSettings() }

    val lastServer: Flow<String> = context.settingsStore.data.map { it[PrefKeys.LAST_SERVER].orEmpty() }

    suspend fun setLastServer(url: String) {
        context.settingsStore.edit { it[PrefKeys.LAST_SERVER] = url }
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        currentStore().edit { it[key] = value }
    }

    suspend fun <E : Enum<E>> setEnum(key: Preferences.Key<String>, value: E) = set(key, value.name)

    // resets everything except the remembered server and incognito
    suspend fun resetAll() {
        currentStore().edit { prefs ->
            val incognito = prefs[PrefKeys.INCOGNITO]
            prefs.clear()
            if (incognito != null) prefs[PrefKeys.INCOGNITO] = incognito
        }
    }

    suspend fun setReadingDirection(direction: ReadingDirection) = setEnum(PrefKeys.DIRECTION, direction)

    suspend fun setPageMode(mode: PageMode) = setEnum(PrefKeys.PAGE_MODE, mode)

    suspend fun setBookFontScale(scale: Float) = set(PrefKeys.FONT_SCALE, scale.coerceIn(0.7f, 2.2f))
}
