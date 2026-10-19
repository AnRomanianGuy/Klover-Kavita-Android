package com.romanianguy.klover.data.prefs

import androidx.datastore.preferences.core.preferencesOf
import com.romanianguy.klover.data.repo.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreSettingsTest {
    @Test
    fun theNewOptionsStartAtSensibleDefaults() {
        val s = preferencesOf().toAppSettings()
        assertTrue(s.coverFlight && s.peekBlur && s.downloadBadges)
        assertTrue(s.homeContinue && s.homeUpdated && s.homeAdded)
        assertEquals(LibrarySort.Name, s.librarySort)
        assertEquals(TapZoneSize.Normal, s.tapZoneSize)
        assertEquals(2, s.downloadParallel)
        assertFalse(s.autoDeleteRead)
    }

    @Test
    fun theNewOptionsComeBackFromStorage() {
        val s = preferencesOf(
            PrefKeys.COVER_FLIGHT to false,
            PrefKeys.HOME_UPDATED to false,
            PrefKeys.LIBRARY_SORT to LibrarySort.RecentlyUpdated.name,
            PrefKeys.TAP_ZONE_SIZE to TapZoneSize.Wide.name,
            PrefKeys.DOWNLOAD_PARALLEL to 3,
            PrefKeys.AUTO_DELETE_READ to true,
        ).toAppSettings()
        assertFalse(s.coverFlight)
        assertFalse(s.homeUpdated)
        assertEquals(LibrarySort.RecentlyUpdated, s.librarySort)
        assertEquals(TapZoneSize.Wide, s.tapZoneSize)
        assertEquals(3, s.downloadParallel)
        assertTrue(s.autoDeleteRead)
    }

    @Test
    fun parallelDownloadsStayBetweenOneAndThree() {
        assertEquals(1, preferencesOf(PrefKeys.DOWNLOAD_PARALLEL to 0).toAppSettings().downloadParallel)
        assertEquals(3, preferencesOf(PrefKeys.DOWNLOAD_PARALLEL to 9).toAppSettings().downloadParallel)
    }

    @Test
    fun wideTapStripsAreWiderThanNarrowOnes() {
        assertTrue(TapZoneSize.Narrow.edge < TapZoneSize.Normal.edge && TapZoneSize.Normal.edge < TapZoneSize.Wide.edge)
    }
}

class ReaderOptionsTest {
    @Test
    fun theReaderOptionsStartAtDefaultsAndRoundTrip() {
        val d = preferencesOf().toAppSettings()
        assertEquals(0f, d.readerBrightness, 0f)
        assertEquals(ReaderOrientation.Auto, d.readerOrientation)
        assertEquals(2, d.preloadScreens)
        assertEquals(DoubleTapZoom.Normal, d.doubleTapZoom)
        assertEquals(ParagraphSpacing.Book, d.bookParagraphs)
        assertFalse(d.bookIndent)
        assertTrue(d.bookHyphenate)

        val s = preferencesOf(
            PrefKeys.READER_BRIGHTNESS to 4f,
            PrefKeys.READER_ORIENTATION to ReaderOrientation.Landscape.name,
            PrefKeys.PRELOAD_SCREENS to 9,
            PrefKeys.DOUBLE_TAP_ZOOM to DoubleTapZoom.Close.name,
            PrefKeys.BOOK_PARAGRAPHS to ParagraphSpacing.Wide.name,
            PrefKeys.BOOK_INDENT to true,
            PrefKeys.BOOK_HYPHENATE to false,
        ).toAppSettings()
        assertEquals(1f, s.readerBrightness, 0f)
        assertEquals(ReaderOrientation.Landscape, s.readerOrientation)
        assertEquals(4, s.preloadScreens)
        assertEquals(3.5f, s.doubleTapZoom.scale, 0f)
        assertEquals(1.4f, s.bookParagraphs.em!!, 0f)
        assertTrue(s.bookIndent)
        assertFalse(s.bookHyphenate)
    }
}

class IncognitoSettingTest {
    @Test
    fun incognitoIsOffByDefaultAndRoundTrips() {
        assertFalse(preferencesOf().toAppSettings().incognito)
        assertTrue(preferencesOf(PrefKeys.INCOGNITO to true).toAppSettings().incognito)
    }
}
