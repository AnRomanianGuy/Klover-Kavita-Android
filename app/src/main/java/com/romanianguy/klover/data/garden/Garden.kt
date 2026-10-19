package com.romanianguy.klover.data.garden

import com.romanianguy.klover.data.history.HistoryEntry
import com.romanianguy.klover.data.stats.StatsSummary
import java.time.Instant
import java.time.ZoneId

// chance of a four- and a five-leaf clover
const val CHANCE_FOUR = 0.07
const val CHANCE_FIVE = 0.012

// a four-leaf is certain after this many chapters without one, a five-leaf likewise
const val FOUR_LEAF_GUARANTEE = 15
const val FIVE_LEAF_GUARANTEE = 55

// 0..1, fixed by the entry: same chapter and moment give the same
fun luck(entry: HistoryEntry): Double {
    // SplitMix64 of the chapter and the time: well spread, and no state to keep.
    var z = (entry.chapterId.toLong() shl 32) xor entry.at xor 0x9E3779B97F4A7C15uL.toLong()
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    z = z xor (z ushr 31)
    return (z ushr 11).toDouble() / (1L shl 53).toDouble()
}

// mostly three leaves, sometimes four or five by roll; guaranteed after enough without
fun nextLeaves(sinceFour: Int, sinceFive: Int, roll: Double): Int = when {
    sinceFive + 1 >= FIVE_LEAF_GUARANTEE -> 5
    roll < CHANCE_FIVE -> 5
    sinceFour + 1 >= FOUR_LEAF_GUARANTEE -> 4
    roll < CHANCE_FIVE + CHANCE_FOUR -> 4
    else -> 3
}

data class Pity(val sinceFour: Int, val sinceFive: Int)

// share of the expected time that counts as really reading
const val MIN_READ_SHARE = 0.3f

// flicking to the last page takes a fraction of the time; nothing to judge by counts as read
fun wasReadProperly(spentSeconds: Float, expectedSeconds: Float?): Boolean {
    if (expectedSeconds == null || expectedSeconds <= 0f) return true
    return spentSeconds >= expectedSeconds * MIN_READ_SHARE
}

fun List<HistoryEntry>.counted(): List<HistoryEntry> = filter { it.read }

// skimmed chapters grow none
fun cloverLeaves(entries: List<HistoryEntry>): List<Int> {
    val out = ArrayList<Int>(entries.size)
    var sinceFour = 0
    var sinceFive = 0
    for (entry in entries.counted().sortedBy { it.at }) {
        val leaves = entry.leaves.takeIf { it in 3..5 } ?: nextLeaves(sinceFour, sinceFive, luck(entry))
        out += leaves
        sinceFour = if (leaves >= 4) 0 else sinceFour + 1
        sinceFive = if (leaves >= 5) 0 else sinceFive + 1
    }
    return out
}

fun pityOf(leaves: List<Int>): Pity {
    var sinceFour = 0
    var sinceFive = 0
    for (n in leaves) {
        sinceFour = if (n >= 4) 0 else sinceFour + 1
        sinceFive = if (n >= 5) 0 else sinceFive + 1
    }
    return Pity(sinceFour, sinceFive)
}

fun rollFor(entries: List<HistoryEntry>, entry: HistoryEntry): Int {
    val pity = pityOf(cloverLeaves(entries))
    return nextLeaves(pity.sinceFour, pity.sinceFive, luck(entry))
}

data class Garden(
    val clovers: Int,
    val threeLeaf: Int,
    val fourLeaf: Int,
    val fiveLeaf: Int,
    val untilFourGuaranteed: Int,
    val untilFiveGuaranteed: Int,
)

fun gardenOf(leaves: List<Int>): Garden {
    val pity = pityOf(leaves)
    return Garden(
        clovers = leaves.size,
        threeLeaf = leaves.count { it == 3 },
        fourLeaf = leaves.count { it == 4 },
        fiveLeaf = leaves.count { it == 5 },
        untilFourGuaranteed = (FOUR_LEAF_GUARANTEE - pity.sinceFour).coerceAtLeast(1),
        untilFiveGuaranteed = (FIVE_LEAF_GUARANTEE - pity.sinceFive).coerceAtLeast(1),
    )
}

data class BadgeFacts(
    val chapters: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val bestDaySeconds: Long,
    val totalSeconds: Long,
    val finishedAtNight: Boolean,
    val finishedEarly: Boolean,
    val goalMetWeeks: Int,
)

enum class Badge(val chapters: Int = 0) {
    FirstChapter(1),
    TenChapters(10),
    FiftyChapters(50),
    HundredChapters(100),
    Streak3,
    Streak7,
    Streak30,
    LongDay,
    TenHours,
    NightOwl,
    EarlyBird,
    GoalMet;

    fun earned(f: BadgeFacts): Boolean = when (this) {
        FirstChapter, TenChapters, FiftyChapters, HundredChapters -> f.chapters >= chapters
        Streak3 -> f.bestStreak >= 3
        Streak7 -> f.bestStreak >= 7
        Streak30 -> f.bestStreak >= 30
        LongDay -> f.bestDaySeconds >= 2 * 3600
        TenHours -> f.totalSeconds >= 10 * 3600
        NightOwl -> f.finishedAtNight
        EarlyBird -> f.finishedEarly
        GoalMet -> f.goalMetWeeks >= 1
    }
}

fun badgeFacts(
    history: List<HistoryEntry>,
    summary: StatsSummary,
    bestDaySeconds: Long,
    goalMetWeeks: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): BadgeFacts {
    val history = history.counted()
    val hours = history.map { Instant.ofEpochMilli(it.at).atZone(zone).hour }
    return BadgeFacts(
        chapters = history.size,
        currentStreak = summary.currentStreak,
        bestStreak = summary.bestStreak,
        bestDaySeconds = bestDaySeconds,
        totalSeconds = summary.totalSeconds,
        finishedAtNight = hours.any { it in 0..3 },
        finishedEarly = hours.any { it in 4..6 },
        goalMetWeeks = goalMetWeeks,
    )
}

fun newlyEarned(before: BadgeFacts, after: BadgeFacts): List<Badge> =
    Badge.entries.filter { !it.earned(before) && it.earned(after) }

fun goalProgress(weekSeconds: Long, goalMinutes: Int): Float =
    if (goalMinutes <= 0) 0f else (weekSeconds / 60f / goalMinutes).coerceIn(0f, 1f)

fun goalMinutesLeft(weekSeconds: Long, goalMinutes: Int): Int =
    if (goalMinutes <= 0) 0 else (goalMinutes - weekSeconds / 60).coerceAtLeast(0).toInt()

data class Recap(
    val minutes: Int,
    val chapters: Int,
    val bestDayMinutes: Int,
    val topSeries: String?,
    val activeDays: Int,
)

fun recapOf(
    history: List<HistoryEntry>,
    summary: StatsSummary,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Recap {
    val weekAgo = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    val thisWeek = history.counted().filter { it.at >= weekAgo }
    return Recap(
        minutes = (summary.weekSeconds / 60).toInt(),
        chapters = thisWeek.size,
        bestDayMinutes = (summary.last7.maxOfOrNull { it.seconds } ?: 0L).div(60).toInt(),
        topSeries = thisWeek.groupingBy { it.seriesName }.eachCount().filterKeys { it.isNotBlank() }.maxByOrNull { it.value }?.key,
        activeDays = summary.last7.count { it.seconds >= 60 },
    )
}
