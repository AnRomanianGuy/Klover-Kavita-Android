package com.romanianguy.klover.ui.events

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.romanianguy.klover.data.events.ActiveEvent
import com.romanianguy.klover.data.events.SeasonalEvents
import com.romanianguy.klover.ui.common.LocalAppSettings
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

// the event that is on now, or null: none is on, or the person turned events off
val LocalActiveEvent = staticCompositionLocalOf<ActiveEvent?> { null }

@Composable
fun ProvideActiveEvent(content: @Composable () -> Unit) {
    val enabled = LocalAppSettings.current.seasonalEvents
    // the date is looked at again after midnight, for an app that stays open over the day an event starts or ends
    val today by produceState(LocalDate.now()) {
        while (true) {
            val now = LocalDateTime.now()
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
            value = LocalDate.now()
        }
    }
    val active = if (enabled) SeasonalEvents.active(today) else null
    CompositionLocalProvider(LocalActiveEvent provides active, content = content)
}

// while an event is on, the screens inside this let what is drawn behind them show: their background is clear, and the
// app's own background lies under it all, so nothing looks different. It is the same composition either way, so a screen
// keeps its state when it switches (when the reader opens, say)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EventBackdrop(clear: Boolean, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shown = if (clear) scheme.copy(background = Color.Transparent) else scheme
    MaterialExpressiveTheme(
        colorScheme = shown,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}
