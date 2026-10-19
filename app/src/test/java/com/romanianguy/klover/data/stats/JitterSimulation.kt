package com.romanianguy.klover.data.stats

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

// how much the shown number jumps from page to page, raw vs kept steady by LeftSmoother, and whether that costs accuracy
class JitterSimulation {
    private class Reader(val name: String, val mean: Float, val spread: Float, val pause: Float, val drift: Float)

    private fun gauss(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }

    private class Result(
        val rawJitter: Float,
        val smoothJitter: Float,
        val rawError: Float,
        val smoothError: Float,
        val rawIn25: Float,
        val smoothIn25: Float,
    )

    private fun run(r: Reader, runs: Int = 300, pages: Int = 40): Result {
        val rnd = Random(3)
        var rawJ = 0f; var smoothJ = 0f; var steps = 0
        val rawErr = ArrayList<Float>(); val smoothErr = ArrayList<Float>()
        repeat(runs) {
            var global: PaceSample? = null
            var session: PaceSample? = null
            val smoother = LeftSmoother()
            val times = List(pages) {
                var t = r.mean * (1f + (rnd.nextFloat() - 0.5f) * r.drift) * exp((gauss(rnd) * r.spread).toDouble()).toFloat()
                if (rnd.nextFloat() < r.pause) t += 40f + rnd.nextFloat() * 120f
                t.coerceIn(2.5f, 300f)
            }
            var prevRaw = 0f; var prevSmooth = 0f; var prevUnits = 0f
            for (page in 0 until pages - 1) {
                session = addPace(PaceKind.Pages, session, times[page], 1f)
                global = addPace(PaceKind.Pages, global, times[page], 1f)
                val units = pages - 1 - page + 0.5f
                val rawNow = estimatePace(PaceKind.Pages, global, null, session) * units
                val smoothNow = smoother.next(units, rawNow)
                if (page >= 5) {
                    rawJ += abs(rawNow / (prevRaw * units / prevUnits) - 1f)
                    smoothJ += abs(smoothNow / (prevSmooth * units / prevUnits) - 1f)
                    steps++
                }
                if (page + 1 in setOf(pages / 4, pages / 2, pages * 3 / 4)) {
                    val actual = times.drop(page + 1).sum()
                    rawErr += abs(rawNow - actual) / actual
                    smoothErr += abs(smoothNow - actual) / actual
                }
                prevRaw = rawNow; prevSmooth = smoothNow; prevUnits = units
            }
        }
        rawErr.sort(); smoothErr.sort()
        return Result(
            rawJ / steps, smoothJ / steps,
            rawErr[rawErr.size / 2], smoothErr[smoothErr.size / 2],
            rawErr.count { it <= 0.25f } / rawErr.size.toFloat(), smoothErr.count { it <= 0.25f } / smoothErr.size.toFloat(),
        )
    }

    @Test
    fun printJitter() {
        val readers = listOf(
            Reader("steady, 10 s a page", 10f, 0.25f, 0.00f, 0.00f),
            Reader("ordinary, 15 s a page", 15f, 0.45f, 0.03f, 0.02f),
            Reader("erratic, 20 s a page", 20f, 0.75f, 0.08f, 0.06f),
            Reader("drifting, 25 s a page", 25f, 0.45f, 0.04f, 0.18f),
        )
        val out = StringBuilder("\nJITTER (unexplained change of the shown time from one page to the next) and accuracy, raw vs steadied\n")
        out.append(
            "%-24s %10s %10s %10s %10s %10s %10s\n".format(
                "reader",
                "raw jitter",
                "steady",
                "raw err",
                "steady err",
                "raw in25",
                "steady in25",
            ),
        )
        var jr = 0f; var js = 0f; var er = 0f; var es = 0f
        for (r in readers) {
            val res = run(r)
            out.append(
                "%-24s %9.1f%% %9.1f%% %9.0f%% %9.0f%% %9.0f%% %10.0f%%\n".format(
                    r.name,
                    res.rawJitter * 100,
                    res.smoothJitter * 100,
                    res.rawError * 100,
                    res.smoothError * 100,
                    res.rawIn25 * 100,
                    res.smoothIn25 * 100,
                ),
            )
            jr += res.rawJitter; js += res.smoothJitter; er += res.rawError; es += res.smoothError
        }
        out.append(
            "%-24s %9.1f%% %9.1f%% %9.0f%% %9.0f%%\n".format(
                "AVERAGE",
                jr / readers.size * 100,
                js / readers.size * 100,
                er / readers.size * 100,
                es / readers.size * 100,
            ),
        )
        println(out)
        assertTrue("steadied jitter ${js / readers.size} vs raw ${jr / readers.size}", js < jr * 0.7f)
        assertTrue("accuracy must not suffer much", es / readers.size < er / readers.size + 0.03f)
    }
}
