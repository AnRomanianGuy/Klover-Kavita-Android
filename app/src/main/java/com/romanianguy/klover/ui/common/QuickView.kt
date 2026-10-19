package com.romanianguy.klover.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

// [current] asks the layout where the cover is now, since a card can move without being measured again; while held it
// swells, so then the resting rect stands
class LiveBounds(private val isPressed: () -> Boolean = { false }) {
    var rect: Rect = Rect.Zero
        private set
    private var coordinates: LayoutCoordinates? = null

    fun record(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        if (!isPressed()) rect = coordinates.boundsInRoot()
    }

    fun current(): Rect {
        val c = coordinates
        return if (c != null && c.isAttached && !isPressed()) c.boundsInRoot().also { rect = it } else rect
    }
}

// what a card needs to hand its cover to the quick view; SeriesCard has this built in, this is for other cards
// (Downloads tab)
@Stable
class QuickView internal constructor(
    val key: Any,
    val interaction: MutableInteractionSource,
    private val pressed: State<Boolean>,
    private val rest: LiveBounds,
    private val controller: PeekController?,
    private val allowed: Boolean,
) {
    val available: Boolean get() = allowed && controller != null

    val bounds: Rect get() = rest.current()

    val lifted: Boolean get() = controller?.activeKey == key

    fun boundsModifier(): Modifier = Modifier.onGloballyPositioned { rest.record(it) }

    fun hideModifier(): Modifier = Modifier.graphicsLayer { alpha = if (lifted) 0f else 1f }

    fun coverModifier(): Modifier = boundsModifier().then(hideModifier())

    fun show(request: PeekRequest) {
        controller?.show(request)
    }
}

@Composable
fun rememberQuickView(): QuickView {
    val controller = LocalPeekController.current
    val allowed = LocalAppSettings.current.holdToPeek
    val key = remember { Any() }
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    val rest = remember(pressed) { LiveBounds { pressed.value } }
    // if the card leaves while lifted, the quick view must not fly it home
    DisposableEffect(controller, key) { onDispose { controller?.cardGone(key) } }
    return remember(controller, allowed) { QuickView(key, interaction, pressed, rest, controller, allowed) }
}
