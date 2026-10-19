package com.romanianguy.klover.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadingStatsTest {
    private val today = LocalDate.of(2026, 10, 17)

    private fun days(vararg entries: Pair<LocalDate, DayStat>) = entries.associate { it.first.toString() to it.second }
    private fun read(minutes: Int, pages: Int = 0, chapters: Int = 0) = DayStat(minutes * 60L, pages, chapters)

    @Test
    fun noDataMeansNothingToShow() {
        val s = summarize(emptyMap(), today)
        assertFalse(s.hasData)
        assertEquals(0, s.currentStreak)
        assertEquals(7, s.last7.size)
        assertEquals(today, s.last7.last().date)
    }

    @Test
    fun theWeekAndMonthCoverTheRightDays() {
        val s = summarize(
            days(
                today to read(10, pages = 5),
                today.minusDays(6) to read(20, pages = 8),
                today.minusDays(7) to read(30, pages = 9), // outside the week, inside the month
                today.minusDays(29) to read(40),
                today.minusDays(30) to read(50), // outside the month
            ),
            today,
        )
        assertEquals(10 * 60L, s.todaySeconds)
        assertEquals(30 * 60L, s.weekSeconds)
        assertEquals(100 * 60L, s.monthSeconds)
        assertEquals(150 * 60L, s.totalSeconds)
        assertEquals(13, s.weekPages)
        assertEquals(22, s.totalPages)
    }

    @Test
    fun aStreakCountsConsecutiveReadingDaysEndingTodayOrYesterday() {
        val ending = summarize(
            days(today to read(5), today.minusDays(1) to read(5), today.minusDays(2) to read(5)),
            today,
        )
        assertEquals(3, ending.currentStreak)
        // Nothing read yet today: the streak is still alive until the day is over.
        val alive = summarize(days(today.minusDays(1) to read(5), today.minusDays(2) to read(5)), today)
        assertEquals(2, alive.currentStreak)
        // Missed yesterday as well: it is gone.
        val broken = summarize(days(today.minusDays(2) to read(5)), today)
        assertEquals(0, broken.currentStreak)
    }

    @Test
    fun aVisitUnderAMinuteDoesNotCountAsAReadingDay() {
        val s = summarize(days(today to DayStat(seconds = 30), today.minusDays(1) to read(5)), today)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.activeDays)
    }

    @Test
    fun theBestStreakIsTheLongestRunEver() {
        val s = summarize(
            days(
                today to read(5),
                today.minusDays(10) to read(5), today.minusDays(11) to read(5), today.minusDays(12) to read(5), today.minusDays(13) to read(5),
            ),
            today,
        )
        assertEquals(1, s.currentStreak)
        assertEquals(4, s.bestStreak)
        assertEquals(5, s.activeDays)
    }

    @Test
    fun averagesAndChaptersAddUp() {
        val s = summarize(days(today to read(10, chapters = 1), today.minusDays(3) to read(30, chapters = 2)), today)
        assertEquals(3, s.totalChapters)
        assertEquals(20 * 60L, s.averageSecondsPerActiveDay)
    }

    @Test
    fun badKeysInTheFileAreIgnored() {
        val s = summarize(mapOf("not-a-date" to read(99), today.toString() to read(1)), today)
        assertEquals(60L, s.totalSeconds)
    }

    @Test
    fun durationsSplitIntoHoursAndMinutes() {
        assertEquals(0 to 0, durationParts(45))
        assertEquals(0 to 5, durationParts(5 * 60 + 59))
        assertEquals(1 to 20, durationParts(80 * 60L))
        assertTrue(durationParts(25 * 3600L).first == 25)
    }
}
