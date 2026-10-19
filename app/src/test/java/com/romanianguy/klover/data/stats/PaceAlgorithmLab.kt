package com.romanianguy.klover.data.stats

import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

// a lab for trying pace estimators on made-up readers; prints a table, asserts nothing
class PaceAlgorithmLab {
    private class Reader(
        val name: String,
        val mean: Float,
        val spread: Float,
        val pause: Float,
        val drift: Float,
        val step: Float = 1f,
    )

    private fun gauss(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }

    private fun chapter(r: Reader, pages: Int, rnd: Random): Pair<List<Float>, List<Float>> {
        val total = ArrayList<Float>()
        val reading = ArrayList<Float>()
        var mean = r.mean
        for (i in 0 until pages) {
            if (i == pages / 2) mean *= r.step
            mean *= 1f + (rnd.nextFloat() - 0.5f) * r.drift
            val read = (mean * exp((gauss(rnd) * r.spread).toDouble()).toFloat()).coerceIn(2.5f, 175f)
            val pause = if (rnd.nextFloat() < r.pause) 40f + rnd.nextFloat() * 140f else 0f
            reading += read
            total += (read + pause).coerceAtMost(175f)
        }
        return total to reading
    }

    private interface Estimator {
        fun add(seconds: Float)
        fun estimate(): Float
    }

    private class Current : Estimator {
        var global: PaceSample? = null
        var session: PaceSample? = null
        override fun add(seconds: Float) {
            session = addPace(PaceKind.Pages, session, seconds, 1f)
            global = addPace(PaceKind.Pages, global, seconds, 1f)
        }
        override fun estimate() = estimatePace(PaceKind.Pages, global, null, session)
    }

    // long window by default, the short one only when it clearly disagrees; readings are first held against pauses and flicks
    private class Dual(
        val longDecay: Float,
        val shortDecay: Float,
        val low: Float,
        val high: Float,
        val prior: Float = 15f,
    ) : Estimator {
        var ls = 0f; var lu = 0f; var ss = 0f; var su = 0f; var n = 0; var odd = 0
        override fun add(seconds: Float) {
            var taken = seconds
            var o = 0
            if (n >= 3 && lu > 0f) {
                val usual = ls / lu
                val dir = when {
                    seconds > usual * 2.5f -> 1
                    seconds < usual * 0.4f -> -1
                    else -> 0
                }
                if (dir != 0 && dir != odd) { taken = if (dir > 0) usual * 2.5f else usual * 0.4f; o = dir }
                else if (dir != 0) taken = seconds.coerceIn(usual * 0.2f, usual * 6f)
            }
            odd = o
            ls = ls * longDecay + taken; lu = lu * longDecay + 1f
            ss = ss * shortDecay + taken; su = su * shortDecay + 1f
            n++
        }
        override fun estimate(): Float {
            if (n == 0) return prior
            val long = ls / lu
            val short = ss / su
            // before there is much to go on, lean on the short window's own mean plus the prior
            val trust = min(1f, n / 3f)
            val base = prior * (1 - trust) + long * trust
            val dev = abs(short - base) / base
            val w = ((dev - low) / (high - low)).coerceIn(0f, 1f)
            return base * (1 - w) + short * w
        }
    }

    // knows the true average pace from the start: the best a steady guess can do
    private class Fixed(val seconds: Float) : Estimator {
        override fun add(seconds: Float) {}
        override fun estimate() = seconds
    }

    private class Score(var median: Float = 0f, var within25: Float = 0f, var within40: Float = 0f)

