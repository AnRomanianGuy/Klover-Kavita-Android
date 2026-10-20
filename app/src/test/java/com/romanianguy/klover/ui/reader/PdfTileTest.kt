package com.romanianguy.klover.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTileTest {
    // a 1000 x 1000 area showing one page that fills it, 600 x 800 points
    private val area = Rect(0f, 0f, 1000f, 1000f)
    private val page = Size(600f, 800f)
    private val center = Offset(500f, 500f)

    private fun spec(scale: Float, offset: Offset = Offset.Zero, margin: Float = 0f, image: Rect = area, view: Rect = area) =
        tileSpec(image, page, view, center, scale, offset, margin)

    @Test
    fun notZoomedNeedsNoSharperPicture() {
        assertNull(spec(1f))
        assertNull(spec(SHARP_FROM - 0.01f))
    }

    @Test
    fun zoomedInTwiceTheMiddleHalfIsRenderedAtDoubleSize() {
        val t = spec(2f)!!
        assertEquals(Rect(250f, 250f, 750f, 750f), t.area)
        // 500 area pixels on screen are 1000 screen pixels
        assertEquals(1000, t.pixels.width)
        assertEquals(1000, t.pixels.height)
        // the same part of a 600 x 800 point page
        assertEquals(Rect(150f, 200f, 450f, 600f), t.points)
    }

    @Test
    fun panningMovesTheRenderedPartTheOtherWay() {
        // the picture is moved 200 px to the left, so what is on screen is further right in the page
        val t = spec(2f, offset = Offset(-200f, 0f))!!
        assertEquals(350f, t.area.left, 0.001f)
        assertEquals(850f, t.area.right, 0.001f)
    }

    @Test
    fun aMarginKeepsASmallPanSharp() {
        val plain = spec(2f)!!
        val padded = spec(2f, margin = 0.1f)!!
        assertTrue(padded.area.width > plain.area.width)
        assertEquals(plain.area.center, padded.area.center)
    }

    @Test
    fun theRenderedPartNeverGoesPastThePage() {
        // the page only fills the left half of the area
        val image = Rect(0f, 0f, 500f, 1000f)
        val t = spec(2f, margin = 0.2f, image = image)!!
        assertTrue(t.area.right <= 500f)
        assertTrue(t.points.right <= page.width + 0.001f)
    }

    @Test
    fun aPartThatIsOffTheScreenHasNothingToRender() {
        assertNull(spec(2f, image = Rect(0f, 0f, 100f, 100f), offset = Offset(0f, 0f)))
    }

    @Test
    fun aHugeRenderIsShrunkToTheMemoryBudget() {
        val t = tileSpec(area, page, area, center, 5f, Offset.Zero, margin = 0f, maxPixels = 1_000_000L)!!
        assertTrue(t.pixels.width.toLong() * t.pixels.height <= 1_100_000L)
        // the shape is kept
        assertEquals(t.area.width / t.area.height, t.pixels.width.toFloat() / t.pixels.height, 0.01f)
    }
}
