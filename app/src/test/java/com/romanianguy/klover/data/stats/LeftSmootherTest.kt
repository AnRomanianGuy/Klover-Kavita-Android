package com.romanianguy.klover.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftSmootherTest {
    @Test
    fun theFirstEstimateIsShownAsItIs() {
        assertEquals(600f, LeftSmoother().next(40f, 600f), 0.001f)
    }

    @Test
    fun withTheSamePaceTheShownTimeCountsDownWithTheReading() {
        val s = LeftSmoother()
        s.next(40f, 600f)
        // 10 of 40 units read, the raw estimate agrees: 450.
        assertEquals(450f, s.next(30f, 450f), 0.5f)
    }

    @Test
    fun smallWobblesInTheRawEstimateDoNotMoveWhatIsShown() {
        val s = LeftSmoother()
        s.next(40f, 600f)
        // The raw estimate is 4% off from what reading alone would give: ignored.
        assertEquals(450f, s.next(30f, 450f * 1.04f), 0.5f)
    }

    @Test
    fun aMediumChangeIsFollowedOnlyAStepAtATime() {
        val s = LeftSmoother()
        s.next(40f, 600f)
        // 25% more than expected: one update moves it 5%, not 25%.
        val once = s.next(30f, 450f * 1.25f)
        assertEquals(450f * 1.05f, once, 1f)
        // Kept up, it gets there in a few more.
        var shown = once
        var units = 30f
        repeat(6) {
            units -= 1f
            shown = s.next(units, (450f * 1.25f) * units / 30f)
        }
        assertTrue("shown $shown", shown / (450f * 1.25f * units / 30f) > 0.95f)
    }

    @Test
    fun aRealJumpIsTakenAtOnce() {
        val s = LeftSmoother()
        s.next(40f, 600f)
        // The reader slowed to half speed for good: the raw estimate doubles, and what is shown goes with it.
        assertEquals(900f, s.next(30f, 900f), 1f)
    }

    @Test
    fun aNoisyEstimateMovesWhatIsShownLessThanItMovesItself() {
        val s = LeftSmoother()
        var units = 40f
        val noise = floatArrayOf(1.12f, 0.9f, 1.15f, 0.88f, 1.1f, 0.93f, 1.08f, 0.91f, 1.13f, 0.95f)
        var prev = s.next(units, 15f * units)
        var rawMoves = 0f
        var shownMoves = 0f
        var prevRaw = 15f * units
        var prevUnits = units
        for (n in noise) {
            units -= 1f
            val raw = 15f * units * n
            val shown = s.next(units, raw)
            rawMoves += kotlin.math.abs(raw / (prevRaw * units / prevUnits) - 1f)
            shownMoves += kotlin.math.abs(shown / (prev * units / prevUnits) - 1f)
            prev = shown; prevRaw = raw; prevUnits = units
        }
        assertTrue("shown $shownMoves raw $rawMoves", shownMoves < rawMoves * 0.6f)
    }

    @Test
    fun startsAgainAfterAReset() {
        val s = LeftSmoother()
        s.next(40f, 600f)
        s.reset()
        assertEquals(1000f, s.next(10f, 1000f), 0.001f)
    }

    @Test
    fun betweenEstimatesTheTimeJustCountsDown() {
        val s = LeftSmoother()
        assertEquals(77f, s.scaled(10f, 77f), 0.001f)       // nothing yet: the fallback
        s.next(1000f, 100f)
        assertEquals(80f, s.scaled(800f, 999f), 0.001f)     // a fifth of the text read: a fifth of the time gone
        assertEquals(80f, s.scaled(800f, 5f), 0.001f)       // and asking does not change what is kept
    }
}
