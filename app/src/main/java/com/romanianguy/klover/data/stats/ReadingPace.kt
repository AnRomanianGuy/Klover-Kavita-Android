package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

enum class PaceKind(
    val key: String,
    val defaultSecondsPerUnit: Float,
    val minSecondsPerUnit: Float,
    val maxSecondsPerUnit: Float,
    val minUnits: Float,
    val finishUnits: Float,
) {
    Manga("manga", 8f, 1.5f, 120f, 1f, 0.75f),

    Pages("pages", 15f, 2f, 180f, 1f, 0.75f),

    Strips("strips", 9f, 1f, 150f, 1f, 0.75f),

    Pdf("pdf", 45f, 5f, 400f, 1f, 0.75f),

    // 0.05 s a character is about 250 words a minute
    Text("text", 0.05f, 0.015f, 0.3f, 60f, 2500f),

    Novel("novel", 0.042f, 0.012f, 0.25f, 60f, 2500f),
}

@Serializable
data class PaceSample(
    val seconds: Float = 0f,
    val units: Float = 0f,
    val count: Int = 0,
    val at: Long = 0,
    val odd: Int = 0,
    val fastSeconds: Float = 0f,
    val fastUnits: Float = 0f,
)

private const val LONG_DECAY = 0.95f
private const val FAST_DECAY = 0.7f
private const val TRUSTED_COUNT = 2
private const val MAX_COUNT = 1_000

private const val DEVIATION_LOW = 0.15f
private const val DEVIATION_HIGH = 0.5f

fun effectivePace(sample: PaceSample): Float {
    val long = sample.seconds / sample.units
    if (sample.fastUnits <= 0f) return long
    val fast = sample.fastSeconds / sample.fastUnits
    val shift = ((abs(fast - long) / long - DEVIATION_LOW) / (DEVIATION_HIGH - DEVIATION_LOW)).coerceIn(0f, 1f)
    return long * (1 - shift) + fast * shift
}

private const val SLOW_LIMIT = 2.5f
private const val FAST_LIMIT = 0.4f
private const val READINGS_BEFORE_LIMIT = 3

private const val HARD_SLOW_LIMIT = 6f
private const val HARD_FAST_LIMIT = 0.2f

private const val TRUSTED_SERIES_COUNT = 3

private const val MAX_SESSION_WEIGHT = 0.85f
private const val TRUSTED_SESSION_COUNT = 3

fun addPace(
    kind: PaceKind,
    sample: PaceSample?,
    seconds: Float,
    units: Float,
    now: Long = 0,
    confirmed: Boolean = false,
): PaceSample? {
    if (units < kind.minUnits || seconds <= 0f) return sample
    val perUnit = seconds / units
    if (perUnit < kind.minSecondsPerUnit || perUnit > kind.maxSecondsPerUnit) return sample
    val old = sample ?: PaceSample()
    var taken = seconds
    var odd = 0
    if (old.count >= READINGS_BEFORE_LIMIT && old.units > 0f) {
        val usual = old.seconds / old.units
        val direction = when {
            perUnit > usual * SLOW_LIMIT -> 1
            perUnit < usual * FAST_LIMIT -> -1
            else -> 0
        }
        if (direction != 0 && direction != old.odd && !confirmed) {
            // the first outlier is capped
            taken = (if (direction > 0) usual * SLOW_LIMIT else usual * FAST_LIMIT) * units
            odd = direction
        } else if (direction != 0) {
            // a second one the same way, or an active reader, is believed (still capped)
            taken = seconds.coerceIn(usual * HARD_FAST_LIMIT * units, usual * HARD_SLOW_LIMIT * units)
        }
    }
    return PaceSample(
        seconds = old.seconds * LONG_DECAY + taken,
        units = old.units * LONG_DECAY + units,
        count = min(MAX_COUNT, old.count + 1),
        at = now,
        odd = odd,
        fastSeconds = old.fastSeconds * FAST_DECAY + taken,
        fastUnits = old.fastUnits * FAST_DECAY + units,
    )
}

private fun blend(prior: Float, sample: PaceSample?, trusted: Int, maxWeight: Float = 1f): Float {
    if (sample == null || sample.units <= 0f || sample.count <= 0) return prior
    val weight = min(maxWeight, maxWeight * sample.count / trusted)
    return prior * (1 - weight) + effectivePace(sample) * weight
}

fun estimatePace(kind: PaceKind, global: PaceSample?, series: PaceSample?, session: PaceSample?): Float {
    val usual = secondsPerUnit(kind, global)
    return blend(blend(usual, series, TRUSTED_SERIES_COUNT), session, TRUSTED_SESSION_COUNT, MAX_SESSION_WEIGHT)
}

fun paceKey(kind: PaceKind, seriesId: Int): String = "${kind.key}#$seriesId"

private const val MAX_SERIES_ENTRIES = 60

fun secondsPerUnit(kind: PaceKind, sample: PaceSample?): Float {
    if (sample == null || sample.units <= 0f || sample.count <= 0) return kind.defaultSecondsPerUnit
    val measured = effectivePace(sample)
    val weight = min(1f, sample.count / TRUSTED_COUNT.toFloat())
    return kind.defaultSecondsPerUnit * (1 - weight) + measured * weight
}

