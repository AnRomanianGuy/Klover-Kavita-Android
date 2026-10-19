package com.romanianguy.klover.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZoomableTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setUp(state: ZoomState, onTap: () -> Unit = {}) {
        rule.setContent {
            Box(
                Modifier
                    .size(400.dp)
                    .testTag("page")
                    .zoomable(state) { _, _ -> onTap() },
            )
        }
    }

    @Test
    fun doubleTapZoomsInAndTheNextOneZoomsBackOut() {
        val state = ZoomState()
        setUp(state)

        rule.onNodeWithTag("page").performTouchInput { doubleClick() }
        rule.waitUntil(timeoutMillis = 5_000) { state.scale > 2.4f }
        assertTrue(state.isZoomed)

        rule.onNodeWithTag("page").performTouchInput { doubleClick() }
        rule.waitUntil(timeoutMillis = 5_000) { !state.isZoomed }
        assertEquals(Offset.Zero, state.offset)
    }

    @Test
    fun pinchOutZoomsAndNeverExceedsTheMaximum() {
        val state = ZoomState()
        setUp(state)

        rule.onNodeWithTag("page").performTouchInput {
            pinch(
                start0 = center + Offset(-20f, 0f),
                end0 = center + Offset(-400f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(400f, 0f),
            )
        }
        rule.waitForIdle()
        assertTrue("pinch should zoom in, scale=${state.scale}", state.scale > 1.5f)
        assertTrue("scale must be capped, scale=${state.scale}", state.scale <= 5.0001f)
    }

    @Test
    fun singleTapIsReportedAndDoesNotZoom() {
        val state = ZoomState()
        var taps = 0
        setUp(state) { taps++ }

        rule.onNodeWithTag("page").performTouchInput { click() }
        rule.waitUntil(timeoutMillis = 5_000) { taps == 1 }
        assertFalse(state.isZoomed)
    }

    @Test
    fun panIsClampedSoTheContentCannotLeaveTheViewport() {
        val state = ZoomState()
        setUp(state)

        rule.onNodeWithTag("page").performTouchInput { doubleClick() }
        rule.waitUntil(timeoutMillis = 5_000) { state.scale > 2.4f }

        // Drag far further than the zoomed overflow allows.
        rule.onNodeWithTag("page").performTouchInput {
            swipe(start = center, end = center + Offset(5_000f, 5_000f), durationMillis = 200)
        }
        rule.waitForIdle()

        val maxOffset = state.size.width * (state.scale - 1f) / 2f
        assertTrue("x offset ${state.offset.x} exceeds $maxOffset", state.offset.x <= maxOffset + 0.5f)
        assertTrue("y offset ${state.offset.y} exceeds $maxOffset", state.offset.y <= maxOffset + 0.5f)
    }
}
