package com.romanianguy.klover.ui.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

const val BOOK_ANGLE_MAX = 170f

private const val BOOK_ANGLE_RELEASE = 173f

// from the hinge angle sensor, sooner than the system's half-open state; changes only when crossing the limit so
// readers aren't redrawn per degree
@Composable
fun rememberFoldBent(): Boolean {
    val context = LocalContext.current
    var bent by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val angle = event.values.firstOrNull() ?: return
                bent = if (bent) angle < BOOK_ANGLE_RELEASE else angle < BOOK_ANGLE_MAX
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager?.unregisterListener(listener) }
    }
    return bent
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberBookHinge(): Rect? {
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    val bent = rememberFoldBent()
    return remember(posture, bent) {
        posture.hingeList.firstOrNull { it.isVertical && (it.isSeparating || bent) }?.bounds
    }
}

// darkens the halves towards the spine; only draws, takes no touches
@Composable
fun BookSpine(
    hinge: Rect,
    modifier: Modifier = Modifier,
    edges: Boolean = false,
    edgeColor: Color = Color(0xFF8A8A8A),
    shade: Float = 0.30f,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val centre = hinge.center.x - origin.x
        val half = hinge.width / 2f
        val spine = 40.dp.toPx()
        // The pages curve down into the spine: dark at the fold, fading out over the first few centimetres.
        val leftEdge = centre - half
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.55f to Color.Black.copy(alpha = shade * 0.35f),
                1f to Color.Black.copy(alpha = shade),
                startX = leftEdge - spine,
                endX = leftEdge,
            ),
            topLeft = Offset(leftEdge - spine, 0f),
            size = Size(spine, size.height),
        )
        val rightEdge = centre + half
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = shade),
                0.45f to Color.Black.copy(alpha = shade * 0.35f),
                1f to Color.Transparent,
                startX = rightEdge,
                endX = rightEdge + spine,
            ),
            topLeft = Offset(rightEdge, 0f),
            size = Size(spine, size.height),
        )
        if (edges) {
            // The edges of the pages stacked up on each side.
            for (i in 0 until 4) {
                val inset = (1.5f + i * 2.4f).dp.toPx()
                val line = edgeColor.copy(alpha = 0.42f - i * 0.09f)
                drawRect(line, Offset(inset, 0f), Size(0.7.dp.toPx(), size.height))
                drawRect(line, Offset(size.width - inset - 0.7.dp.toPx(), 0f), Size(0.7.dp.toPx(), size.height))
            }
        }
    }
}
