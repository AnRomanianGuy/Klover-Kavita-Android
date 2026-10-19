package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.model.LibraryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeTimeTest {
    @Test
    fun mangaIsReadQuickerThanOtherImagePages() {
        assertEquals(PaceKind.Manga, paceKindForSeries(ContentFormat.Archive, LibraryKind.Manga))
        assertEquals(PaceKind.Pages, paceKindForSeries(ContentFormat.Archive, LibraryKind.Comic))
        assertEquals(PaceKind.Pages, paceKindForSeries(ContentFormat.Archive, null))
    }

    @Test
    fun aPdfIsReadByWhatItsLibraryHolds() {
        assertEquals(PaceKind.Manga, paceKindForSeries(ContentFormat.Pdf, LibraryKind.Manga))
        assertEquals(PaceKind.Pages, paceKindForSeries(ContentFormat.Pdf, LibraryKind.ComicVine))
        assertEquals(PaceKind.Pdf, paceKindForSeries(ContentFormat.Pdf, LibraryKind.Book))
    }

    @Test
    fun aBookHasNoGuessUntilItIsOpen() {
        assertNull(paceKindForSeries(ContentFormat.Epub, LibraryKind.Book))
    }

    @Test
    fun theTimeLeftIsThePagesLeftAtThePace() {
        assertEquals(400L, volumeSecondsLeft(50f, 8f))
        // half a chapter of 20 pages is 10 pages
        assertEquals(150L, volumeSecondsLeft(10f, 15f))
    }

    @Test
    fun nothingLeftOrNoPaceSaysNothing() {
        assertNull(volumeSecondsLeft(0f, 8f))
        assertNull(volumeSecondsLeft(0.3f, 8f))
        assertNull(volumeSecondsLeft(30f, null))
    }
}
