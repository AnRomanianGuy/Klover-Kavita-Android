package com.romanianguy.klover.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesReaderPrefsTest {
    private val general = AppSettings()

    @Test
    fun aNewSeriesHasNoChoices() {
        assertTrue(SeriesReaderPrefs().isEmpty)
        assertTrue(SeriesReaderPrefs.decode(null).isEmpty)
        assertTrue(SeriesReaderPrefs.decode("").isEmpty)
    }

    @Test
    fun whatIsWrittenIsReadBack() {
        val prefs = SeriesReaderPrefs(
            ReadingDirection.RightToLeft,
            PageMode.Double,
            ReadingStyle.Webtoon,
            DoubleTapZoom.Close,
        )
        assertEquals(prefs, SeriesReaderPrefs.decode(prefs.encode()))
        val partial = SeriesReaderPrefs(direction = ReadingDirection.LeftToRight)
        assertEquals("LeftToRight,-,-,-", partial.encode())
        assertEquals(partial, SeriesReaderPrefs.decode(partial.encode()))
    }

    @Test
    fun anUnreadableValueIsNoChoiceRatherThanACrash() {
        val decoded = SeriesReaderPrefs.decode("Sideways,Triple,Pages")
        assertNull(decoded.direction)
        assertNull(decoded.pageMode)
        assertEquals(ReadingStyle.Pages, decoded.style)
        assertNull(decoded.zoom)
    }

    @Test
    fun libraryTypesHaveTheirUsualDirection() {
        assertEquals(ReadingDirection.RightToLeft, defaultDirectionFor(0)) // manga
        assertEquals(ReadingDirection.LeftToRight, defaultDirectionFor(1)) // comics
        assertEquals(ReadingDirection.LeftToRight, defaultDirectionFor(5)) // comic vine
        assertNull(defaultDirectionFor(2)) // books
        assertNull(defaultDirectionFor(null))
    }

    @Test
    fun theLibraryDefaultAppliesWhenTheSeriesHasNoChoice() {
        val manga = effectiveReaderSettings(general, SeriesReaderPrefs(), libraryType = 0)
        assertEquals(ReadingDirection.RightToLeft, manga.readingDirection)
        val comic = effectiveReaderSettings(general, SeriesReaderPrefs(), libraryType = 1)
        assertEquals(ReadingDirection.LeftToRight, comic.readingDirection)
    }

    @Test
    fun aChoiceForTheSeriesBeatsTheLibraryDefault() {
        val picked = SeriesReaderPrefs(direction = ReadingDirection.LeftToRight)
        assertEquals(
            ReadingDirection.LeftToRight,
            effectiveReaderSettings(general, picked, libraryType = 0).readingDirection,
        )
    }

    @Test
    fun withDirectionByLibraryOffTheGeneralSettingIsUsed() {
        val fixed = general.copy(autoDirection = false, readingDirection = ReadingDirection.LeftToRight)
        assertEquals(
            ReadingDirection.LeftToRight,
            effectiveReaderSettings(fixed, SeriesReaderPrefs(), libraryType = 0).readingDirection,
        )
        // A series' own choice still wins.
        val picked = SeriesReaderPrefs(direction = ReadingDirection.RightToLeft)
        assertEquals(
            ReadingDirection.RightToLeft,
            effectiveReaderSettings(fixed, picked, libraryType = 1).readingDirection,
        )
    }

    @Test
    fun booksFallBackToTheGeneralDirection() {
        val rtl = general.copy(readingDirection = ReadingDirection.RightToLeft)
        assertEquals(
            ReadingDirection.RightToLeft,
            effectiveReaderSettings(rtl, SeriesReaderPrefs(), libraryType = 2).readingDirection,
        )
    }

    @Test
    fun theOtherChoicesOverrideTheGeneralOnesOnlyForTheirSeries() {
        val picked = SeriesReaderPrefs(
            pageMode = PageMode.Single,
            style = ReadingStyle.Webtoon,
            zoom = DoubleTapZoom.Gentle,
        )
        val effective = effectiveReaderSettings(general, picked, libraryType = 1)
        assertEquals(PageMode.Single, effective.pageMode)
        assertEquals(ReadingStyle.Webtoon, effective.readingStyle)
        assertEquals(DoubleTapZoom.Gentle, effective.doubleTapZoom)
        // A series with no choices follows the general settings.
        val untouched = effectiveReaderSettings(general, SeriesReaderPrefs(), libraryType = 1)
        assertEquals(general.pageMode, untouched.pageMode)
        assertEquals(general.readingStyle, untouched.readingStyle)
        assertEquals(general.doubleTapZoom, untouched.doubleTapZoom)
        assertFalse(untouched.readingStyle == ReadingStyle.Webtoon)
    }
}
