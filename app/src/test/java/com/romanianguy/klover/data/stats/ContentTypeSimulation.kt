package com.romanianguy.klover.data.stats

import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

// one made-up reader on manga (about 7 s a page), comics (20 s) and a PDF (50 s) in turn: does a pace per content kind beat
// one pace for everything? Prints the error early in each chapter (at 15% and 40% read)
class ContentTypeSimulation {
    private class Kind(val kind: PaceKind, val mean: Float)

    private fun gauss(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }

    private fun run(separate: Boolean): Triple<Float, Float, Float> {
        val rnd = Random(77)
        val errors = ArrayList<Float>()
        val kinds = listOf(Kind(PaceKind.Manga, 7f), Kind(PaceKind.Pages, 20f), Kind(PaceKind.Pdf, 50f))
        repeat(150) {
            // What the reader has learnt, per content kind (or - the old way - for all page-like content together).
            val global = HashMap<String, PaceSample?>()
            for (chapter in 0 until 24) {
                val k = kinds[chapter % kinds.size]
                val key = if (separate) k.kind.key else "all"
                val spread = 0.4f
                val times = List(40) { (k.mean * exp((gauss(rnd) * spread).toDouble()).toFloat()).coerceIn(2.5f, 300f) }
                var session: PaceSample? = null
                for (page in 0 until 40) {
                    session = addPace(PaceKind.Pages, session, times[page], 1f)
                    val before = global[key]
                    global[key] = addPace(PaceKind.Pages, before, times[page], 1f)
                    if (chapter >= 6 && page + 1 in setOf(6, 16)) {
                        // The prior a chapter starts from is the kind's own starting guess when kinds are separate.
                        val prior = if (separate) k.kind else PaceKind.Pages
                        val estimate = estimatePace(prior, global[key], null, session) * (40 - 1 - page + 0.5f)
                        val actual = times.drop(page + 1).sum()
                        errors += abs(estimate - actual) / actual
                    }
                }
            }
        }
        errors.sort()
        return Triple(
            errors[errors.size / 2],
            errors.count { it <= 0.25f } / errors.size.toFloat(),
            errors.count { it <= 0.40f } / errors.size.toFloat(),
        )
    }

    @Test
    fun printContentTypeBenefit() {
        val (m1, a1, b1) = run(separate = false)
        val (m2, a2, b2) = run(separate = true)
        println(
            "\nCONTENT TYPES (manga 7 s, comics 20 s, PDF 50 s a page, new series each time, early in the chapter)\n" +
                "%-26s median %3.0f%%  within 25%% %3.0f%%  within 40%% %3.0f%%\n".format(
                    "one pace for everything",
                    m1 * 100,
                    a1 * 100,
                    b1 * 100,
                ) +
                "%-26s median %3.0f%%  within 25%% %3.0f%%  within 40%% %3.0f%%\n".format(
                    "a pace per kind of content",
                    m2 * 100,
                    a2 * 100,
                    b2 * 100,
                ),
        )
    }
}
