package com.romanianguy.klover.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.preferencesOf
import com.romanianguy.klover.data.prefs.AppFont
import com.romanianguy.klover.data.prefs.ContrastLevel
import com.romanianguy.klover.data.prefs.CornerStyle
import com.romanianguy.klover.data.prefs.GridSpacing
import com.romanianguy.klover.data.prefs.NavLabels
import com.romanianguy.klover.data.prefs.PrefKeys
import com.romanianguy.klover.data.prefs.TextScale
import com.romanianguy.klover.data.prefs.toAppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalisationTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun everyHueGivesReadableLightAndDarkSchemesAtEveryContrast() {
        for (hue in listOf(0f, 40f, 90f, 150f, 200f, 260f, 310f, 360f)) for (level in ContrastLevel.entries) for (dark in listOf(false, true)) {
            val scheme = accentColorScheme(hueSeed(hue), dark, level.level)
            val label = "hue=$hue contrast=$level dark=$dark"
            assertTrue("$label primary", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
            assertTrue("$label surface", contrast(scheme.surface, scheme.onSurface) >= 4.5f)
        }
    }

    @Test
    fun higherContrastSeparatesTextFromItsBackgroundAtLeastAsWell() {
        val seed = hueSeed(200f)
        val standard = accentColorScheme(seed, dark = false, contrast = ContrastLevel.Standard.level)
        val high = accentColorScheme(seed, dark = false, contrast = ContrastLevel.High.level)
        assertTrue(contrast(high.primaryContainer, high.onPrimaryContainer) >= contrast(standard.primaryContainer, standard.onPrimaryContainer))
    }

    @Test
    fun differentHuesAreDifferentColours() {
        assertNotEquals(hueSeed(30f), hueSeed(210f))
        // Out-of-range hues are clamped, not crashed on.
        assertEquals(hueSeed(360f), hueSeed(999f))
        assertEquals(hueSeed(0f), hueSeed(-5f))
    }

    @Test
    fun theTypefaceChoiceChangesEveryTextRole() {
        val plain = kloverTypography(AppFont.Default)
        val serif = kloverTypography(AppFont.Serif)
        assertEquals(KloverTypography, plain)
        assertNotEquals(plain.bodyLarge.fontFamily, serif.bodyLarge.fontFamily)
        assertNotEquals(plain.titleLargeEmphasized.fontFamily, serif.titleLargeEmphasized.fontFamily)
    }

    @Test
    fun theOptionsRoundTripAndFallBackToDefaults() {
        val defaults = preferencesOf().toAppSettings()
        assertEquals(CornerStyle.Standard, defaults.cornerStyle)
        assertEquals(TextScale.Default, defaults.textScale)
        assertEquals(NavLabels.Selected, defaults.navLabels)
        assertTrue(defaults.showSubtitles)

        val s = preferencesOf(
            PrefKeys.CORNER_STYLE to CornerStyle.Round.name,
            PrefKeys.APP_FONT to AppFont.Mono.name,
            PrefKeys.TEXT_SCALE to TextScale.Huge.name,
            PrefKeys.NAV_LABELS to NavLabels.Never.name,
            PrefKeys.GRID_SPACING to GridSpacing.Relaxed.name,
            PrefKeys.CONTRAST to ContrastLevel.High.name,
            PrefKeys.ACCENT_HUE to 500f,
            PrefKeys.SHOW_SUBTITLES to false,
        ).toAppSettings()
        assertEquals(CornerStyle.Round, s.cornerStyle)
        assertEquals(AppFont.Mono, s.appFont)
        assertEquals(TextScale.Huge, s.textScale)
        assertEquals(NavLabels.Never, s.navLabels)
        assertEquals(GridSpacing.Relaxed, s.gridSpacing)
        assertEquals(ContrastLevel.High, s.contrast)
        assertEquals(360f, s.accentHue, 0f)
        assertEquals(false, s.showSubtitles)
    }

    @Test
    fun theScalesAreOrdered() {
        assertTrue(CornerStyle.Sharp.scale < CornerStyle.Standard.scale && CornerStyle.Standard.scale < CornerStyle.Round.scale)
        assertTrue(TextScale.Small.factor < 1f && TextScale.Huge.factor > TextScale.Large.factor)
        assertTrue(GridSpacing.Compact.dp < GridSpacing.Normal.dp && GridSpacing.Normal.dp < GridSpacing.Relaxed.dp)
    }
}
