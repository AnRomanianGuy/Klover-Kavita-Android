package com.romanianguy.klover.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadingPaceTest {
    @Test
    fun withNoReadingsTheStartingGuessIsUsed() {
        assertEquals(PaceKind.Pages.defaultSecondsPerUnit, secondsPerUnit(PaceKind.Pages, null), 0f)
        assertEquals(PaceKind.Text.defaultSecondsPerUnit, secondsPerUnit(PaceKind.Text, PaceSample()), 0f)
    }

    @Test
    fun measuredPaceTakesOverGraduallyAndThenFully() {
        var sample: PaceSample? = null
        // Someone who takes 30 s a page (the guess is 15).
        sample = addPace(PaceKind.Pages, sample, 30f, 1f)
        val one = secondsPerUnit(PaceKind.Pages, sample)
        assertTrue("one reading moves the estimate part of the way", one > 15f && one < 30f)
        repeat(6) { sample = addPace(PaceKind.Pages, sample, 30f, 1f) }
        assertEquals(30f, secondsPerUnit(PaceKind.Pages, sample), 0.01f)
    }

    @Test
    fun oldReadingsFadeSoThePaceFollowsTheReader() {
        var sample: PaceSample? = null
        repeat(40) { sample = addPace(PaceKind.Pages, sample, 40f, 1f) }
        repeat(80) { sample = addPace(PaceKind.Pages, sample, 10f, 1f) }
        assertEquals(10f, secondsPerUnit(PaceKind.Pages, sample), 1.5f)
    }

    @Test
    fun aFlickOrAForgottenPhoneDoesNotCount() {
        val base = addPace(PaceKind.Pages, null, 12f, 1f)
        assertSame(base, addPace(PaceKind.Pages, base, 0.4f, 1f))
        assertSame(base, addPace(PaceKind.Pages, base, 900f, 1f))
        assertNull(addPace(PaceKind.Pages, null, 900f, 1f))
        // Text: a few characters say nothing; a screen skimmed in a second is not reading.
        assertNull(addPace(PaceKind.Text, null, 20f, 10f))
        assertNull(addPace(PaceKind.Text, null, 1f, 900f))
    }

    @Test
    fun severalPagesInOneStepAreSpreadOverThePages() {
        // A double page: 40 s for two pages is 20 s each.
        val sample = addPace(PaceKind.Pages, null, 40f, 2f)!!
        assertEquals(20f, sample.seconds / sample.units, 0.001f)
    }

    @Test
    fun timeLeftIsUnitsTimesPace() {
        assertEquals(150L, secondsLeft(10f, 15f))
        assertEquals(0L, secondsLeft(-3f, 15f))
    }

    @Test
    fun durationsAreWordedInWholeMinutes() {
        assertEquals(LeftText.UnderAMinute, leftText(0))
        assertEquals(LeftText.UnderAMinute, leftText(29))
        assertEquals(LeftText.Minutes(1), leftText(30))
        assertEquals(LeftText.Minutes(5), leftText(290))
        assertEquals(LeftText.Minutes(59), leftText(59 * 60))
        assertEquals(LeftText.HoursMinutes(1, 0), leftText(3600))
        assertEquals(LeftText.HoursMinutes(2, 5), leftText(2 * 3600 + 5 * 60))
    }
}

class StreakStatusTest {
    private val today = LocalDate.of(2026, 10, 18)

    private fun days(vararg offsets: Pair<Int, Long>) =
        offsets.associate { (back, seconds) -> today.minusDays(back.toLong()).toString() to DayStat(seconds = seconds) }

    @Test
    fun noReadingNoStreak() {
        val s = streakStatus(emptyMap(), today, 0)
        assertEquals(0, s.days)
        assertFalse(s.atRisk)
    }

    @Test
    fun aStreakThroughYesterdayIsAtRiskUntilTodayHasAMinute() {
        val d = days(1 to 600, 2 to 600, 3 to 600)
        val early = streakStatus(d, today, 10)
        assertEquals(3, early.days)
        assertTrue(early.atRisk)
        assertEquals(50L, early.secondsToKeep)
    }

    @Test
    fun readingAMinuteTodayCountsTodayAndEndsTheRisk() {
        val d = days(1 to 600, 2 to 600)
        val s = streakStatus(d, today, 75)
        assertEquals(3, s.days)
        assertTrue(s.todayDone)
        assertFalse(s.atRisk)
        assertEquals(0L, s.secondsToKeep)
    }

    @Test
    fun theFirstMinuteEverStartsAStreakOfOne() {
        val s = streakStatus(emptyMap(), today, 61)
        assertEquals(1, s.days)
        assertFalse(s.atRisk)
    }

    @Test
    fun aMissedDayBreaksIt() {
        val d = days(2 to 600, 3 to 600)
        assertEquals(0, streakStatus(d, today, 5).days)
    }

    @Test
    fun timeAlreadyReportedForTodayAddsToTheLiveSeconds() {
        val d = days(1 to 600, 0 to 40)
        val s = streakStatus(d, today, 25)
        assertTrue(s.todayDone)
        assertEquals(2, s.days)
    }
}

