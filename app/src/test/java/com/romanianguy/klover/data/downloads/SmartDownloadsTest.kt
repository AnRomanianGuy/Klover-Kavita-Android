package com.romanianguy.klover.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDownloadsTest {
    private fun request(id: Int, volume: Float = 0f, order: Float = 0f) = DownloadRequest(
        chapterId = id, seriesId = 1, volumeId = 0, libraryId = 1, seriesName = "S", title = "T$id",
        format = 0, pages = 10, volumeOrder = volume, order = order,
    )

    private fun entry(
        id: Int,
        auto: Boolean = true,
        read: Int = 10,
        pending: Boolean = false,
        series: Int = 1,
        order: Float = id.toFloat(),
        state: DownloadState = DownloadState.Completed,
        pages: Int = 10,
    ) = DownloadEntry(
        chapterId = id, seriesId = series, server = "http://s", seriesName = "S", title = "T$id", format = 0,
        pages = pages, order = order, state = state, localPagesRead = read, pendingSync = pending, auto = auto,
    )


    private val chapters = (1..8).map { request(it, order = it.toFloat()) }

    @Test
    fun takesTheNextUnreadChaptersAfterTheOneBeingRead() {
        val ahead = chaptersAhead(chapters, unread = (3..8).toSet(), currentId = 3, count = 3)
        assertEquals(listOf(4, 5, 6), ahead.map { it.chapterId })
    }

    @Test
    fun chaptersAlreadyReadAreSkippedNotCounted() {
        // 5 was read earlier: the three after chapter 3 are 4, 6 and 7.
        val ahead = chaptersAhead(chapters, unread = setOf(3, 4, 6, 7, 8), currentId = 3, count = 3)
        assertEquals(listOf(4, 6, 7), ahead.map { it.chapterId })
    }

    @Test
    fun nearTheEndThereIsLessToFetch() {
        assertEquals(
            listOf(8),
            chaptersAhead(chapters, unread = setOf(7, 8), currentId = 7, count = 3).map { it.chapterId },
        )
        assertTrue(chaptersAhead(chapters, unread = setOf(8), currentId = 8, count = 3).isEmpty())
    }

    @Test
    fun aChapterThatIsNotInTheSeriesOrNothingToAskForGivesNothing() {
        assertTrue(chaptersAhead(chapters, unread = (1..8).toSet(), currentId = 99, count = 3).isEmpty())
        assertTrue(chaptersAhead(chapters, unread = (1..8).toSet(), currentId = 1, count = 0).isEmpty())
    }


    @Test
    fun readChaptersGoExceptTheLatestOne() {
        val entries = listOf(entry(1), entry(2), entry(3), entry(4, read = 3))
        // 1, 2, 3 are read; 3 is the latest read and stays. 4 is still being read.
        assertEquals(setOf(1, 2), smartCleanupCandidates(entries).toSet())
    }

    @Test
    fun aChapterTheUserDownloadedIsNeverCleanedUp() {
        val entries = listOf(entry(1, auto = false), entry(2), entry(3))
        assertEquals(listOf(2), smartCleanupCandidates(entries))
    }

    @Test
    fun nothingGoesBeforeTheServerHasThePosition() {
        val entries = listOf(entry(1, pending = true), entry(2), entry(3))
        // 1 is waiting to sync so it stays; of the other two the later one is kept.
        assertEquals(listOf(2), smartCleanupCandidates(entries))
        assertTrue(smartCleanupCandidates(listOf(entry(1, pending = true), entry(2, pending = true))).isEmpty())
    }

    @Test
    fun unfinishedAndUnreadChaptersStay() {
        val entries = listOf(
            entry(1, read = -1),
            entry(2, read = 9),
            entry(3, state = DownloadState.Running, read = 10),
            entry(4, pages = 0, read = 0),
        )
        assertTrue(smartCleanupCandidates(entries).isEmpty())
    }

    @Test
    fun eachSeriesKeepsItsOwnLatestReadChapter() {
        val entries = listOf(
            entry(1, series = 1), entry(2, series = 1),
            entry(11, series = 2), entry(12, series = 2), entry(13, series = 2),
        )
        assertEquals(setOf(1, 11, 12), smartCleanupCandidates(entries).toSet())
    }

    @Test
    fun aLoneReadChapterIsKept() {
        assertFalse(smartCleanupCandidates(listOf(entry(1))).isNotEmpty())
    }
}
