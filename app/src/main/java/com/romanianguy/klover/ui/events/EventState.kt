package com.romanianguy.klover.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
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