class BookCharsLeftTest {
    @Test
    fun aSingleSectionBookIsJustWhatIsLeftOfIt() {
        assertEquals(500f, bookCharsLeft(500, 0, 1, emptyMap(), 0)!!, 0f)
    }

    @Test
    fun theServersWordCountSizesTheSectionsNotYetSeen() {
        // 10 sections, 6000 words -> 36000 characters, 3600 a section; 3 sections after this one.
        val left = bookCharsLeft(1000, 6, 10, emptyMap(), 6000)!!
        assertEquals(1000f + 3 * 3600f, left, 0.5f)
    }

    @Test
    fun whenEverySectionIsKnownWhatIsLeftIsExactWhateverTheWordCountSays() {
        // A book on the device: all four sections measured. A word count far too high must not move the answer.
        val seen = mapOf(0 to 300, 1 to 200, 2 to 50, 3 to 70)
        assertEquals(100f + 50f + 70f, bookCharsLeft(100, 1, 4, seen, 99_999)!!, 0f)
        assertEquals(100f + 50f + 70f, bookCharsLeft(100, 1, 4, seen, 0)!!, 0f)
    }

    @Test
    fun sectionsAlreadySeenCountAtTheirRealSize() {
        val left = bookCharsLeft(100, 0, 4, mapOf(1 to 50, 2 to 70), 0)!!
        // sections 1 and 2 as seen; section 3 at the average of those seen (60).
        assertEquals(100f + 50f + 70f + 60f, left, 0.5f)
    }

    @Test
    fun atTheStartTheWholeBooksWordCountLessWhatWasReadIsUsed() {
        // 6000 words = 36000 characters over 10 sections; 500 read of an intro that turns out short.
        val left = bookCharsLeft(1500, 0, 10, mapOf(0 to 2000), 6000)!!
        assertTrue("left $left", left in 34_000f..35_600f)
    }

    @Test
    fun theWordCountEstimateFadesOutByHalfWay() {
        val seen = (0..5).associateWith { 3600 }
        // At the start of section 5 of 10, half the 36000 characters are read: the section sizes alone decide.
        assertEquals(3600f + 4 * 3600f, bookCharsLeft(3600, 5, 10, seen, 6000)!!, 0.5f)
    }

    @Test
    fun aWordCountTheSectionsSeenContradictIsNotTrusted() {
        val seen = (0..3).associateWith { 3600 }
        // The count says 120000 characters; four sections of 3600 say about 36000. The sections win.
        assertEquals(3600f + 6 * 3600f, bookCharsLeft(3600, 3, 10, seen, 20_000)!!, 0.5f)
    }

    @Test
    fun withoutAWordCountTheSectionsSeenStandInForTheRest() {
        val left = bookCharsLeft(100, 1, 5, mapOf(0 to 200, 1 to 400), 0)!!
        assertEquals(100f + 3 * 300f, left, 0.5f)
    }

    @Test
    fun withNothingToGoOnThereIsNoEstimate() {
        assertNull(bookCharsLeft(100, 1, 5, emptyMap(), 0))
    }

    @Test
    fun theLastSectionIsJustWhatIsLeftOfIt() {
        assertEquals(300f, bookCharsLeft(300, 4, 5, emptyMap(), 10_000)!!, 0f)
    }
}

class PaceEstimateTest {
    private fun sample(secondsPerUnit: Float, count: Int) = PaceSample(
        seconds = secondsPerUnit * 10f,
        units = 10f,
        count = count,
    )

    @Test
    fun aPauseInTheMiddleOfAPageCountsForALimitedTime() {
        // A reader at about 12 s a page walks away for ten minutes on one page.
        var s: PaceSample? = null
        repeat(6) { s = addPace(PaceKind.Pages, s, 12f, 1f) }
        val before = s!!.seconds / s!!.units
        val after = addPace(PaceKind.Pages, s, 170f, 1f)!!
        val pace = after.seconds / after.units
        // Unclamped, that one page would drag the pace to about 35 s; held to 2.5 times the usual it moves it far less.
        assertTrue("pace $pace should stay near $before", pace < before * 1.35f)
    }

    @Test
    fun aSeriesYouReadSlowlyPullsTheEstimateToItsOwnPace() {
        val general = sample(15f, 40)
        val dense = sample(40f, 8)
        assertEquals(40f, estimatePace(PaceKind.Pages, general, dense, null), 0.5f)
        // A few readings of it move the estimate part of the way.
        val few = sample(40f, 2)
        val estimate = estimatePace(PaceKind.Pages, general, few, null)
        assertTrue(estimate > 15f && estimate < 40f)
    }

    @Test
    fun thisSittingCountsHeavilyButNeverTakesOverEntirely() {
        val general = sample(15f, 40)
        val session = sample(30f, 30)
        val estimate = estimatePace(PaceKind.Pages, general, null, session)
        assertEquals(15f * 0.15f + 30f * 0.85f, estimate, 0.2f)
    }

