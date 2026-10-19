package com.romanianguy.klover.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.romanianguy.klover.data.prefs.Accent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentSchemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    // Custom has no colour of its own: it follows the hue slider.
    private val seeds = Accent.entries.filter { it != Accent.Custom }.mapNotNull { it.seed }

    @Test
    fun everyAccentKeepsReadableContrastInLightAndDark() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = accentColorScheme(seed, dark)
            val label = "seed=%08X dark=%s".format(seed, dark)
            assertTrue("$label primary", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
            assertTrue("$label container", contrast(scheme.primaryContainer, scheme.onPrimaryContainer) >= 4.5f)
            assertTrue("$label surface", contrast(scheme.surface, scheme.onSurface) >= 4.5f)
        }
    }

    @Test
    fun differentAccentsGiveDifferentPrimaries() {
        val primaries = seeds.map { accentColorScheme(it, dark = false).primary }.toSet()
        assertEquals(seeds.size, primaries.size)
    }

    @Test
    fun darkAndLightSchemesDiffer() {
        val seed = Accent.Ocean.seed!!
        assertNotEquals(accentColorScheme(seed, false).surface, accentColorScheme(seed, true).surface)
    }

    @Test
    fun amoledMakesTheBackgroundsBlackButLeavesTheAccentAlone() {
        val dark = accentColorScheme(Accent.Violet.seed!!, dark = true)
        val amoled = dark.asAmoled()
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(dark.primary, amoled.primary)
        // Cards still step up from the black so they stay distinguishable.
        assertTrue(amoled.surfaceContainerHighest.luminance() > amoled.surfaceContainerLow.luminance())
        assertTrue(contrast(amoled.surface, amoled.onSurface) >= 7f)
    }
}
