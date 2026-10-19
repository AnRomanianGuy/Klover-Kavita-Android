package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate

@Serializable
data class DayStat(val seconds: Long = 0, val pages: Int = 0, val chapters: Int = 0)

class ReadingStats(
    storage: AccountStorage,
    scope: CoroutineScope,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    // One set of days per account: signing in as someone else shows their statistics, and streaks, instead.
    private val store = AccountStore(
        storage,
        "stats.json",
        MapSerializer(String.serializer(), DayStat.serializer()),
        emptyMap(),
        scope,
    )
    val days: StateFlow<Map<String, DayStat>> = store.data

    fun addSeconds(seconds: Long) {
        if (seconds > 0) update { copy(seconds = this.seconds + seconds) }
    }

    fun addPages(pages: Int) {
        if (pages > 0) update { copy(pages = this.pages + pages) }
    }

    fun addChapter() = update { copy(chapters = chapters + 1) }

    fun reset() {
        store.set(emptyMap())
    }

    // the reader reports as the app goes to the background, when the process is likeliest to be stopped, so don't
    // wait for the save
    fun flush() = store.flush()

    private fun update(block: DayStat.() -> DayStat) {
        val key = today().toString()
        store.update { map ->
            val trimmed = if (map.size > KEEP_DAYS) {
                map.entries.sortedBy { it.key }.takeLast(KEEP_DAYS).associate { it.key to it.value }
            } else {
                map
            }
            trimmed + (key to (trimmed[key] ?: DayStat()).block())
        }
    }

    private companion object {
        const val KEEP_DAYS = 400
    }
}

const val ACTIVE_DAY_SECONDS = 60L

data class DayBar(val date: LocalDate, val seconds: Long)

data class StatsSummary(
    val todaySeconds: Long = 0,
    val weekSeconds: Long = 0,
    val monthSeconds: Long = 0,
    val totalSeconds: Long = 0,
    val todayPages: Int = 0,
    val weekPages: Int = 0,
    val totalPages: Int = 0,
    val totalChapters: Int = 0,
    val activeDays: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val last7: List<DayBar> = emptyList(),
    val averageSecondsPerActiveDay: Long = 0,
) {
    val hasData: Boolean get() = totalSeconds > 0 || totalPages > 0
}

fun summarize(days: Map<String, DayStat>, today: LocalDate): StatsSummary {
    val byDate = days.mapNotNull { (key, stat) -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to stat } }.toMap()
    fun secondsIn(from: LocalDate, to: LocalDate) = byDate.filterKeys { it in from..to }.values.sumOf { it.seconds }
    fun pagesIn(from: LocalDate, to: LocalDate) = byDate.filterKeys { it in from..to }.values.sumOf { it.pages }

    val active = byDate.filterValues { it.seconds >= ACTIVE_DAY_SECONDS }.keys
    val total = byDate.values.sumOf { it.seconds }

    // Current streak: consecutive reading days ending today - or yesterday, so the streak is not lost until the day
    // is over.
    var streak = 0
    var day = if (today in active) today else today.minusDays(1)
    while (day in active) {
        streak++
        day = day.minusDays(1)
    }

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (date in active.sorted()) {
        run = if (previous != null && date == previous.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        previous = date
    }

    return StatsSummary(
        todaySeconds = byDate[today]?.seconds ?: 0,
        weekSeconds = secondsIn(today.minusDays(6), today),
        monthSeconds = secondsIn(today.minusDays(29), today),
        totalSeconds = total,
        todayPages = byDate[today]?.pages ?: 0,
        weekPages = pagesIn(today.minusDays(6), today),
        totalPages = byDate.values.sumOf { it.pages },
        totalChapters = byDate.values.sumOf { it.chapters },
        activeDays = active.size,
        currentStreak = streak,
        bestStreak = best,
        last7 = (6 downTo 0).map { back -> today.minusDays(back.toLong()).let { DayBar(it, byDate[it]?.seconds ?: 0) } },
        averageSecondsPerActiveDay = if (active.isEmpty()) 0 else total / active.size,
    )
}

fun durationParts(seconds: Long): Pair<Int, Int> {
    val minutes = (seconds / 60).toInt()
    return minutes / 60 to minutes % 60
}
