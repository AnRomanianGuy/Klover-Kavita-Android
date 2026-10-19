@file:SuppressLint("RestrictedApi")

package com.romanianguy.klover.ui.theme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

// full M3 scheme from one seed with Google's tonal palette algorithm, so contrast holds whatever the seed
fun accentColorScheme(seed: Int, dark: Boolean, contrast: Double = 0.0): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, contrast)
    val roles = MaterialDynamicColors()
    fun role(color: DynamicColor) = Color(color.getArgb(scheme))

    return if (dark) {
        darkColorScheme(
            primary = role(roles.primary()),
            onPrimary = role(roles.onPrimary()),
            primaryContainer = role(roles.primaryContainer()),
            onPrimaryContainer = role(roles.onPrimaryContainer()),
            inversePrimary = role(roles.inversePrimary()),
            secondary = role(roles.secondary()),
            onSecondary = role(roles.onSecondary()),
            secondaryContainer = role(roles.secondaryContainer()),
            onSecondaryContainer = role(roles.onSecondaryContainer()),
            tertiary = role(roles.tertiary()),
            onTertiary = role(roles.onTertiary()),
            tertiaryContainer = role(roles.tertiaryContainer()),
            onTertiaryContainer = role(roles.onTertiaryContainer()),
            background = role(roles.background()),
            onBackground = role(roles.onBackground()),
            surface = role(roles.surface()),
            onSurface = role(roles.onSurface()),
            surfaceVariant = role(roles.surfaceVariant()),
            onSurfaceVariant = role(roles.onSurfaceVariant()),
            surfaceTint = role(roles.surfaceTint()),
            inverseSurface = role(roles.inverseSurface()),
            inverseOnSurface = role(roles.inverseOnSurface()),
            error = role(roles.error()),
            onError = role(roles.onError()),
            errorContainer = role(roles.errorContainer()),
            onErrorContainer = role(roles.onErrorContainer()),
            outline = role(roles.outline()),
            outlineVariant = role(roles.outlineVariant()),
            scrim = role(roles.scrim()),
            surfaceBright = role(roles.surfaceBright()),
            surfaceDim = role(roles.surfaceDim()),
            surfaceContainer = role(roles.surfaceContainer()),
            surfaceContainerHigh = role(roles.surfaceContainerHigh()),
            surfaceContainerHighest = role(roles.surfaceContainerHighest()),
            surfaceContainerLow = role(roles.surfaceContainerLow()),
            surfaceContainerLowest = role(roles.surfaceContainerLowest()),
        )
    } else {
        lightColorScheme(
            primary = role(roles.primary()),
            onPrimary = role(roles.onPrimary()),
            primaryContainer = role(roles.primaryContainer()),
            onPrimaryContainer = role(roles.onPrimaryContainer()),
            inversePrimary = role(roles.inversePrimary()),
            secondary = role(roles.secondary()),
            onSecondary = role(roles.onSecondary()),
            secondaryContainer = role(roles.secondaryContainer()),
            onSecondaryContainer = role(roles.onSecondaryContainer()),
            tertiary = role(roles.tertiary()),
            onTertiary = role(roles.onTertiary()),
            tertiaryContainer = role(roles.tertiaryContainer()),
            onTertiaryContainer = role(roles.onTertiaryContainer()),
            background = role(roles.background()),
            onBackground = role(roles.onBackground()),
            surface = role(roles.surface()),
            onSurface = role(roles.onSurface()),
            surfaceVariant = role(roles.surfaceVariant()),
            onSurfaceVariant = role(roles.onSurfaceVariant()),
            surfaceTint = role(roles.surfaceTint()),
            inverseSurface = role(roles.inverseSurface()),
            inverseOnSurface = role(roles.inverseOnSurface()),
            error = role(roles.error()),
            onError = role(roles.onError()),
            errorContainer = role(roles.errorContainer()),
            onErrorContainer = role(roles.onErrorContainer()),
            outline = role(roles.outline()),
            outlineVariant = role(roles.outlineVariant()),
            scrim = role(roles.scrim()),
            surfaceBright = role(roles.surfaceBright()),
            surfaceDim = role(roles.surfaceDim()),
            surfaceContainer = role(roles.surfaceContainer()),
            surfaceContainerHigh = role(roles.surfaceContainerHigh()),
            surfaceContainerHighest = role(roles.surfaceContainerHighest()),
            surfaceContainerLow = role(roles.surfaceContainerLow()),
            surfaceContainerLowest = role(roles.surfaceContainerLowest()),
        )
    }
}

// true-black backgrounds with surfaces stepping up from there; accents untouched. Works for generated and dynamic
// schemes
fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF191919),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceBright = Color(0xFF2A2A2A),
)

fun hueSeed(hue: Float): Int = Color.hsv(hue.coerceIn(0f, 360f), 0.62f, 0.78f).toArgb()
