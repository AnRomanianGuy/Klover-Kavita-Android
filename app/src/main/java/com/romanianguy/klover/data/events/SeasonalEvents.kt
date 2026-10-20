package com.romanianguy.klover.data.events

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SeasonalEvent(val key: String) {
    Halloween("halloween"),
}

// an event that is on now; [year] is the year it peaks in, so the same event a year later is a new one
data class ActiveEvent(val event: SeasonalEvent, val year: Int, val peak: LocalDate) {
    val id: String get() = "${event.key}-$year"

    fun daysToPeak(today: LocalDate): Long = ChronoUnit.DAYS.between(today, peak)
}

object SeasonalEvents {
    // an event starts two weeks before its day and stays for a week after it
    const val LEAD_DAYS = 14L
    const val TAIL_DAYS = 7L

    fun peak(event: SeasonalEvent, year: Int): LocalDate = when (event) {
        SeasonalEvent.Halloween -> LocalDate.of(year, 10, 31)
    }

    fun active(today: LocalDate): ActiveEvent? {
        // a window can run into the next year, so last year's and next year's days are looked at too
        for (year in today.year - 1..today.year + 1) {
            for (event in SeasonalEvent.entries) {
                val peak = peak(event, year)
                if (!today.isBefore(peak.minusDays(LEAD_DAYS)) && !today.isAfter(peak.plusDays(TAIL_DAYS))) {
                    return ActiveEvent(event, year, peak)
                }
            }
        }
        return null
    }
}
