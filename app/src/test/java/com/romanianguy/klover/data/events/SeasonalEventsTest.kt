package com.romanianguy.klover.data.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SeasonalEventsTest {
    private fun on(year: Int, month: Int, day: Int) = SeasonalEvents.active(LocalDate.of(year, month, day))

    @Test
    fun halloweenStartsTwoWeeksBeforeTheDay() {
        assertNull("the day before the window", on(2026, 10, 16))
        assertEquals(SeasonalEvent.Halloween, on(2026, 10, 17)?.event)
    }

    @Test
    fun itRunsThroughTheDayAndStaysForAWeek() {
        assertEquals(SeasonalEvent.Halloween, on(2026, 10, 31)?.event)
        assertEquals(SeasonalEvent.Halloween, on(2026, 11, 7)?.event)
        assertNull("the day after the week", on(2026, 11, 8))
    }

    @Test
    fun theRestOfTheYearHasNoEvent() {
        for ((m, d) in listOf(1 to 1, 3 to 15, 6 to 21, 9 to 30, 12 to 25)) assertNull("$m/$d", on(2026, m, d))
    }

    @Test
    fun theSameEventAYearLaterIsANewOne() {
        val a = on(2026, 10, 25)!!
        val b = on(2027, 10, 25)!!
        assertEquals("halloween-2026", a.id)
        assertEquals("halloween-2027", b.id)
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun leapYearsDoNotMoveIt() {
        assertEquals(SeasonalEvent.Halloween, on(2028, 10, 17)?.event)
        assertNull(on(2028, 10, 16))
    }

    @Test
    fun theDaysToTheDayCountDownAndGoNegative() {
        assertEquals(14, on(2026, 10, 17)!!.daysToPeak(LocalDate.of(2026, 10, 17)))
        assertEquals(0, on(2026, 10, 31)!!.daysToPeak(LocalDate.of(2026, 10, 31)))
        assertEquals(-7, on(2026, 11, 7)!!.daysToPeak(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun everyDayOfTheWindowIsInItAndNoOtherDayIs() {
        var inside = 0
        var day = LocalDate.of(2026, 1, 1)
        while (day.year == 2026) {
            if (SeasonalEvents.active(day) != null) inside++
            day = day.plusDays(1)
        }
        assertEquals(SeasonalEvents.LEAD_DAYS + SeasonalEvents.TAIL_DAYS + 1, inside.toLong())
    }
}