    @Test
    fun aSecondSlowReadingInARowIsBelievedInFull() {
        var s: PaceSample? = null
        repeat(8) { s = addPace(PaceKind.Pages, s, 10f, 1f) }
        val usual = s!!.seconds / s!!.units
        val one = addPace(PaceKind.Pages, s, 90f, 1f)!!        // held to 2.5x usual
        val two = addPace(PaceKind.Pages, one, 90f, 1f)!!      // believed
        val afterOne = one.seconds / one.units
        val afterTwo = two.seconds / two.units
        assertTrue("one pause barely moves the pace ($afterOne vs $usual)", afterOne < usual * 1.4f)
        assertTrue("a second slow page does ($afterTwo)", afterTwo > afterOne * 1.5f)
    }

    @Test
    fun evenABelievedPauseIsCappedAtSixTimesTheUsualPace() {
        var s: PaceSample? = null
        repeat(8) { s = addPace(PaceKind.Pages, s, 10f, 1f) }
        val usualSeconds = s!!.seconds / s!!.units
        val one = addPace(PaceKind.Pages, s, 150f, 1f)!!
        val two = addPace(PaceKind.Pages, one, 150f, 1f)!!
        // The second counts, but as at most 6x the usual pace then (which the first pause had already nudged up), not 150 s.
        val usualThen = one.seconds / one.units
        val added = two.seconds - one.seconds * 0.95f
        assertTrue("added $added vs cap ${usualThen * 6f}", added <= usualThen * 6f + 0.01f)
        assertTrue(
            "and more than the first pause was allowed ($added vs ${usualSeconds * 2.5f})",
            added > usualSeconds * 2.5f,
        )
    }

    @Test
    fun aSlowPageTheReaderWasSeenBusyThroughIsABelievedSlowdownAtOnce() {
        var s: PaceSample? = null
        repeat(8) { s = addPace(PaceKind.Pages, s, 10f, 1f) }
        val held = addPace(PaceKind.Pages, s, 60f, 1f)!!
        val confirmed = addPace(PaceKind.Pages, s, 60f, 1f, confirmed = true)!!
        assertTrue("confirmed ${confirmed.seconds} vs held ${held.seconds}", confirmed.seconds > held.seconds + 20f)
        assertEquals(0, confirmed.odd)
        assertEquals(1, held.odd)
    }

    @Test
    fun aConfirmedReadingStillHasTheHardCap() {
        var s: PaceSample? = null
        repeat(8) { s = addPace(PaceKind.Pages, s, 10f, 1f) }
        val usual = s!!.seconds / s!!.units
        val confirmed = addPace(PaceKind.Pages, s, 170f, 1f, confirmed = true)!!
        val added = confirmed.seconds - s!!.seconds * 0.95f
        assertTrue("added $added", added <= usual * 6f + 0.01f)
    }

    @Test
    fun aSecondFastReadingInARowIsBelievedToo() {
        var s: PaceSample? = null
        repeat(8) { s = addPace(PaceKind.Pages, s, 30f, 1f) }
        val one = addPace(PaceKind.Pages, s, 5f, 1f)!!         // held to 0.4x usual
        val two = addPace(PaceKind.Pages, one, 5f, 1f)!!       // believed
        val perOne = one.seconds / one.units
        val perTwo = two.seconds / two.units
        assertTrue("two quick pages pull the pace down ($perTwo < $perOne)", perTwo < perOne - 2f)
    }

    @Test
    fun theEstimateRisesAgainWhenTheReaderSlowsDown() {
        // A sitting that is quick for a while, then slow: the pace follows both ways within a few pages.
        var session: PaceSample? = null
        repeat(6) { session = addPace(PaceKind.Pages, session, 8f, 1f) }
        val quick = estimatePace(PaceKind.Pages, null, null, session)
        repeat(4) { session = addPace(PaceKind.Pages, session, 40f, 1f) }
        val slow = estimatePace(PaceKind.Pages, null, null, session)
        assertTrue("quick $quick", quick < 12f)
        assertTrue("slow $slow", slow > 25f)
    }

    @Test
    fun withNothingMeasuredTheStartingGuessIsUsed() {
        assertEquals(PaceKind.Strips.defaultSecondsPerUnit, estimatePace(PaceKind.Strips, null, null, null), 0f)
    }

    @Test
    fun leastRecentlyReadSeriesAreDroppedFromTheFile() {
        val file = java.io.File.createTempFile("pace", ".json")
        val pace = ReadingPace(
            com.romanianguy.klover.data.session.SingleFile(file),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        repeat(150) { id -> pace.record(PaceKind.Pages, 10f, 1f, seriesId = id) }
        val series = pace.samples.value.keys.count { '#' in it }
        assertTrue("kept $series", series <= 60)
        assertTrue(pace.samples.value.containsKey(PaceKind.Pages.key))
        file.delete()
    }

    @Test
    fun manySectionsSeenOutweighTheWordCount() {
        // Three sections of 4000 characters seen; the word count says 9000 a section. The rest follow what was seen.
        val left = bookCharsLeft(500, 2, 6, mapOf(0 to 4000, 1 to 4000, 2 to 4000), 9000)!!
        assertEquals(500f + 3 * 4000f, left, 0.5f)
    }
}
