package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// how accurate the time-left estimate has been: per content kind, a histogram of actual/predicted
// ratios that fades old outcomes. It corrects a biased estimate and gives the shown range

@Serializable
data class Check(val kind: String, val predicted: Int, val at: Int, val left: Int = 0, val done: Boolean = false)

@Serializable
data class WordInfo(val count: Int, val learned: Boolean = false)

@Serializable
data class ChapterTrack(val spent: Int = 0, val checks: List<Check> = emptyList(), val taken: Int = 0)

@Serializable
data class Outcomes(val n: Int = 0, val bins: List<Int> = emptyList())

@Serializable
data class AccuracyData(
    val outcomes: Map<String, Outcomes> = emptyMap(),
    val pending: Map<Int, ChapterTrack> = emptyMap(),
    val words: Map<Int, WordInfo> = emptyMap(),
    val wordFactor: Float = 1f,
    val wordFactorN: Int = 0,
)

data class Calibration(val correction: Float = 1f, val band: Float? = null, val samples: Int = 0) {
    fun bandAt(progress: Float): Float {
        val left = (1f - progress.coerceIn(0f, 0.97f)) / (1f - REFERENCE_PROGRESS)
        return (band ?: DEFAULT_BAND) * sqrt(left.coerceIn(0.05f, 1.3f))
    }

    fun accurateAt(progress: Float): Boolean = bandAt(progress) <= ACCURATE_BAND

    val accurate: Boolean get() = accurateAt(REFERENCE_PROGRESS)

    fun range(seconds: Long, progress: Float = REFERENCE_PROGRESS): LongRange {
        val b = bandAt(progress)
        return (seconds * (1 - b)).toLong()..(seconds * (1 + b)).toLong()
    }
}

const val ACCURATE_BAND = 0.25f

const val REFERENCE_PROGRESS = 0.35f

const val DEFAULT_BAND = 0.4f

private const val BINS = 32
private const val LOG_MIN = -2f
private const val LOG_STEP = 0.125f

private const val FADE = 0.97f
private const val WEIGHT = 100

const val MIN_FOR_BAND = 6

private const val SHRINK = 10f
private const val NO_CORRECTION_BELOW = 0.07f
private const val MAX_PENDING = 6
private const val MAX_WORDS = 12

private const val MIN_SPAN = 0.2f

val CHECK_SPOTS = floatArrayOf(0.15f, 0.40f, 0.65f)

const val CHECK_SPOT_SLACK = 0.3f

fun outcomeRatio(actualSeconds: Float, predictedSeconds: Float): Float? =
    if (actualSeconds <= 0f || predictedSeconds <= 0f) null else (actualSeconds / predictedSeconds).coerceIn(0.25f, 4f)

private fun binOf(ratio: Float): Int = ((ln(ratio) / ln(2f) - LOG_MIN) / LOG_STEP).toInt().coerceIn(0, BINS - 1)

private fun centre(bin: Int): Float = 2f.pow(LOG_MIN + (bin + 0.5f) * LOG_STEP)

fun addOutcome(outcomes: Outcomes?, ratio: Float): Outcomes {
    val old = outcomes?.bins?.takeIf { it.size == BINS } ?: List(BINS) { 0 }
    val bin = binOf(ratio)
    val bins = List(BINS) { i -> (old[i] * FADE).roundToInt() + if (i == bin) WEIGHT else 0 }
    return Outcomes(n = ((outcomes?.n ?: 0) + 1).coerceAtMost(100_000), bins = bins)
}

private fun total(bins: List<Int>) = bins.sum().toFloat()

private fun typicalRatio(bins: List<Int>): Float {
    val half = total(bins) / 2f
    var seen = 0f
    for (i in bins.indices) {
        seen += bins[i]
        if (seen >= half) return centre(i)
    }
    return 1f
}

fun correction(outcomes: Outcomes?): Float {
    if (outcomes == null || outcomes.n == 0 || outcomes.bins.size != BINS) return 1f
    val typical = typicalRatio(outcomes.bins)
    // close to 1 is just noise, only correct a clear lean
    if (abs(typical - 1f) < NO_CORRECTION_BELOW) return 1f
    val trust = outcomes.n / (outcomes.n + SHRINK)
    return (1f + (typical - 1f) * trust).coerceIn(0.8f, 1.5f)
}

