package com.romanianguy.klover.data.stats

import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

// plays made-up readers through made-up books with the reader's own time-left code and prints how far off it is.
// it shows how the method behaves, not how it will do for you
class BookAccuracySimulation {
    private class Reader(val name: String, val secondsPerChar: Float, val spread: Float, val pause: Float)

    private class Counts(val name: String, val factor: Float?)

    private fun gauss(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }

    private fun logNormal(rnd: Random, median: Float, spread: Float) = median * exp((gauss(rnd) * spread).toDouble()).toFloat()

    private class Score(val median: Float, val within25: Float, val within40: Float)

    private fun evaluate(
        r: Reader,
        counts: Counts,
        history: Boolean,
        charsPerScreen: Int,
        early: Boolean = false,
        runs: Int = 300,
    ): Score {
        val rnd = Random(21)
        val errors = ArrayList<Float>()
        repeat(runs) {
            // A book of 18 sections of very different length (a few short ones: title page, notes).
            val sections = 18
            val lengths = List(sections) { i -> (if (i == 0 || i == sections - 1) logNormal(rnd, 2500f, 0.5f) else logNormal(rnd, 16000f, 0.55f)).toInt().coerceAtLeast(400) }
            val total = lengths.sum()
            val words = counts.factor?.let { (total / 5.8f * it).toLong() } ?: 0L

            var global: PaceSample? = null
            if (history) repeat(40) { global = addPace(PaceKind.Text, global, 1000f * r.secondsPerChar * (0.7f + rnd.nextFloat() * 0.6f), 1000f) }
            var session: PaceSample? = null
            val seen = HashMap<Int, Int>()

            // The reading, screen by screen: how long each took, in order.
            class Screen(
                val section: Int,
                val index: Int,
                val chars: Int,
                val seconds: Float,
                val charsLeftInSection: Int,
            )
            val screens = ArrayList<Screen>()
            for (s in 0 until sections) {
                var left = lengths[s]
                var index = 0
                while (left > 0) {
                    val chars = min(charsPerScreen, left)
                    var t = chars * logNormal(rnd, r.secondsPerChar, r.spread)
                    if (rnd.nextFloat() < r.pause) t += 40f + rnd.nextFloat() * 140f
                    screens += Screen(s, index, chars, t.coerceAtMost(300f), left)
                    left -= chars
                    index++
                }
            }
            val totalScreens = screens.size
            val checks = if (early) setOf(totalScreens / 20, totalScreens / 10, totalScreens / 5) else setOf(
                totalScreens / 4,
                totalScreens / 2,
                totalScreens * 3 / 4,
            )
            for ((i, screen) in screens.withIndex()) {
                seen[screen.section] = lengths[screen.section]
                val isLastOfSection = i + 1 >= screens.size || screens[i + 1].section != screen.section
                // The reader only learns from a screen when it turns to the next one in the same section.
                if (!isLastOfSection) {
                    session = addPace(PaceKind.Text, session, screen.seconds, screen.chars.toFloat())
                    global = addPace(PaceKind.Text, global, screen.seconds, screen.chars.toFloat())
                }
                if (i + 1 in checks && i + 1 < totalScreens) {
                    val next = screens[i + 1]
                    val charsLeft = bookCharsLeft(
                        next.charsLeftInSection,
                        next.section,
                        sections,
                        seen.toMap().let { it + (next.section to lengths[next.section]) },
                        words,
                    )
                    if (charsLeft != null) {
                        val predicted = estimatePace(PaceKind.Text, global, null, session) * charsLeft
                        val actual = screens.drop(i + 1).sumOf { it.seconds.toDouble() }.toFloat()
                        errors += abs(predicted - actual) / actual
                    }
                }
            }
        }
        errors.sort()
        return Score(
            errors[errors.size / 2],
            errors.count { it <= 0.25f } / errors.size.toFloat(),
            errors.count { it <= 0.40f } / errors.size.toFloat(),
        )
    }

    @Test
    fun printBookAccuracy() {
        val readers = listOf(
            Reader("average, 250 wpm", 0.048f, 0.30f, 0.01f),
            Reader("slow, 130 wpm", 0.092f, 0.30f, 0.01f),
            Reader("fast, 400 wpm", 0.030f, 0.30f, 0.01f),
            Reader("erratic, 250 wpm", 0.048f, 0.65f, 0.06f),
        )
        val counts = listOf(
            Counts("count right to 5%", 1.0f),
            Counts("count 40% too low", 0.6f),
            Counts("no count (offline)", null),
        )
        val out = StringBuilder("\nBOOK TIME LEFT (18 sections, ~1000 chars a screen, 300 runs each; error = |predicted - actual| / actual)\n")
        out.append("%-20s %-20s %-8s %9s %9s %9s\n".format("reader", "server word count", "history", "median", "in25%", "in40%"))
        var sumMedian = 0f; var sum25 = 0f; var n = 0
        for (r in readers) for (c in counts) for (history in listOf(false, true)) {
            val s = evaluate(r, c, history, 1000)
            out.append(
                "%-20s %-20s %-8s %8.0f%% %8.0f%% %8.0f%%\n".format(
                    r.name,
                    c.name,
                    if (history) "yes" else "none",
                    s.median * 100,
                    s.within25 * 100,
                    s.within40 * 100,
                ),
            )
            if (!history && c.factor == 1.0f) { sumMedian += s.median; sum25 += s.within25; n++ }
        }
        out.append("\nEARLY IN THE BOOK (checked at 5%, 10% and 20% of the way)\n")
        out.append("%-20s %-20s %9s %9s %9s\n".format("reader", "server word count", "median", "in25%", "in40%"))
        for (c in counts) {
            var m = 0f; var w25 = 0f; var w40 = 0f
            for (r in readers) { val sc = evaluate(r, c, false, 1000, early = true); m += sc.median; w25 += sc.within25; w40 += sc.within40 }
            out.append(
                "%-20s %-20s %8.0f%% %8.0f%% %8.0f%%\n".format(
                    "(avg of 4 readers)",
                    c.name,
                    m / readers.size * 100,
                    w25 / readers.size * 100,
                    w40 / readers.size * 100,
                ),
            )
        }
        out.append(
            "AVERAGE (right word count, no history): median err %.1f%%, within 25%% %.1f%%\n".format(sumMedian / n * 100, sum25 / n * 100),
        )
        println(out)
    }
}
