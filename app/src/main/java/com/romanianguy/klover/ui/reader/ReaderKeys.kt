package com.romanianguy.klover.ui.reader

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

// volume down is next, up is previous (consumed so the system volume stays); refocusOn re-claims focus since a tap or
// dialog can take it
@Composable
fun Modifier.volumeKeyPaging(
    enabled: Boolean,
    refocusOn: Any?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    if (!enabled) return this
    val focus = remember { FocusRequester() }
    LaunchedEffect(refocusOn) { runCatching { focus.requestFocus() } }
    return this
        .focusRequester(focus)
        .focusable()
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.VolumeDown -> {
                    if (event.type == KeyEventType.KeyDown) onNext()
                    true
                }

                Key.VolumeUp -> {
                    if (event.type == KeyEventType.KeyDown) onPrevious()
                    true
                }

                else -> false
            }
        }
}