fun band(outcomes: Outcomes?): Float? {
    if (outcomes == null || outcomes.n < MIN_FOR_BAND || outcomes.bins.size != BINS) return null
    val c = correction(outcomes)
    val errors = outcomes.bins.indices
        .filter { outcomes.bins[it] > 0 }
        .map { abs(centre(it) / c - 1f) to outcomes.bins[it] }
        .sortedBy { it.first }
    val target = total(outcomes.bins) * 0.75f
    var seen = 0f
    for ((error, weight) in errors) {
        seen += weight
        if (seen >= target) return error.coerceIn(0.08f, 0.6f)
    }
    return 0.6f
}

fun calibrate(outcomes: Outcomes?): Calibration = Calibration(correction(outcomes), band(outcomes), outcomes?.n ?: 0)

enum class Steadiness { Steady, Regular, Varied, Erratic }

fun steadinessOf(band: Float): Steadiness = when {
    band <= 0.18f -> Steadiness.Steady
    band <= 0.28f -> Steadiness.Regular
    band <= 0.40f -> Steadiness.Varied
    else -> Steadiness.Erratic
}

enum class Speed { Fast, Average, Slow }

fun speedOf(kind: PaceKind, pace: PaceSample?): Speed {
    if (pace == null || pace.units <= 0f || pace.count < 3) return Speed.Average
    val ratio = effectivePace(pace) / kind.defaultSecondsPerUnit
    return when {
        ratio < 0.75f -> Speed.Fast
        ratio > 1.35f -> Speed.Slow
        else -> Speed.Average
    }
}

data class Profile(val accuracyPercent: Int, val steadiness: Steadiness, val speed: Speed, val samples: Int)

fun profile(kind: PaceKind, outcomes: Outcomes?, pace: PaceSample?): Profile? {
    val b = band(outcomes) ?: return null
    return Profile(((1f - b) * 100f).roundToInt(), steadinessOf(b), speedOf(kind, pace), outcomes?.n ?: 0)
}

fun overallProfile(outcomes: Map<String, Outcomes>): Pair<Int, Steadiness>? {
    var weight = 0f
    var sum = 0f
    for (kind in PaceKind.entries) {
        val o = outcomes[kind.key] ?: continue
        val b = band(o) ?: continue
        weight += o.n
        sum += b * o.n
    }
    if (weight == 0f) return null
    val b = sum / weight
    return ((1f - b) * 100f).roundToInt() to steadinessOf(b)
}

fun withSpent(data: AccuracyData, chapterId: Int, seconds: Int): AccuracyData {
    val track = data.pending[chapterId] ?: ChapterTrack()
    return touch(data, chapterId, track.copy(spent = track.spent + seconds))
}

fun withCheck(
    data: AccuracyData,
    chapterId: Int,
    kind: String,
    predictedSeconds: Int,
    spentNow: Int,
    spot: Int,
    unitsLeft: Int = 0,
): AccuracyData {
    val track = data.pending[chapterId] ?: ChapterTrack()
    val bit = 1 shl spot
    if (track.taken and bit != 0 || predictedSeconds <= 0) return data
    var outcomes = data.outcomes
    val earlier = track.checks.map { c ->
        if (c.done || c.left <= 0) return@map c
        val read = c.left - unitsLeft
        if (read < c.left * MIN_SPAN) return@map c
        val predictedForThat = c.predicted * (read.toFloat() / c.left)
        val ratio = outcomeRatio((spentNow - c.at).toFloat(), predictedForThat) ?: return@map c
        outcomes = outcomes + (c.kind to addOutcome(outcomes[c.kind], ratio))
        c.copy(done = true)
    }
    val next = track.copy(
        taken = track.taken or bit,
        checks = earlier + Check(kind, predictedSeconds, spentNow, unitsLeft),
    )
    return touch(data.copy(outcomes = outcomes), chapterId, next)
}

