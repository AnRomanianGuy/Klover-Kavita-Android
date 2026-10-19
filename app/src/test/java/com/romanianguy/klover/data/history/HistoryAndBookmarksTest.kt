package com.romanianguy.klover.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryAndBookmarksTest {
    private val hour = 3_600_000L
    private fun entry(chapter: Int, at: Long, series: Int = 1) = HistoryEntry(
        chapter,
        series,
        "Series",
        "Chapter $chapter",
        at,
    )

    @Test
    fun finishingTheSameChapterAgainWithinASittingReplacesIt() {
        val list = withEntry(listOf(entry(5, 1_000)), entry(5, 1_000 + 2 * hour))
        assertEquals(1, list.size)
        assertEquals(1_000 + 2 * hour, list[0].at)
    }

    @Test
    fun readingItAgainTheNextDayIsAnotherEntry() {
        val list = withEntry(listOf(entry(5, 1_000)), entry(5, 1_000 + 30 * hour))
        assertEquals(2, list.size)
    }

    @Test
    fun theOldestEntriesGoWhenThereAreTooMany() {
        var list = emptyList<HistoryEntry>()
        for (i in 1..700) list = withEntry(list, entry(i, i * 100L * hour))
        assertEquals(600, list.size)
        assertEquals(101, list.first().chapterId)
        assertEquals(700, list.last().chapterId)
    }

    @Test
    fun entriesAreGroupedByDayNewestFirst() {
        val zone = ZoneId.of("UTC")
        val day1 = LocalDate.of(2026, 10, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        val groups = groupByDay(
            listOf(entry(1, day1 + hour), entry(2, day1 + 25 * hour), entry(3, day1 + 26 * hour)),
            zone,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 16)), groups.map { it.first })
        assertEquals(listOf(3, 2), groups[0].second.map { it.chapterId })
    }

    private fun bookmark(chapter: Int, page: Int) = Bookmark(chapter, 1, "Series", "Chapter $chapter", page, at = 1)

    @Test
    fun flaggingAPageTwiceTakesTheFlagOff() {
        val flagged = toggled(emptyList(), bookmark(5, 3))
        assertEquals(1, flagged.size)
        assertTrue(toggled(flagged, bookmark(5, 3)).isEmpty())
    }

    @Test
    fun otherPagesAndChaptersAreSeparateFlags() {
        val list = toggled(toggled(emptyList(), bookmark(5, 3)), bookmark(5, 4))
        assertEquals(2, list.size)
        assertEquals(3, toggled(list, bookmark(6, 3)).size)
        assertFalse(toggled(list, bookmark(5, 3)).any { it.page == 3 && it.chapterId == 5 })
    }
}
