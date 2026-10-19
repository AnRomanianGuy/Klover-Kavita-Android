package com.romanianguy.klover.data.garden

import com.romanianguy.klover.data.history.HistoryEntry
import com.romanianguy.klover.data.history.withFinishedChapter
import com.romanianguy.klover.data.stats.DayBar
import com.romanianguy.klover.data.stats.StatsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GardenTest {
    private val utc = ZoneId.of("UTC")
    private fun at(hour: Int, day: Int = 18) = LocalDate.of(2026, 10, day).atStartOfDay(utc).toInstant().toEpochMilli() + hour * 3_600_000L
    private fun entry(hour: Int, series: String = "S", day: Int = 18) = HistoryEntry(1, 1, series, "c", at(hour, day))

    private fun entries(count: Int, from: Long = 1_000_000L) =
        (0 until count).map { HistoryEntry(100 + it, 1, "S", "c$it", from + it * 3_600_000L) }

    @Test
    fun theSameChapterAtTheSameMomentAlwaysGivesTheSameLuck() {
        val e = HistoryEntry(7, 1, "S", "c", 123_456_789L)
        assertEquals(luck(e), luck(e.copy(title = "other")), 0.0)
        assertTrue(luck(e) in 0.0..1.0)
        assertTrue(luck(e) != luck(e.copy(at = e.at + 1)))
    }

    @Test
    fun chanceDecidesButTheGuaranteesCatchEveryDrySpell() {
        // 5000 chapters in a row: never more than 15 without a four (or better), nor more than 55 without a five
        val leaves = cloverLeaves(entries(5000))
        var sinceFour = 0
        var sinceFive = 0
        var worstFour = 0
        var worstFive = 0
        for (n in leaves) {
            sinceFour = if (n >= 4) 0 else sinceFour + 1
            sinceFive = if (n >= 5) 0 else sinceFive + 1
            worstFour = maxOf(worstFour, sinceFour)
            worstFive = maxOf(worstFive, sinceFive)
        }
        assertTrue("four-leaf drought $worstFour", worstFour <= FOUR_LEAF_GUARANTEE - 1)
        assertTrue("five-leaf drought $worstFive", worstFive <= FIVE_LEAF_GUARANTEE - 1)
    }

    @Test
    fun mostCloversAreThreeLeafAndTheRarerOnesTurnUpNowAndThen() {
        val leaves = cloverLeaves(entries(5000))
        val threes = leaves.count { it == 3 }
        val fours = leaves.count { it == 4 }
        val fives = leaves.count { it == 5 }
        assertTrue("threes $threes", threes > 3500)
        assertTrue("fours $fours", fours in 150..700)
        assertTrue("fives $fives", fives in 50..250)
        assertTrue(fives < fours)
    }

    @Test
    fun theFifteenthChapterAtTheLatestGivesAFourLeafClover() {
        // whatever the luck, the first 15 hold a four-leaf or better
        for (start in listOf(1L, 99_999L, 5_000_000_000L)) {
            assertTrue(cloverLeaves(entries(15, start)).any { it >= 4 })
        }
    }

    @Test
    fun theFiftyFifthChapterAtTheLatestGivesAFiveLeafClover() {
        for (start in listOf(1L, 99_999L, 5_000_000_000L)) {
            assertTrue(cloverLeaves(entries(55, start)).any { it == 5 })
        }
    }

    @Test
    fun aCloverKeptWithItsEntryIsNeverRolledAgain() {
        val kept = entries(3).mapIndexed { i, e -> e.copy(leaves = if (i == 1) 5 else 3) }
        assertEquals(listOf(3, 5, 3), cloverLeaves(kept))
    }

    @Test
    fun theNextCloverFollowsWhatWasKept() {
        // 14 threes: the 15th is certain to be a four (or better), whatever its roll
        val pity = pityOf(List(14) { 3 })
        assertEquals(4, nextLeaves(pity.sinceFour, pity.sinceFive, 0.99))
        assertEquals(3, nextLeaves(0, 0, 0.99))
        assertEquals(4, nextLeaves(0, 0, 0.05))
        assertEquals(5, nextLeaves(0, 0, 0.001))
        assertEquals(5, nextLeaves(3, 54, 0.99))
    }

    @Test
    fun theGardenCountsEachKindAndTheDistanceToTheGuarantees() {
        val g = gardenOf(listOf(3, 3, 4, 3, 3, 5, 3, 3))
        assertEquals(8, g.clovers)
        assertEquals(1, g.fourLeaf)
        assertEquals(1, g.fiveLeaf)
        assertEquals(6, g.threeLeaf)
        assertEquals(FOUR_LEAF_GUARANTEE - 2, g.untilFourGuaranteed)
        assertEquals(FIVE_LEAF_GUARANTEE - 2, g.untilFiveGuaranteed)
        assertEquals(FOUR_LEAF_GUARANTEE, gardenOf(emptyList()).untilFourGuaranteed)
    }

    @Test
    fun aChapterFlickedThroughIsNotReadButAFairShareOfTheTimeIs() {
        // a 20-page manga chapter at 8 s a page takes 160 s: 30% of that is 48 s
        assertFalse(wasReadProperly(spentSeconds = 12f, expectedSeconds = 160f))
        assertFalse(wasReadProperly(spentSeconds = 47f, expectedSeconds = 160f))
        assertTrue(wasReadProperly(spentSeconds = 48f, expectedSeconds = 160f))
        assertTrue(wasReadProperly(spentSeconds = 300f, expectedSeconds = 160f))
        // nothing to judge by: taken as read
        assertTrue(wasReadProperly(0f, null))
        assertTrue(wasReadProperly(0f, 0f))
    }

    @Test
    fun skimmedChaptersGrowNoCloverAndCountForNothing() {
        val all = entries(6).mapIndexed { i, e -> e.copy(leaves = 3, read = i % 2 == 0) } // 3 read, 3 skimmed
        assertEquals(3, cloverLeaves(all).size)
        assertEquals(3, gardenOf(cloverLeaves(all)).clovers)
        assertEquals(3, badgeFacts(all, StatsSummary(), 0L, 0).chapters)
        val now = all.last().at
        assertEquals(3, recapOf(all, StatsSummary(), now).chapters)
    }

    @Test
    fun aSkimGrowsNothingAndNeverTakesAwayAClover() {
        val read = HistoryEntry(7, 1, "S", "c", 1_000_000L, 10, leaves = 0, read = true)
        val first = withFinishedChapter(emptyList(), read)
        assertTrue(first.single().leaves in 3..5)
        // flicked through again within the sitting: the entry (and its clover) stays
        val again = withFinishedChapter(first, read.copy(at = read.at + 60_000, read = false))
        assertEquals(first, again)
        // a skim of a chapter never read is listed, with no clover
        val skim = withFinishedChapter(emptyList(), read.copy(read = false))
        assertEquals(0, skim.single().leaves)
        assertFalse(skim.single().read)
        // reading it properly afterwards replaces the skim and rolls a clover
        val later = withFinishedChapter(skim, read.copy(at = read.at + 3_600_000))
        assertEquals(1, later.size)
        assertTrue(later.single().read)
        assertTrue(later.single().leaves in 3..5)
    }

    private fun facts(
        chapters: Int = 0,
        best: Int = 0,
        day: Long = 0,
        total: Long = 0,
        night: Boolean = false,
        early: Boolean = false,
        goal: Int = 0,
    ) =
        BadgeFacts(chapters, best, best, day, total, night, early, goal)

    @Test
    fun chapterBadgesComeWithTheirCounts() {
        val f = facts(chapters = 12)
        assertTrue(Badge.FirstChapter.earned(f))
        assertTrue(Badge.TenChapters.earned(f))
        assertFalse(Badge.FiftyChapters.earned(f))
    }

    @Test
    fun streakAndTimeBadges() {
        assertTrue(Badge.Streak7.earned(facts(best = 7)))
        assertFalse(Badge.Streak30.earned(facts(best = 29)))
        assertTrue(Badge.LongDay.earned(facts(day = 2 * 3600)))
        assertFalse(Badge.LongDay.earned(facts(day = 2 * 3600 - 1)))
        assertTrue(Badge.TenHours.earned(facts(total = 10 * 3600)))
    }

    @Test
    fun nightOwlAndEarlyBirdComeFromWhenChaptersWereFinished() {
        val summary = StatsSummary()
        val owl = badgeFacts(listOf(entry(2)), summary, 0, 0, utc)
        assertTrue(owl.finishedAtNight)
        assertFalse(owl.finishedEarly)
        val bird = badgeFacts(listOf(entry(5)), summary, 0, 0, utc)
        assertTrue(bird.finishedEarly)
        val noon = badgeFacts(listOf(entry(12)), summary, 0, 0, utc)
        assertFalse(noon.finishedAtNight || noon.finishedEarly)
    }

    @Test
    fun onlyWhatWasNotEarnedBeforeIsNew() {
        val before = facts(chapters = 9)
        val after = facts(chapters = 10)
        assertEquals(listOf(Badge.TenChapters), newlyEarned(before, after))
        assertTrue(newlyEarned(after, after).isEmpty())
    }

    @Test
    fun theWeeklyGoalFillsAndCapsAtOne() {
        assertEquals(0.5f, goalProgress(60 * 60, 120), 0.001f)
        assertEquals(1f, goalProgress(500 * 60, 120), 0.001f)
        assertEquals(0f, goalProgress(1000, 0), 0f)
        assertEquals(45, goalMinutesLeft(75 * 60, 120))
        assertEquals(0, goalMinutesLeft(200 * 60, 120))
        assertEquals(0, goalMinutesLeft(10, 0))
    }

    @Test
    fun theRecapCountsTheLastSevenDays() {
        val today = LocalDate.of(2026, 10, 18)
        val summary = StatsSummary(
            weekSeconds = 150 * 60,
            last7 = (6 downTo 0).map { DayBar(today.minusDays(it.toLong()), if (it == 1) 90 * 60L else if (it == 0) 60 * 60L else 0L) },
        )
        val history = listOf(
            entry(10, "Lanterns", 18),
            entry(11, "Lanterns", 17),
            entry(9, "Orchard", 17),
            entry(9, "Old", 1),
        )
        val recap = recapOf(history, summary, at(12), utc)
        assertEquals(150, recap.minutes)
        assertEquals(3, recap.chapters)
        assertEquals(90, recap.bestDayMinutes)
        assertEquals("Lanterns", recap.topSeries)
        assertEquals(2, recap.activeDays)
    }

    @Test
    fun theFirstLookKeepsWhatIsEarnedSilently() {
        val update = updateBadges(emptyList(), setOf(Badge.FirstChapter, Badge.TenChapters), celebrate = true)!!
        assertTrue(update.celebrate.isEmpty())
        assertTrue(Badge.TenChapters.name in update.seen && Badge.FirstChapter.name in update.seen)
    }

    @Test
    fun aBadgeEarnedLaterIsCelebratedOnce() {
        val seen = listOf(INITIALIZED, Badge.FirstChapter.name)
        val update = updateBadges(seen, setOf(Badge.FirstChapter, Badge.TenChapters), celebrate = true)!!
        assertEquals(listOf(Badge.TenChapters), update.celebrate)
        // and not again once it is kept
        assertEquals(null, updateBadges(update.seen, setOf(Badge.FirstChapter, Badge.TenChapters), celebrate = true))
    }

    @Test
    fun withTheGardenSwitchedOffBadgesAreKeptButNotCelebrated() {
        val seen = listOf(INITIALIZED)
        val update = updateBadges(seen, setOf(Badge.FirstChapter), celebrate = false)!!
        assertTrue(update.celebrate.isEmpty())
        assertTrue(Badge.FirstChapter.name in update.seen)
        // turning it on again does not bring them back as news
        assertEquals(null, updateBadges(update.seen, setOf(Badge.FirstChapter), celebrate = true))
    }

    @Test
    fun severalBadgesAtOnceAreAllCelebrated() {
        val seen = listOf(INITIALIZED)
        val update = updateBadges(seen, setOf(Badge.FirstChapter, Badge.Streak3), celebrate = true)!!
        assertEquals(setOf(Badge.FirstChapter, Badge.Streak3), update.celebrate.toSet())
    }

    @Test
    fun theGoalCountsOnlyWhenThereIsOne() {
        assertTrue(goalMet(120 * 60L, 120))
        assertFalse(goalMet(119 * 60L, 120))
        assertFalse(goalMet(10_000, 0))
    }
}