fun chapterPace(
    startUnits: Float,
    endUnits: Float,
    startProgress: Float,
    seconds: Float,
    usualSecondsPerUnit: Float?,
): Pair<Float, Float>? {
    val pages = startUnits - endUnits
    if (startUnits <= 0f || startProgress > 0.2f || pages < 4f || pages < 0.7f * startUnits || seconds <= 0f) return null
    val perPage = seconds / pages
    if (usualSecondsPerUnit != null && (perPage > 2.5f * usualSecondsPerUnit || perPage < 0.4f * usualSecondsPerUnit)) return null
    return seconds to pages
}

fun secondsLeft(unitsLeft: Float, secondsPerUnit: Float): Long = (unitsLeft.coerceAtLeast(0f) * secondsPerUnit).roundToLong()

const val CHARS_PER_WORD = 6f

private const val SECTIONS_TO_TRUST = 3f

// blends the rest of the sections with the whole book by word count; exact when every size is known
fun bookCharsLeft(textLeftInSection: Int, section: Int, sections: Int, seen: Map<Int, Int>, words: Long): Float? {
    if (sections <= 1) return textLeftInSection.toFloat()
    val fromWords = if (words > 0) words * CHARS_PER_WORD / sections else null
    val fromSeen = if (seen.isNotEmpty()) seen.values.average().toFloat() else null
    val average = when {
        // once a few sections are seen they say more than the word count
        fromWords != null && fromSeen != null -> {
            val trust = min(1f, seen.size / SECTIONS_TO_TRUST)
            fromWords * (1 - trust) + fromSeen * trust
        }
        fromWords != null -> fromWords
        fromSeen != null -> fromSeen
        else -> return null
    }
    var byAverage = textLeftInSection.toFloat()
    for (s in section + 1 until sections) byAverage += seen[s]?.toFloat() ?: average
    // every size is known, nothing left to estimate
    if ((0 until sections).all { it in seen }) return byAverage
    if (words <= 0) return byAverage

    val total = words * CHARS_PER_WORD
    val current = seen[section] ?: textLeftInSection
    var before = 0f
    for (s in 0 until section) before += seen[s]?.toFloat() ?: average
    val read = before + (current - textLeftInSection).coerceAtLeast(0)
    val byTotal = (total - read).coerceAtLeast(textLeftInSection.toFloat())
    var weight = (1f - read / total / TOTAL_FADES_BY).coerceIn(0f, 1f)
    if (seen.size >= SECTIONS_TO_TRUST && fromSeen != null) {
        val disagreement = abs(fromSeen * sections / total - 1f)
        weight *= (1f - (disagreement - AGREEMENT_SLACK) / AGREEMENT_RANGE).coerceIn(0f, 1f)
    }
    return byTotal * weight + byAverage * (1 - weight)
}

private const val TOTAL_FADES_BY = 0.5f

private const val AGREEMENT_SLACK = 0.1f
private const val AGREEMENT_RANGE = 0.3f

sealed interface LeftText {
    data object UnderAMinute : LeftText
    data class Minutes(val minutes: Int) : LeftText
    data class HoursMinutes(val hours: Int, val minutes: Int) : LeftText
}

fun leftText(seconds: Long): LeftText {
    if (seconds < 30) return LeftText.UnderAMinute
    val minutes = ((seconds + 30) / 60).toInt()
    return if (minutes >= 60) LeftText.HoursMinutes(minutes / 60, minutes % 60) else LeftText.Minutes(minutes)
}

data class StreakStatus(val days: Int, val todayDone: Boolean, val secondsToKeep: Long) {
    val atRisk: Boolean get() = days > 0 && !todayDone
}

fun streakStatus(days: Map<String, DayStat>, today: LocalDate, liveSeconds: Long): StreakStatus {
    val key = today.toString()
    val stored = days[key] ?: DayStat()
    val todaySeconds = stored.seconds + liveSeconds.coerceAtLeast(0)
    val summary = summarize(days + (key to stored.copy(seconds = todaySeconds)), today)
    val done = todaySeconds >= ACTIVE_DAY_SECONDS
    return StreakStatus(summary.currentStreak, done, if (done) 0 else ACTIVE_DAY_SECONDS - todaySeconds)
}

class ReadingPace(
    storage: AccountStorage,
    scope: CoroutineScope,
) {
    private val store = AccountStore(
        storage,
        "pace.json",
        MapSerializer(String.serializer(), PaceSample.serializer()),
        emptyMap(),
        scope,
    )
    val samples: StateFlow<Map<String, PaceSample>> = store.data

    fun record(kind: PaceKind, seconds: Float, units: Float, seriesId: Int? = null, confirmed: Boolean = false) {
        val now = System.currentTimeMillis() / 1000
        store.update { map ->
            var out = map
            val all = addPace(kind, map[kind.key], seconds, units, now = now, confirmed = confirmed)
            if (all != null && all !== map[kind.key]) out = out + (kind.key to all)
            if (seriesId != null) {
                val key = paceKey(kind, seriesId)
                val one = addPace(kind, map[key], seconds, units, now = now, confirmed = confirmed)
                if (one != null && one !== map[key]) out = out + (key to one)
            }
            prune(out)
        }
    }

    private fun prune(map: Map<String, PaceSample>): Map<String, PaceSample> {
        val series = map.filterKeys { '#' in it }
        if (series.size <= MAX_SERIES_ENTRIES) return map
        val drop = series.entries.sortedBy { it.value.at }.take(series.size - MAX_SERIES_ENTRIES).map { it.key }.toSet()
        return map.filterKeys { it !in drop }
    }

    fun reset() {
        store.set(emptyMap())
    }

    fun flush() = store.flush()
}