    private fun evaluate(
        r: Reader,
        make: () -> Estimator,
        history: Boolean,
        againstReading: Boolean,
        runs: Int = 400,
        pages: Int = 40,
    ): Score {
        val rnd = Random(11)
        val errors = ArrayList<Float>()
        repeat(runs) {
            val e = make()
            if (history) repeat(30) { e.add(r.mean * (0.7f + rnd.nextFloat() * 0.6f)) }
            val (total, reading) = chapter(r, pages, rnd)
            val checks = setOf(pages / 4, pages / 2, pages * 3 / 4)
            for (page in 0 until pages) {
                e.add(total[page])
                val left = pages - 1 - page
                if (page + 1 in checks && left > 0) {
                    val predicted = e.estimate() * (left + 0.5f)
                    val actual = (if (againstReading) reading else total).drop(page + 1).sum()
                    errors += abs(predicted - actual) / actual
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

    // after the pace steps (10 s to 30 s halfway), how wrong is the estimate 8 pages later?
    private fun lag(make: () -> Estimator, runs: Int = 300): Float {
        val rnd = Random(5)
        val r = Reader("step", 10f, 0.35f, 0.02f, 0.02f, step = 3f)
        val errors = ArrayList<Float>()
        repeat(runs) {
            val e = make()
            val (total, _) = chapter(r, 40, rnd)
            for (page in 0 until 28) e.add(total[page])
            val predicted = e.estimate() * 11.5f
            val actual = total.drop(28).sum()
            errors += abs(predicted - actual) / actual
        }
        errors.sort()
        return errors[errors.size / 2]
    }

    @Test
    fun lab() {
        val readers = listOf(
            Reader("steady 10s", 10f, 0.25f, 0.00f, 0.00f),
            Reader("ordinary 15s", 15f, 0.45f, 0.03f, 0.02f),
            Reader("careful 45s", 45f, 0.40f, 0.03f, 0.02f),
            Reader("erratic 20s", 20f, 0.75f, 0.08f, 0.06f),
            Reader("drifting 25s", 25f, 0.45f, 0.04f, 0.18f),
        )
        val configs: List<Pair<String, () -> Estimator>> = listOf(
            "current" to { Current() },
            "dual .95/.7 band .15-.5" to { Dual(0.95f, 0.7f, 0.15f, 0.5f) },
            "dual .97/.7 band .2-.6" to { Dual(0.97f, 0.7f, 0.2f, 0.6f) },
            "dual .97/.75 band .25-.7" to { Dual(0.97f, 0.75f, 0.25f, 0.7f) },
            "dual .98/.7 band .3-.8" to { Dual(0.98f, 0.7f, 0.3f, 0.8f) },
            "dual .95/.8 band .2-.6" to { Dual(0.95f, 0.8f, 0.2f, 0.6f) },
        )
        val out = StringBuilder("\nLAB (avg over 5 readers, no history; error vs wall-clock time / vs reading time; step lag = median err 8 pages after a 3x slowdown)\n")
        out.append("%-28s %9s %9s %9s %9s %9s\n".format("algorithm", "median", "in25%", "in40%", "vsRead25", "stepLag"))
        for ((name, make) in configs) {
            var m = 0f; var w25 = 0f; var w40 = 0f; var rd25 = 0f
            for (r in readers) {
                val a = evaluate(r, make, history = false, againstReading = false)
                val b = evaluate(r, make, history = false, againstReading = true)
                m += a.median; w25 += a.within25; w40 += a.within40; rd25 += b.within25
            }
            val n = readers.size
            out.append(
                "%-28s %8.1f%% %8.1f%% %8.1f%% %8.1f%% %8.1f%%\n".format(
                    name,
                    m / n * 100,
                    w25 / n * 100,
                    w40 / n * 100,
                    rd25 / n * 100,
                    lag(make) * 100,
                ),
            )
        }
        var m = 0f; var w25 = 0f; var w40 = 0f; var rd25 = 0f
        for (r in readers) {
            val a = evaluate(r, { Fixed(r.mean) }, history = false, againstReading = false)
            val b = evaluate(r, { Fixed(r.mean) }, history = false, againstReading = true)
            m += a.median; w25 += a.within25; w40 += a.within40; rd25 += b.within25
        }
        val n = readers.size
        out.append(
            "%-28s %8.1f%% %8.1f%% %8.1f%% %8.1f%%\n".format(
                "FLOOR (true mean known)",
                m / n * 100,
                w25 / n * 100,
                w40 / n * 100,
                rd25 / n * 100,
            ),
        )
        println(out)
    }
}
