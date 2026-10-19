package com.romanianguy.klover.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPartsTest {
    private fun entry(
        ranges: List<PageRange> = emptyList(),
        state: DownloadState = DownloadState.Completed,
        pages: Int = 20,
    ) = DownloadEntry(
        chapterId = 1, seriesId = 1, server = "s", seriesName = "Series", title = "Volume 1", format = 1,
        pages = pages, state = state, ranges = ranges, doneItems = 3, totalItems = 6,
    )

    private val one = PageRange(4, 7, "Chapter 1.5")
    private val two = PageRange(7, 12, "Chapter 2")

    @Test
    fun aRangeThatStartsWhereAnotherDoesIsTheSameChapter() {
        val ranges = withRange(withRange(emptyList(), two), one)
        assertEquals(listOf(4, 7), ranges.map { it.from })
        assertEquals(ranges, withRange(ranges, PageRange(4, 7, "again")))
    }

    @Test
    fun theWantedPagesAreCountedOnceEvenWhenEstimatesOverlap() {
        assertEquals((4 until 12).toList(), pagesOf(listOf(one, two)))
        assertEquals(listOf(4, 5, 6, 7), pagesOf(listOf(PageRange(4, 7), PageRange(6, 8))))
    }

    @Test
    fun anEntryOfPartsKnowsHowManyPagesAndWhatItHolds() {
        val parts = entry(ranges = listOf(one, two))
        assertEquals(8, parts.wantedPages)
        assertEquals("Volume 1 · Chapter 1.5, Chapter 2", parts.displayTitle)
        assertTrue(!parts.isWhole)
        val whole = entry()
        assertEquals(20, whole.wantedPages)
        assertEquals("Volume 1", whole.displayTitle)
    }

    @Test
    fun aVolumeOfWhichOnlySomePagesAreDownloadedIsOnlyPartlyDownloaded() {
        assertEquals(DownloadBadge.Partial, summarizeDownloads(listOf(entry(ranges = listOf(one)))))
        assertEquals(DownloadBadge.Done, summarizeDownloads(listOf(entry())))
    }

    @Test
    fun aChapterIsDownloadedWhenItsPagesWereAskedFor() {
        val parts = entry(ranges = listOf(one, two))
        assertEquals(DownloadBadge.Done, partBadge(parts, 4, 7))
        assertEquals(DownloadBadge.Done, partBadge(parts, 7, 12))
        // the chapter before and the one after were not asked for
        assertEquals(DownloadBadge.None, partBadge(parts, 0, 4))
        assertEquals(DownloadBadge.None, partBadge(parts, 12, 14))
        // and a chapter only half covered is not downloaded
        assertEquals(DownloadBadge.None, partBadge(entry(ranges = listOf(one)), 4, 9))
    }

    @Test
    fun everyChapterOfAFileDownloadedWholeIsDownloaded() {
        assertEquals(DownloadBadge.Done, partBadge(entry(), 0, 4))
        assertEquals(DownloadBadge.None, partBadge(null, 0, 4))
    }

    @Test
    fun aChapterOnItsWayShowsTheProgress() {
        val running = entry(ranges = listOf(one), state = DownloadState.Running)
        val badge = partBadge(running, 4, 7)
        assertTrue(badge is DownloadBadge.Active)
        assertEquals(0.5f, (badge as DownloadBadge.Active).progress, 0.001f)
    }
}