fun withoutChecks(data: AccuracyData, chapterId: Int): AccuracyData {
    val track = data.pending[chapterId] ?: return data
    if (track.checks.isEmpty()) return data
    return touch(data, chapterId, track.copy(checks = emptyList()))
}

fun withFinished(data: AccuracyData, chapterId: Int, spentNow: Int): AccuracyData {
    val track = data.pending[chapterId] ?: return data
    var outcomes = data.outcomes
    for (check in track.checks) {
        if (check.done) continue
        val ratio = outcomeRatio((spentNow - check.at).toFloat(), check.predicted.toFloat()) ?: continue
        outcomes = outcomes + (check.kind to addOutcome(outcomes[check.kind], ratio))
    }
    return data.copy(outcomes = outcomes, pending = data.pending - chapterId)
}

fun withWords(data: AccuracyData, chapterId: Int, count: Int): AccuracyData {
    if (count <= 0) return data
    val words = LinkedHashMap(data.words)
    val old = words.remove(chapterId)
    words[chapterId] = WordInfo(count, old?.learned ?: false)
    while (words.size > MAX_WORDS) words.remove(words.keys.first())
    return data.copy(words = words)
}

// once per book
fun withLearnedFactor(data: AccuracyData, chapterId: Int, observed: Float): AccuracyData {
    val info = data.words[chapterId] ?: return data
    if (info.learned) return data
    val weight = maxOf(0.25f, 1f / (data.wordFactorN + 2))
    val factor = data.wordFactor * (1 - weight) + observed.coerceIn(0.4f, 2.5f) * weight
    return data.copy(
        words = data.words + (chapterId to info.copy(learned = true)),
        wordFactor = factor,
        wordFactorN = (data.wordFactorN + 1).coerceAtMost(1_000),
    )
}

const val MIN_SECTIONS_TO_LEARN = 5

fun observedWordFactor(sizes: Collection<Int>, sections: Int, serverWords: Long): Float? {
    if (serverWords <= 0 || sections <= 0 || sizes.size < MIN_SECTIONS_TO_LEARN) return null
    return sizes.average().toFloat() * sections / (serverWords * CHARS_PER_WORD)
}

private fun touch(data: AccuracyData, chapterId: Int, track: ChapterTrack): AccuracyData {
    val pending = LinkedHashMap(data.pending)
    pending.remove(chapterId)
    pending[chapterId] = track
    while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
    return data.copy(pending = pending)
}

class ReadingAccuracy(
    storage: AccountStorage,
    scope: CoroutineScope,
) {
    private val store = AccountStore(storage, "accuracy.json", AccuracyData.serializer(), AccuracyData(), scope)
    val data: StateFlow<AccuracyData> = store.data

    fun spent(chapterId: Int): Int = store.data.value.pending[chapterId]?.spent ?: 0

    fun addSpent(chapterId: Int, seconds: Int) {
        if (seconds > 0) store.update { withSpent(it, chapterId, seconds) }
    }

    fun check(chapterId: Int, kind: String, predictedSeconds: Int, spentNow: Int, spot: Int, unitsLeft: Int) {
        store.update { withCheck(it, chapterId, kind, predictedSeconds, spentNow, spot, unitsLeft) }
    }

    fun dropChecks(chapterId: Int) {
        store.update { withoutChecks(it, chapterId) }
    }

    fun words(chapterId: Int): Int? = store.data.value.words[chapterId]?.count

    fun setWords(chapterId: Int, count: Int) {
        store.update { withWords(it, chapterId, count) }
    }

    fun wordFactor(): Float = store.data.value.wordFactor

    fun learnWordFactor(chapterId: Int, observed: Float) {
        store.update { withLearnedFactor(it, chapterId, observed) }
    }

    fun finish(chapterId: Int, spentNow: Int) {
        store.update { withFinished(it, chapterId, spentNow) }
    }

    fun taken(chapterId: Int): Int = store.data.value.pending[chapterId]?.taken ?: 0

    fun flush() = store.flush()

    fun reset() {
        store.set(AccuracyData())
    }
}
