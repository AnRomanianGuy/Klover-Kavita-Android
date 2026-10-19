package com.romanianguy.klover.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class TapZoneTest {
    private val size = IntSize(1000, 2000)

    private fun zone(x: Float, enabled: Boolean = true, inverted: Boolean = false) =
        tapZone(Offset(x, 500f), size, enabled, inverted)

    @Test
    fun edgesTurnAndTheMiddleTogglesControls() {
        assertEquals(TapZone.Left, zone(100f))
        assertEquals(TapZone.Center, zone(500f))
        assertEquals(TapZone.Right, zone(900f))
    }

    @Test
    fun invertingSwapsOnlyTheSides() {
        assertEquals(TapZone.Right, zone(100f, inverted = true))
        assertEquals(TapZone.Left, zone(900f, inverted = true))
        assertEquals(TapZone.Center, zone(500f, inverted = true))
    }

    @Test
    fun withTapZonesOffEveryTapIsACenterTap() {
        assertEquals(TapZone.Center, zone(100f, enabled = false))
        assertEquals(TapZone.Center, zone(900f, enabled = false, inverted = true))
    }

    @Test
    fun anEmptyViewportNeverTurnsPages() {
        assertEquals(TapZone.Center, tapZone(Offset(0f, 0f), IntSize.Zero))
    }

    @Test
    fun theStripSizeDecidesWhereTurningStarts() {
        val at = Offset(250f, 500f) // a quarter of the way across
        assertEquals(TapZone.Center, tapZone(at, size, edge = 0.2f))
        assertEquals(TapZone.Left, tapZone(at, size, edge = 0.4f))
        assertEquals(TapZone.Right, tapZone(Offset(750f, 500f), size, edge = 0.4f))
    }
}
