package com.romanianguy.klover.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.prefs.Accent
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.prefs.ThemeMode
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.events.ProvideActiveEvent

@Composable
fun isAppDark(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KloverTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = isAppDark(settings)
    val context = LocalContext.current
    val seed = if (settings.accent == Accent.Custom) hueSeed(settings.accentHue) else settings.accent.seed
    val contrast = settings.contrast.level

    // Generating a palette is not free, so it is only redone when the accent, contrast or light / dark mode changes.
    val colorScheme: ColorScheme = remember(seed, dark, settings.amoled, contrast, context) {
        val base = when {
            // Wallpaper colours (Material You exists on every supported version).
            seed == null ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> accentColorScheme(seed ?: Accent.Clover.seed!!, dark, contrast)
        }
        if (dark && settings.amoled) base.asAmoled() else base
    }

    val shapes = remember(settings.cornerStyle) { scaledShapes(settings.cornerStyle.scale) }
    val typography = remember(settings.appFont) { kloverTypography(settings.appFont) }
    // The app's text size on top of the system's: every sp in the app follows it.
    val density = LocalDensity.current
    val scaledDensity = remember(density, settings.textScale) { Density(density.density, density.fontScale * settings.textScale.factor) }

    CompositionLocalProvider(LocalAppSettings provides settings, LocalDensity provides scaledDensity) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            // Calm and Reduced use the standard (non-bouncy) motion tokens everywhere in the app.
            motionScheme = if (settings.motionStyle == MotionStyle.Bouncy) MotionScheme.expressive() else MotionScheme.standard(),
            typography = typography,
            content = { ProvideActiveEvent(content) },
        )
    }
}

private fun scaledShapes(scale: Float): Shapes = Shapes(
    extraSmall = RoundedCornerShape((4 * scale).dp),
    small = RoundedCornerShape((8 * scale).dp),
    medium = RoundedCornerShape((12 * scale).dp),
    large = RoundedCornerShape((16 * scale).dp),
    extraLarge = RoundedCornerShape((28 * scale).dp),
)
