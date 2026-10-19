package com.romanianguy.klover.data.stats

import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

// a made-up reader through many chapters, the app learning from each prediction (calibrate); prints how much that helps
class HybridAccuracySimulation {
    private class Reader(
        val name: String,
        val mean: Float,
        val spread: Float,
        val pause: Float,
        val pauseSeconds: Float,
    )

    private fun gauss(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }

    private class Result(
        var rawErrors: MutableList<Float> = ArrayList(),
        var fixedErrors: MutableList<Float> = ArrayList(),
        var inside: Int = 0,
        var total: Int = 0,
        var width: Float = 0f,
        var storageBytes: Int = 0,
    )

    private fun simulate(r: Reader, chapters: Int, pages: Int = 40, runs: Int = 200): Result {
        val result = Result()
        val rnd = Random(33)
        repeat(runs) {
            var global: PaceSample? = null
            var outcomes: Outcomes? = null
            for (chapter in 0 until chapters) {
                var session: PaceSample? = null
                val times = List(pages) {
                    var t = r.mean * exp((gauss(rnd) * r.spread).toDouble()).toFloat()
                    if (rnd.nextFloat() < r.pause) t += r.pauseSeconds * (0.4f + rnd.nextFloat())
                    t.coerceIn(2.5f, 300f)
                }
                val checkpoints = CHECK_SPOTS.map { (it * pages).toInt() }
                val predictions = ArrayList<Pair<Int, Float>>() // page index and the raw prediction there
                for (page in 0 until pages) {
                    session = addPace(PaceKind.Pages, session, times[page], 1f)
                    global = addPace(PaceKind.Pages, global, times[page], 1f)
                    if (page + 1 in checkpoints) {
                        val left = pages - 1 - page
                        predictions += page to estimatePace(PaceKind.Pages, global, null, session) * (left + 0.5f)
                    }
                }
                val calibration = calibrate(outcomes)
                // Judge this chapter's predictions with what was known before it (the outcomes of the chapters before).
                if (chapter >= 3) {
                    for ((page, raw) in predictions) {
                        val actual = times.drop(page + 1).sum()
                        val fixed = raw * calibration.correction
                        result.rawErrors += abs(raw - actual) / actual
                        result.fixedErrors += abs(fixed - actual) / actual
                        calibration.range(fixed.toLong())?.let { range ->
                            result.total++
                            if (actual in range.first.toFloat()..range.last.toFloat()) result.inside++
                            result.width += (range.last - range.first) / fixed
                        }
                    }
                }
                // The chapter is finished: its predictions become outcomes.
                for ((page, raw) in predictions) {
                    outcomeRatio(times.drop(page + 1).sum(), raw)?.let { outcomes = addOutcome(outcomes, it) }
                }
            }
            result.storageBytes = outcomes?.let { com.romanianguy.klover.data.network.KavitaJson.encodeToString(Outcomes.serializer(), it).length } ?: 0
        }
        return result
    }

    private fun share(list: List<Float>, limit: Float) = list.count { it <= limit } / list.size.toFloat()

    @Test
    fun printHybridAccuracy() {
        val readers = listOf(
            Reader("ordinary, 15 s a page", 15f, 0.45f, 0.03f, 60f),
            Reader("steady, 10 s a page", 10f, 0.25f, 0.00f, 0f),
            Reader("erratic, 20 s a page", 20f, 0.75f, 0.08f, 90f),
            Reader("pauses a lot", 15f, 0.40f, 0.12f, 120f),
            Reader("careful, 45 s a page", 45f, 0.40f, 0.04f, 120f),
        )
        val out = StringBuilder("\nHYBRID (pace + learned correction and range). Chapters 4+ of 12, three checks each, 200 runs\n")
        out.append(
            "%-24s %10s %10s %10s %10s %12s %11s\n".format(
                "reader",
                "raw med",
                "fixed med",
                "raw in25",
                "fixed in25",
                "range holds",
                "range width",
            ),
        )
        var rawMed = 0f; var fixMed = 0f; var raw25 = 0f; var fix25 = 0f; var hold = 0f
        for (r in readers) {
            val res = simulate(r, 12)
            val raw = res.rawErrors.sorted(); val fix = res.fixedErrors.sorted()
            val med1 = raw[raw.size / 2]; val med2 = fix[fix.size / 2]
            val r25 = share(res.rawErrors, 0.25f); val f25 = share(res.fixedErrors, 0.25f)
            val holds = res.inside / res.total.toFloat()
            out.append(
                "%-24s %9.0f%% %9.0f%% %9.0f%% %9.0f%% %11.0f%% %10.0f%%\n".format(
                    r.name,
                    med1 * 100,
                    med2 * 100,
                    r25 * 100,
                    f25 * 100,
                    holds * 100,
                    res.width / res.total * 100,
                ),
            )
            rawMed += med1; fixMed += med2; raw25 += r25; fix25 += f25; hold += holds
        }
        val n = readers.size
        out.append(
            "%-24s %9.0f%% %9.0f%% %9.0f%% %9.0f%% %11.0f%%\n".format(
                "AVERAGE",
                rawMed / n * 100,
                fixMed / n * 100,
                raw25 / n * 100,
                fix25 / n * 100,
                hold / n * 100,
            ),
        )
        out.append(
            "stored outcomes of one kind: about %d bytes as JSON, however much is read\n".format(
                simulate(readers[0], 12, runs = 1).storageBytes,
            ),
        )
        println(out)
    }
}
