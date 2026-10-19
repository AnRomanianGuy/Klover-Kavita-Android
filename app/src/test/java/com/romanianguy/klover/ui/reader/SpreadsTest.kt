package com.romanianguy.klover.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsTest {
    private fun List<Spread>.asPages() = map { it.pages }

    @Test
    fun singleModeIsOnePagePerScreen() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), buildSpreads(3, dual = false).asPages())
    }

    @Test
    fun dualModeKeepsCoverAloneThenPairs() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)),
            buildSpreads(6, dual = true).asPages(),
        )
    }

    @Test
    fun dualModeWithoutCoverPairsFromTheStart() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3)),
            buildSpreads(4, dual = true, coverAlone = false).asPages(),
        )
    }

    @Test
    fun widePageTakesAWholeScreenAndShiftsLaterPairs() {
        // pages: 0 cover, 1 normal, 2 WIDE, 3 normal, 4 normal, 5 normal
        val spreads = buildSpreads(6, dual = true) { it == 2 }
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4), listOf(5)), spreads.asPages())
    }

    @Test
    fun pageBeforeAWidePageIsNotPairedWithIt() {
        val spreads = buildSpreads(5, dual = true) { it == 3 }
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4)), spreads.asPages())
    }

    @Test
    fun everyPageAppearsExactlyOnceInOrder() {
        val wide = setOf(4, 9)
        val spreads = buildSpreads(20, dual = true) { it in wide }
        assertEquals((0 until 20).toList(), spreads.flatMap { it.pages })
    }

    @Test
    fun emptyAndSinglePageDocuments() {
        assertTrue(buildSpreads(0, dual = true).isEmpty())
        assertEquals(listOf(listOf(0)), buildSpreads(1, dual = true).asPages())
    }

    @Test
    fun indexOfPageSurvivesSwitchingBetweenModes() {
        val dual = buildSpreads(10, dual = true)
        val single = buildSpreads(10, dual = false)
        // Looking at the spread with pages 3 and 4, then folding: we land on that spread's first page (3),
        // so the reader never jumps to something the user had not just seen.
        val dualIndex = dual.indexOfPage(4)
        assertEquals(listOf(3, 4), dual[dualIndex].pages)
        assertEquals(3, single[single.indexOfPage(dual[dualIndex].first)].first)
        assertEquals(0, dual.indexOfPage(-5))
        assertEquals(dual.lastIndex, dual.indexOfPage(999))
    }

    @Test
    fun aChapterStartNeverJoinsThePageBeforeIt() {
        // 12 pages, chapters starting at 0, 4 and 8: pairing would put pages 3 and 4 together.
        val spreads = buildSpreads(12, dual = true, breaks = setOf(0, 4, 8))
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5), listOf(6, 7), listOf(8, 9), listOf(10, 11)),
            spreads.asPages(),
        )
    }
}
