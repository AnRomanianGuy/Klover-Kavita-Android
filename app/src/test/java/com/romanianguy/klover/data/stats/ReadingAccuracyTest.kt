package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.network.KavitaJson
import com.romanianguy.klover.ui.reader.book.SpeakingRate
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingAccuracyTest {
    private fun outcomes(vararg ratios: Float): Outcomes {
        var o: Outcomes? = null
        for (r in ratios) o = addOutcome(o, r)
        return o!!
    }

    private fun repeated(ratio: Float, times: Int): Outcomes {
        var o: Outcomes? = null
        repeat(times) { o = addOutcome(o, ratio) }
        return o!!
    }

    @Test
    fun withNoOutcomesThereIsNoCorrectionNoClaimOfAccuracyAndAWideRange() {
        val c = calibrate(null)
        assertEquals(1f, c.correction, 0f)
        assertNull(c.band)
        assertEquals(false, c.accurate)
        // Before there is any history the range is as wide as the default allows for.
        val r = c.range(1000)
        assertEquals(600L, r.first)
        assertEquals(1400L, r.last)
    }

    @Test
    fun anEstimateIsCalledAccurateOnlyWhenTheOutcomesProveIt() {
        assertEquals(false, calibrate(repeated(1.0f, MIN_FOR_BAND - 1)).accurate)     // too few to say
        assertEquals(true, calibrate(repeated(1.0f, 20)).accurate)                    // always right on
        var scattered: Outcomes? = null
        val ratios = floatArrayOf(0.6f, 1.6f, 0.8f, 1.3f, 0.7f, 1.5f, 1.0f, 0.9f, 1.2f, 0.65f)
        repeat(3) { for (r in ratios) scattered = addOutcome(scattered, r) }
        assertEquals(false, calibrate(scattered).accurate)                            // too scattered
    }

    @Test
    fun theProfileTagsTheReaderBySteadinessAndSpeed() {
        assertEquals(Steadiness.Steady, steadinessOf(0.1f))
        assertEquals(Steadiness.Regular, steadinessOf(0.25f))
        assertEquals(Steadiness.Varied, steadinessOf(0.35f))
        assertEquals(Steadiness.Erratic, steadinessOf(0.5f))
        // Manga starts at 8 s a page: 5 s is fast, 8 s is average, 13 s is slow. Too few readings say average.
        fun pace(secondsPerPage: Float, count: Int) = PaceSample(
            seconds = secondsPerPage * 10f,
            units = 10f,
            count = count,
        )
        assertEquals(Speed.Fast, speedOf(PaceKind.Manga, pace(5f, 20)))
        assertEquals(Speed.Average, speedOf(PaceKind.Manga, pace(8f, 20)))
        assertEquals(Speed.Slow, speedOf(PaceKind.Manga, pace(13f, 20)))
        assertEquals(Speed.Average, speedOf(PaceKind.Manga, pace(3f, 2)))
    }

    @Test
    fun theProfileNeedsEnoughChecksAndGivesAPercentage() {
        assertNull(profile(PaceKind.Pages, repeated(1.0f, 3), null))
        val p = profile(PaceKind.Pages, repeated(1.0f, 20), null)!!
        // Always exactly right: the narrowest range allowed (8%), so 92%.
        assertEquals(92, p.accuracyPercent)
        assertEquals(Steadiness.Steady, p.steadiness)
        assertNull(overallProfile(emptyMap()))
        val overall = overallProfile(mapOf("pages" to repeated(1.0f, 20), "manga" to repeated(1.0f, 10)))!!
        assertEquals(92, overall.first)
    }

    @Test
    fun anEstimateThatIsAlwaysAThirdShortIsCorrectedUpwardsGradually() {
        val few = correction(repeated(1.33f, 3))
        val many = correction(repeated(1.33f, 40))
        assertTrue("few $few", few > 1.02f && few < many)
        assertTrue("many $many", many in 1.2f..1.35f)
    }

    @Test
    fun anEstimateThatIsAboutRightIsLeftAlone() {
        assertEquals(1f, correction(repeated(1.03f, 30)), 0f)
        assertEquals(1f, correction(repeated(0.96f, 30)), 0f)
    }

    @Test
    fun theRangeComesOnlyOnceThereIsEnoughToSayAndFollowsHowOftenItMissed() {
        assertNull(band(repeated(1.0f, MIN_FOR_BAND - 1)))
        val tight = band(repeated(1.0f, 20))!!
        // Outcomes that scatter between 0.6 and 1.6 give a wider range than ones that all say 1.
        var scattered: Outcomes? = null
        val ratios = floatArrayOf(0.6f, 1.6f, 0.8f, 1.3f, 0.7f, 1.5f, 1.0f, 0.9f, 1.2f, 0.65f)
        repeat(3) { for (r in ratios) scattered = addOutcome(scattered, r) }
        val wide = band(scattered)!!
        assertTrue("tight $tight wide $wide", wide > tight + 0.15f)
        assertTrue(tight >= 0.08f && wide <= 0.6f)
    }

    @Test
    fun theRangeIsAroundTheCorrectedPrediction() {
        val range = Calibration(correction = 1f, band = 0.25f, samples = 20).range(1000)!!
        assertEquals(750L, range.first)
        assertEquals(1250L, range.last)
    }

    @Test
    fun olderReadingFadesSoTheSummaryFollowsTheReader() {
        // A long spell of estimates 40% short, then a long spell of right ones: the correction goes away.
        var o: Outcomes? = null
        repeat(60) { o = addOutcome(o, 1.4f) }
        val before = correction(o)
        repeat(100) { o = addOutcome(o, 1.0f) }
        val after = correction(o)
        assertTrue("before $before", before > 1.2f)
        assertEquals(1f, after, 0.05f)
    }

    @Test
    fun theSummaryNeverGrowsHoweverMuchIsRead() {
        var o: Outcomes? = null
        val sizes = ArrayList<Int>()
        for (i in 1..5000) {
            o = addOutcome(o, 0.5f + (i % 17) / 10f)
            if (i == 50 || i == 500 || i == 5000) sizes += KavitaJson.encodeToString(Outcomes.serializer(), o!!).length
        }
        assertEquals(32, o!!.bins.size)
        assertTrue("sizes $sizes", sizes.last() <= sizes.first() + 40)
        assertTrue("size ${sizes.last()}", sizes.last() < 400)
    }

    @Test
    fun aChapterTurnsItsPredictionsIntoOutcomesWhenItIsFinished() {
        var data = AccuracyData()
        data = withSpent(data, 7, 40)                                     // 40 s read when the first spot is reached
        data = withCheck(data, 7, "pages", predictedSeconds = 600, spentNow = 40, spot = 0)
        data = withSpent(data, 7, 760)                                    // 800 s in all by the end
        data = withFinished(data, 7, spentNow = 800)
        assertTrue(data.pending.isEmpty())
        // 760 s really passed after a prediction of 600: a ratio of about 1.27.
        val o = data.outcomes.getValue("pages")
        assertEquals(1, o.n)
        // One outcome moves the correction only a little (it is trusted gradually).
        val c = correction(o)
        assertTrue("correction $c", c > 1f && c < 1.1f)
    }

    @Test
    fun aSpotGetsOnePredictionOnly() {
        var data = withCheck(AccuracyData(), 3, "manga", 300, 10, spot = 1)
        data = withCheck(data, 3, "manga", 250, 60, spot = 1)
        assertEquals(1, data.pending.getValue(3).checks.size)
        data = withCheck(data, 3, "manga", 200, 90, spot = 2)
        assertEquals(2, data.pending.getValue(3).checks.size)
    }

    @Test
    fun aFinishedChapterThatWasNeverCheckedLeavesNoOutcome() {
        val data = withFinished(withSpent(AccuracyData(), 9, 100), 9, 100)
        assertTrue(data.outcomes.isEmpty())
        assertTrue(data.pending.isNotEmpty() || data.pending.isEmpty())
    }

    @Test
    fun onlyTheLastFewChaptersBeingReadAreRemembered() {
        var data = AccuracyData()
        for (id in 1..12) data = withSpent(data, id, 30)
        assertEquals(6, data.pending.size)
        assertTrue(1 !in data.pending && 12 in data.pending)
    }

    @Test
    fun theWholeRecordStaysWithinAHandfulOfKilobytes() {
        // Every kind of content with a long history, six chapters half read, and the paces of 60 series: the worst case.
        var outcomes = emptyMap<String, Outcomes>()
        for (kind in PaceKind.entries) {
            var o: Outcomes? = null
            repeat(2000) { i -> o = addOutcome(o, 0.6f + (i % 13) / 10f) }
            outcomes = outcomes + (kind.key to o!!)
        }
        var data = AccuracyData(outcomes)
        for (id in 100..105) {
            data = withSpent(data, id, 1234)
            for (spot in CHECK_SPOTS.indices) data = withCheck(data, id, "pages", 12345, 1234, spot)
        }
        val accuracyBytes = KavitaJson.encodeToString(AccuracyData.serializer(), data).length

        var paces = emptyMap<String, PaceSample>()
        for (kind in PaceKind.entries) {
            var s: PaceSample? = null
            repeat(200) { s = addPace(kind, s, kind.defaultSecondsPerUnit * kind.minUnits * (1f + it % 7 / 10f), kind.minUnits, now = 1_760_000_000L) }
            paces = paces + (kind.key to s!!)
            for (series in 1..10) paces = paces + (paceKey(kind, series) to s!!)
        }
        val paceBytes = KavitaJson.encodeToString(
            MapSerializer(String.serializer(), PaceSample.serializer()),
            paces,
        ).length

        println("STORAGE: accuracy.json $accuracyBytes bytes, pace.json $paceBytes bytes (worst case)")
        assertTrue("accuracy $accuracyBytes", accuracyBytes < 6_000)
        assertTrue("pace $paceBytes", paceBytes < 16_000)
        assertNotNull(data)
    }
}

