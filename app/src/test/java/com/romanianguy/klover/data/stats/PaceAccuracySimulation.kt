package com.romanianguy.klover.data.stats

import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

// not a pass/fail test: plays made-up readers through the pace code and prints how far off "time left" is
class PaceAccuracySimulation {
    private class Reader(
        val name: String,
        val meanSeconds: Float,
        val spread: Float,
        val pauseChance: Float,
        val drift: Float,
    )

    private fun chapter(r: Reader, pages: Int, rnd: Random): Pair<List<Float>, List<Boolean>> {
        val times = ArrayList<Float>()
        val paused = ArrayList<Boolean>()
        var mean = r.meanSeconds
        repeat(pages) {
            mean *= 1f + (rnd.nextFloat() - 0.5f) * r.drift
            val noise = exp((gauss(rnd) * r.spread).toDouble()).toFloat()
            var t = mean * noise
            val pause = rnd.nextFloat() < r.pauseChance
            if (pause) t += 40f + rnd.nextFloat() * 140f
            times += t.coerceIn(2.5f, 175f)
            paused += pause
        }
        return times to paused
    }

    private fun gauss(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f); val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }

    private fun run(r: Reader, history: Boolean, runs: Int = 400, pages: Int = 40): Triple<Float, Float, Float> {
        val errors = ArrayList<Float>()
        val rnd = Random(7)
        repeat(runs) {
            var global: PaceSample? = null
            if (history) repeat(30) { global = addPace(PaceKind.Pages, global, r.meanSeconds * (0.7f + rnd.nextFloat() * 0.6f), 1f) }
            var session: PaceSample? = null
            val (times, _) = chapter(r, pages, rnd)
            for (page in 0 until pages) {
                // the reader has just finished page `page`; the app learns from how long it took
                session = addPace(PaceKind.Pages, session, times[page], 1f)
                global = addPace(PaceKind.Pages, global, times[page], 1f)
                val left = pages - 1 - page
                if (page + 1 in setOf(pages / 4, pages / 2, pages * 3 / 4) && left > 0) {
                    val predicted = estimatePace(PaceKind.Pages, global, null, session) * (left + 0.5f)
                    val actual = times.drop(page + 1).sum()
                    errors += abs(predicted - actual) / actual
                }
            }
        }
        errors.sort()
        val within25 = errors.count { it <= 0.25f } / errors.size.toFloat()
        val within40 = errors.count { it <= 0.40f } / errors.size.toFloat()
        return Triple(errors[errors.size / 2], within25, within40)
    }

    @Test
    fun printAccuracy() {
        val readers = listOf(
            Reader("steady, 10 s a page", 10f, 0.25f, 0.00f, 0.00f),
            Reader("ordinary, 15 s a page", 15f, 0.45f, 0.03f, 0.02f),
            Reader("slow and careful, 45 s a page", 45f, 0.40f, 0.03f, 0.02f),
            Reader("erratic, 20 s a page", 20f, 0.75f, 0.08f, 0.06f),
            Reader("speeds up and slows down", 25f, 0.45f, 0.04f, 0.18f),
        )
        val out = StringBuilder("\nPACE ACCURACY (40-page chapter, 400 runs each; error = |predicted - actual| / actual)\n")
        out.append("reader                           history   median err   within 25%   within 40%\n")
        for (r in readers) for (history in listOf(false, true)) {
            val (median, w25, w40) = run(r, history)
            out.append(
                "%-32s %-8s  %8.0f%%   %9.0f%%   %9.0f%%\n".format(
                    r.name,
                    if (history) "yes" else "none",
                    median * 100,
                    w25 * 100,
                    w40 * 100,
                ),
            )
        }
        var sumMedian = 0f; var sum25 = 0f; var n = 0
        for (r in readers) { val (m, w25, _) = run(r, false); sumMedian += m; sum25 += w25; n++ }
        out.append("AVERAGE (no history): median err %.1f%%, within 25%% %.1f%%\n".format(sumMedian / n * 100, sum25 / n * 100))
        println(out)
    }
}
