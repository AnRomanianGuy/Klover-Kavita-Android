package com.romanianguy.klover.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressTest {
    @Test
    fun theSixthPageCountsAsSixPagesRead() {
        // The bug report: "if I'm on page 6 it saves page 5".
        assertEquals(6, pagesRead(lastVisibleIndex = 5, pageCount = 20))
    }

    @Test
    fun theFirstPageCountsAsOne() {
        assertEquals(1, pagesRead(0, 20))
    }

    @Test
    fun reachingTheLastPageSavesThePageCountWhichMarksTheChapterRead() {
        assertEquals(20, pagesRead(19, 20))
    }

    @Test
    fun neverSavesMoreThanThePageCountOrLessThanZero() {
        assertEquals(20, pagesRead(500, 20))
        assertEquals(0, pagesRead(-3, 20))
        assertEquals(0, pagesRead(0, 0))
    }

    @Test
    fun resumesOnThePageThatWasOnScreenWhenSaved() {
        assertEquals(5, resumeIndex(savedPagesRead = 6, pageCount = 20))
    }

    @Test
    fun savingAndResumingRoundTripsToTheSamePage() {
        for (index in 0 until 19) {
            assertEquals(index, resumeIndex(pagesRead(index, 20), 20))
        }
        // The last page marks the chapter read, so reopening it starts again from the beginning.
        assertEquals(0, resumeIndex(pagesRead(19, 20), 20))
    }

    @Test
    fun unreadAndFinishedChaptersOpenOnTheFirstPage() {
        assertEquals(0, resumeIndex(0, 20))
        assertEquals(0, resumeIndex(20, 20))
        assertEquals(0, resumeIndex(25, 20))
    }

    @Test
    fun emptyDocumentIsHandled() {
        assertEquals(0, resumeIndex(3, 0))
    }
}