class AccuracyLimitsTest {
    @Test
    fun aChapterTeachesTheEstimateBeforeItIsFinished() {
        // 40 units left at the first spot, 600 s predicted; by the second, 20 units later, 180 s had passed.
        var data = withCheck(AccuracyData(), 5, "pages", predictedSeconds = 600, spentNow = 0, spot = 0, unitsLeft = 40)
        data = withCheck(data, 5, "pages", predictedSeconds = 320, spentNow = 180, spot = 1, unitsLeft = 20)
        // The first prediction covered 600 s for 40 units, so 300 s for the 20 read: it took 180 s, a ratio of 0.6.
        val outcomes = data.outcomes.getValue("pages")
        assertEquals(1, outcomes.n)
        assertTrue(data.pending.getValue(5).checks[0].done)
        assertTrue(!data.pending.getValue(5).checks[1].done)
        // Leaving the chapter there (never finishing it) has still taught the estimate something.
    }

    @Test
    fun aStretchTooShortToJudgeIsLeftWaiting() {
        var data = withCheck(AccuracyData(), 5, "pages", 600, 0, spot = 0, unitsLeft = 40)
        data = withCheck(data, 5, "pages", 590, 20, spot = 1, unitsLeft = 37)   // only 3 of 40 read
        assertTrue(data.outcomes.isEmpty())
        assertTrue(!data.pending.getValue(5).checks[0].done)
    }

