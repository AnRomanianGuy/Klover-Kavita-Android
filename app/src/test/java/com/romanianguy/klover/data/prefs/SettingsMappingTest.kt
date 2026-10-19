package com.romanianguy.klover.data.prefs

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun aFreshInstallGetsEveryDefault() {
        assertEquals(AppSettings(), preferencesOf().toAppSettings())
    }

    @Test
    fun storedValuesComeBack() {
        val prefs = mutablePreferencesOf().apply {
            this[PrefKeys.ACCENT] = Accent.Rose.name
            this[PrefKeys.AMOLED] = true
            this[PrefKeys.PAGE_TURN] = PageTurn.Fade.name
            this[PrefKeys.BOOK_MARGINS] = BookMargins.Wide.name
            this[PrefKeys.INVERT_TAP_ZONES] = true
        }
        val settings = prefs.toAppSettings()
        assertEquals(Accent.Rose, settings.accent)
        assertTrue(settings.amoled)
        assertEquals(PageTurn.Fade, settings.pageTurn)
        assertEquals(BookMargins.Wide, settings.bookMargins)
        assertTrue(settings.invertTapZones)
    }

    @Test
    fun anUnknownEnumNameFallsBackToTheDefault() {
        val prefs = preferencesOf(PrefKeys.THEME to "Solarized", PrefKeys.READER_BACKGROUND to "Plaid")
        val settings = prefs.toAppSettings()
        assertEquals(ThemeMode.System, settings.themeMode)
        assertEquals(ReaderBackground.Black, settings.readerBackground)
    }

    @Test
    fun outOfRangeNumbersAreClamped() {
        val prefs = preferencesOf(PrefKeys.FONT_SCALE to 9f, PrefKeys.BOOK_LINE_SPACING to 0.1f)
        val settings = prefs.toAppSettings()
        assertEquals(2.2f, settings.bookFontScale, 0f)
        assertEquals(1.2f, settings.bookLineSpacing, 0f)
    }

    @Test
    fun upgradedInstallsThatTurnedDynamicColorOffKeepAFixedAccent() {
        val prefs = preferencesOf(PrefKeys.DYNAMIC_LEGACY to false)
        assertEquals(Accent.Clover, prefs.toAppSettings().accent)
        assertEquals(Accent.Wallpaper, preferencesOf(PrefKeys.DYNAMIC_LEGACY to true).toAppSettings().accent)
    }
}
