package com.romanianguy.klover.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRenderSizeTest {
    @Test
    fun anOrdinaryPageIsRenderedAtTheFullWidth() {
        // A4 in points.
        assertEquals(1500 to 2122, pdfRenderSize(595, 842))
    }

    @Test
    fun aVeryTallPageShrinksInsteadOfGrowingWithoutLimit() {
        val (w, h) = pdfRenderSize(600, 60_000)
        assertEquals(6000, h)
        assertTrue(w in 1..1500)
        // The shape is kept (to within a pixel of rounding).
        assertEquals(600.0 / 60_000, w.toDouble() / h, 0.0005)
    }

    @Test
    fun aPageWithNoSizeDoesNotDivideByZero() {
        val (w, h) = pdfRenderSize(0, 0)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test
    fun aWidePageIsAsWideAndAsFlatAsItsShapeSays() {
        assertEquals(1500 to 1059, pdfRenderSize(842, 595))
    }
}