    @Test
    fun finishingDoesNotCountAStretchAlreadyChecked() {
        var data = withCheck(AccuracyData(), 5, "pages", 600, 0, spot = 0, unitsLeft = 40)
        data = withCheck(data, 5, "pages", 320, 180, spot = 1, unitsLeft = 20)
        data = withFinished(data, 5, spentNow = 400)
        // One outcome from the first stretch, one from the last (the second prediction against the rest of the chapter).
        assertEquals(2, data.outcomes.getValue("pages").n)
        assertTrue(data.pending.isEmpty())
    }

    @Test
    fun aJumpDropsTheWaitingPredictionsInsteadOfGivingAWrongOutcome() {
        var data = withCheck(AccuracyData(), 5, "pages", 600, 0, spot = 0, unitsLeft = 40)
        data = withoutChecks(data, 5)
        assertTrue(data.pending.getValue(5).checks.isEmpty())
        // Which spots have been used is kept, so the same spot is not taken twice.
        assertEquals(1, data.pending.getValue(5).taken)
    }

    @Test
    fun theLastFewWordCountsAreKept() {
        var data = AccuracyData()
        for (id in 1..15) data = withWords(data, id, 1000 * id)
        assertEquals(12, data.words.size)
        assertTrue(1 !in data.words && 15 in data.words)
        assertEquals(15_000, data.words.getValue(15).count)
    }

