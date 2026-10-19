package com.romanianguy.klover.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.romanianguy.klover.data.prefs.AppFont

val KloverTypography = Typography()

fun kloverTypography(font: AppFont): Typography {
    val family = when (font) {
        AppFont.Default -> return KloverTypography
        AppFont.Serif -> FontFamily.Serif
        AppFont.Mono -> FontFamily.Monospace
    }
    fun TextStyle.f() = copy(fontFamily = family)
    val t = KloverTypography
    return t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
        displayLargeEmphasized = t.displayLargeEmphasized.f(), displayMediumEmphasized = t.displayMediumEmphasized.f(),
        displaySmallEmphasized = t.displaySmallEmphasized.f(), headlineLargeEmphasized = t.headlineLargeEmphasized.f(),
        headlineMediumEmphasized = t.headlineMediumEmphasized.f(), headlineSmallEmphasized = t.headlineSmallEmphasized.f(),
        titleLargeEmphasized = t.titleLargeEmphasized.f(), titleMediumEmphasized = t.titleMediumEmphasized.f(),
        titleSmallEmphasized = t.titleSmallEmphasized.f(), bodyLargeEmphasized = t.bodyLargeEmphasized.f(),
        bodyMediumEmphasized = t.bodyMediumEmphasized.f(), bodySmallEmphasized = t.bodySmallEmphasized.f(),
        labelLargeEmphasized = t.labelLargeEmphasized.f(), labelMediumEmphasized = t.labelMediumEmphasized.f(),
        labelSmallEmphasized = t.labelSmallEmphasized.f(),
    )
}
