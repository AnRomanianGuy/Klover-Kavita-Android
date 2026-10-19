package com.romanianguy.klover.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.MotionStyle

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

// the tapped cover may fly to the series page: wanted in Settings, and not under Reduced motion
fun AppSettings.flightEnabled(): Boolean = coverFlight && !reducedMotion()

fun AppSettings.reducedMotion(): Boolean =
    motionStyle == MotionStyle.Reduced