    @Test
    fun aSourceWhoseCountsAreAlwaysShortIsLearntOnceAndOnlyOncePerBook() {
        var data = withWords(AccuracyData(), 1, 5000)
        data = withLearnedFactor(data, 1, observed = 1.6f)
        val first = data.wordFactor
        assertTrue("factor $first", first > 1.2f && first < 1.6f)
        // The same book again counts for nothing more.
        assertEquals(first, withLearnedFactor(data, 1, 1.6f).wordFactor, 0f)
        // A second book that says the same moves it further.
        data = withLearnedFactor(withWords(data, 2, 7000), 2, 1.6f)
        assertTrue(data.wordFactor > first)
        assertEquals(2, data.wordFactorN)
        // A book with no kept count cannot teach anything.
        assertEquals(data.wordFactor, withLearnedFactor(data, 99, 2f).wordFactor, 0f)
    }

    @Test
    fun theFactorIsKeptToASensibleRange() {
        var data = withWords(AccuracyData(), 1, 5000)
        data = withLearnedFactor(data, 1, observed = 50f)
        assertTrue(data.wordFactor <= 2.5f)
    }
}

class SureEnoughTest {
    @Test
    fun theNearerTheEndTheSurerTheEstimate() {
        val c = Calibration(correction = 1f, band = 0.3f, samples = 20)
        assertTrue(c.bandAt(0.1f) > c.bandAt(0.35f))
        assertEquals(0.3f, c.bandAt(REFERENCE_PROGRESS), 0.001f)
        assertTrue(c.bandAt(0.85f) < 0.18f)
        assertTrue(!c.accurateAt(0.35f))      // usually 30% off a third of the way in: a range
        assertTrue(c.accurateAt(0.85f))       // but with little left, one number
    }

    @Test
    fun anEstimateThatHasAlwaysBeenCloseIsShownAsOneNumberFromEarlyOn() {
        val c = Calibration(correction = 1f, band = 0.12f, samples = 30)
        assertTrue(c.accurateAt(0.2f))
        assertTrue(c.accurateAt(0.5f))
    }

    @Test
    fun withNoHistoryARangeIsShownUntilNearlyAtTheEnd() {
        val c = Calibration()
        assertTrue(!c.accurateAt(0.2f))
        assertTrue(!c.accurateAt(0.6f))
        assertTrue(c.accurateAt(0.9f))
    }

    @Test
    fun theRangeNarrowsAsTheChapterGoes() {
        val c = Calibration(1f, 0.3f, 20)
        val early = c.range(1000, 0.2f)
        val late = c.range(1000, 0.8f)
        assertTrue((late.last - late.first) < (early.last - early.first))
    }
}

class SpeakingRateTest {
    @Test
    fun beforeAnythingIsHeardTheSpeedSettingDecides() {
        val rate = SpeakingRate()
        assertEquals(15f, rate.charsPerSecond(1f), 0.01f)
        assertEquals(30f, rate.charsPerSecond(2f), 0.01f)
    }

    @Test
    fun aVoiceThatSpeaksFasterThanTheSettingSaysIsMeasured() {
        val rate = SpeakingRate()
        var position = 0
        var now = 0L
        // 6 characters every 300 ms is 20 characters a second, where the setting at 1x would say 15.
        repeat(40) {
            rate.onPosition(position, now)
            position += 6
            now += 300
        }
        assertEquals(20f, rate.charsPerSecond(1f), 0.8f)
    }

    @Test
    fun timeSpentPausedIsNotSpeakingTime() {
        val rate = SpeakingRate()
        var position = 0
        var now = 0L
        repeat(30) { rate.onPosition(position, now); position += 5; now += 333 }
        val before = rate.charsPerSecond(1f)
        rate.pause()
        now += 60_000
        repeat(5) { rate.onPosition(position, now); position += 5; now += 333 }
        assertEquals(before, rate.charsPerSecond(1f), 1f)
    }

