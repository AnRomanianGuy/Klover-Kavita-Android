package com.romanianguy.klover.data.covers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCheckTest {
    private val w = 64
    private val h = 96
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun fill(f: (x: Int, y: Int) -> Int) = IntArray(w * h) { f(it % w, it / w) }

    @Test
    fun aWhitePageWithTextHazeIsATextPage() {
        // shrunk text is a light grey haze in lines, never solid black
        val page = fill { _, y -> if (y % 6 in 1..2 && y > 12) argb(205, 205, 205) else argb(250, 250, 250) }
        assertTrue(looksLikeTextPage(page))
    }

    @Test
    fun aBlankWhitePageIsOneToo() {
        assertTrue(looksLikeTextPage(fill { _, _ -> argb(255, 255, 255) }))
    }

    @Test
    fun anOffWhiteScannedPageIsOneToo() {
        assertTrue(looksLikeTextPage(fill { _, y -> if (y % 7 == 0) argb(190, 185, 175) else argb(238, 233, 222) }))
    }

    @Test
    fun aColourCoverIsNot() {
        assertFalse(looksLikeTextPage(fill { x, y -> argb(40 + x * 2, 120, 200 - y) }))
    }

    @Test
    fun aDarkCoverIsNot() {
        assertFalse(looksLikeTextPage(fill { _, _ -> argb(20, 20, 25) }))
    }

    @Test
    fun aBlackAndWhiteMangaCoverWithHeavyBlackIsNot() {
        // a quarter of it solid black
        assertFalse(looksLikeTextPage(fill { x, _ -> if (x < 16) argb(10, 10, 10) else argb(245, 245, 245) }))
    }

    @Test
    fun aWhiteCoverWithAColouredTitleBlockIsNot() {
        assertFalse(looksLikeTextPage(fill { _, y -> if (y in 30..60) argb(200, 40, 60) else argb(250, 250, 250) }))
    }

    @Test
    fun nothingToLookAtIsNotAPage() {
        assertFalse(looksLikeTextPage(IntArray(0)))
    }
}