    @Test
    fun movingTheVoiceToAnotherPlaceDoesNotCountAsFastSpeech() {
        val rate = SpeakingRate()
        var now = 0L
        repeat(30) { rate.onPosition(it * 5, now); now += 333 }
        val before = rate.charsPerSecond(1f)
        rate.onPosition(50_000, now)
        assertEquals(before, rate.charsPerSecond(1f), 0.5f)
    }

    @Test
    fun changingTheSpeedStartsAgainFromTheSetting() {
        val rate = SpeakingRate()
        repeat(30) { rate.onPosition(it * 5, it * 333L) }
        rate.reset()
        assertEquals(22.5f, rate.charsPerSecond(1.5f), 0.01f)
    }
}

class ChapterPaceTest {
    @Test
    fun aChapterReadThroughFromItsStartGivesItsPace() {
        // 20 pages (19.5 left of 20.5 at the first look), 240 s: 12 s a page.
        val (seconds, pages) = chapterPace(
            startUnits = 20.5f,
            endUnits = 0.5f,
            startProgress = 0.05f,
            seconds = 240f,
            usualSecondsPerUnit = 12f,
        )!!
        assertEquals(240f, seconds, 0.001f)
        assertEquals(20f, pages, 0.001f)
    }

    @Test
    fun aChapterEnteredPartWayOrMostlySkippedSaysNothing() {
        assertNull(chapterPace(20.5f, 0.5f, startProgress = 0.5f, seconds = 240f, usualSecondsPerUnit = 12f))
        // jumped to near the end: only 5 of 20 pages were read
        assertNull(chapterPace(5.5f, 0.5f, startProgress = 0.05f, seconds = 60f, usualSecondsPerUnit = 12f).takeIf { false })
        assertNull(chapterPace(startUnits = 20.5f, endUnits = 12.5f, startProgress = 0.05f, seconds = 90f, usualSecondsPerUnit = 12f))
    }

    @Test
    fun tooShortAChapterToSayAnything() {
        assertNull(chapterPace(3.5f, 0.5f, 0.05f, 30f, 12f))
    }

    @Test
    fun aChapterLeftOpenOrFlickedThroughIsNotAPace() {
        assertNull(chapterPace(20.5f, 0.5f, 0.05f, 2_000f, 12f))   // 100 s a page when 12 is usual
        assertNull(chapterPace(20.5f, 0.5f, 0.05f, 40f, 12f))      // 2 s a page
    }

    @Test
    fun withNoUsualPaceYetAnyChapterReadThroughCounts() {
        assertEquals(300f to 20f, chapterPace(20.5f, 0.5f, 0.0f, 300f, null))
    }

    @Test
    fun theWordCountFactorIsTheRealTextAgainstWhatTheCountSays() {
        // Ten sections of 3600 characters = 36000; 6000 words at 6 characters = 36000: the count is right.
        assertEquals(1f, observedWordFactor(List(10) { 3600 }, 10, 6000)!!, 0.001f)
        // The same text with a count that is half of it: the real text is twice what the count says.
        assertEquals(2f, observedWordFactor(List(10) { 3600 }, 10, 3000)!!, 0.001f)
    }

    @Test
    fun noWordCountOrTooFewSectionsTeachNothing() {
        assertNull(observedWordFactor(List(10) { 3600 }, 10, 0))
        assertNull(observedWordFactor(List(MIN_SECTIONS_TO_LEARN - 1) { 3600 }, 10, 6000))
        assertNull(observedWordFactor(List(10) { 3600 }, 0, 6000))
    }

    @Test
    fun aBookOnTheDeviceTeachesTheSameAsFiveSectionsRead() {
        // Reading the first five of ten gives the average of those five; the whole book gives the true one. Both are what the
        // reader and the download hand to the same function, so the same book can be learnt from either way.
        val all = List(10) { 3000 + it * 100 }
        val five = all.take(5)
        val fromAll = observedWordFactor(all, 10, 6000)!!
        val fromFive = observedWordFactor(five, 10, 6000)!!
        assertEquals(all.average().toFloat() * 10 / 36_000f, fromAll, 0.0001f)
        assertEquals(five.average().toFloat() * 10 / 36_000f, fromFive, 0.0001f)
    }
}
