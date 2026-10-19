package com.romanianguy.klover.ui.reader.book

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.ui.common.LocalAppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val ReadAloudSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReadAloudBar(
    paused: Boolean,
    speed: Float,
    sleepMinutes: Int,
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    onToggle: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSleep: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // the finger's position shows while held, the voice moves on release
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: progress()
    val percent = (shown * 100).toInt().coerceIn(0, 100)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.read_aloud),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.read_aloud_stop),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let(onSeek)
                    dragging = null
                },
                modifier = Modifier.padding(end = 8.dp),
            )
            // the speed control opens like Android's ring / vibrate picker and takes the whole row
            var expanded by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val haptics = LocalHapticFeedback.current
            val hapticsOn = LocalAppSettings.current.haptics
            LaunchedEffect(expanded) {
                if (expanded) {
                    delay(4_000) // left open and forgotten: close by itself
                    expanded = false
                }
            }
            Row(
                // fixed height so the row doesn't resize as controls come and go
                Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpeedPicker(
                    speed = speed,
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onPick = { picked ->
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSpeed(picked)
                        scope.launch {
                            delay(260)
                            expanded = false
                        }
                    },
                )
                PhysicsReveal(visible = !expanded, delayMillis = if (expanded) 0 else 110) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = onPreviousSentence) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.read_aloud_previous),
                            )
                        }
                        FilledIconButton(onClick = onToggle, modifier = Modifier.size(56.dp)) {
                            AnimatedContent(
                                targetState = paused,
                                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                                label = "play-pause",
                            ) { isPaused ->
                                Icon(
                                    if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = stringResource(
                                        if (isPaused) R.string.read_aloud_resume else R.string.read_aloud_pause,
                                    ),
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        FilledTonalIconButton(onClick = onNextSentence) {
                            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.read_aloud_next))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                PhysicsReveal(visible = !expanded, delayMillis = if (expanded) 0 else 150) {
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        val on = sleepMinutes != 0
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                if (on) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
                                contentDescription = stringResource(R.string.read_aloud_sleep),
                                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf(
                                0 to R.string.sleep_off,
                                15 to R.string.sleep_15,
                                30 to R.string.sleep_30,
                                60 to R.string.sleep_60,
                                -1 to R.string.sleep_section,
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    trailingIcon = { if (sleepMinutes == value) Text("•") },
                                    onClick = { menu = false; onSleep(value) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedPicker(
    speed: Float,
    expanded: Boolean,
    onExpand: () -> Unit,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = ReadAloudSpeeds.indexOfFirst { abs(it - speed) < 0.01f }.coerceAtLeast(0)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadAloudSpeeds.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val distance = abs(index - selectedIndex)
            // opening ripples out from the nearest neighbours, closing from the farthest
            val delay = if (expanded) distance * 40 else (ReadAloudSpeeds.size - 1 - distance) * 22
            PhysicsReveal(visible = expanded || selected, delayMillis = delay) {
                val shapes = when {
                    !expanded -> ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.MinHeight)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == ReadAloudSpeeds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { if (expanded) onPick(option) else onExpand() },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    shapes = shapes,
                    colors = ToggleButtonDefaults.colors(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(speedLabel(option), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun PhysicsReveal(visible: Boolean, delayMillis: Int = 0, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        delay(delayMillis.toLong())
        progress.animateTo(
            if (visible) 1f else 0f,
            spring(dampingRatio = 0.68f, stiffness = 380f, visibilityThreshold = 0.002f),
        )
    }
    val p = progress.value
    if (p <= 0.002f && !visible) return
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                val width = (placeable.width * p.coerceAtLeast(0f)).roundToInt()
                layout(width, placeable.height) { placeable.placeRelative((width - placeable.width) / 2, 0) }
            }
            .graphicsLayer {
                val scale = 0.6f + 0.4f * p.coerceIn(0f, 1.15f)
                scaleX = scale
                scaleY = scale
                alpha = p.coerceIn(0f, 1f)
            },
    ) { content() }
}

private fun speedLabel(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0')) + "x"

// one surface that morphs between the card and a thin progress bar when idle with the controls hidden; tapping the
// bar expands it
@Composable
internal fun ReadAloudDock(
    minimized: Boolean,
    paused: Boolean,
    progress: () -> Float,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val morph = remember { Animatable(if (minimized) 1f else 0f) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(minimized) {
        morph.animateTo(if (minimized) 1f else 0f, motion.slowSpatialSpec())
    }
    val density = LocalDensity.current
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val cardCorner = with(density) { 28.dp.toPx() }
    val sideCard = with(density) { DOCK_SIDE.toPx() }
    val sideBar = with(density) { 28.dp.toPx() }
    val barHeight = with(density) { 6.dp.toPx() }
    val touchHeight = with(density) { 32.dp.toPx() }
    val cardHeight = remember { floatArrayOf(0f) }
    val label = stringResource(R.string.read_aloud_open_player)

    Box(
        modifier
            .graphicsLayer {
                val m = morph.value
                val side = sideCard + (sideBar - sideCard) * m
                val h = cardHeight[0] + (barHeight - cardHeight[0]) * m
                val corner = (cardCorner + (barHeight / 2 - cardCorner) * m).coerceAtMost(h / 2)
                shape = DockShape(side, h, corner)
                clip = true
                shadowElevation = (6f - 3f * m) * density.density
            }
            .drawWithContent {
                val m = morph.value
                drawRect(lerp(cardColor, trackColor, m.coerceIn(0f, 1f)))
                drawContent()
                val fillAlpha = ((m - 0.75f) * 4f).coerceIn(0f, 1f)
                if (fillAlpha > 0f) {
                    val side = sideCard + (sideBar - sideCard) * m
                    val h = cardHeight[0] + (barHeight - cardHeight[0]) * m
                    val top = (size.height - h) / 2
                    val full = size.width - 2 * side
                    drawRoundRect(
                        fillColor.copy(alpha = fillColor.alpha * fillAlpha),
                        topLeft = Offset(side, top),
                        size = Size((full * progress().coerceIn(0f, 1f)).coerceAtLeast(h), h),
                        cornerRadius = CornerRadius(h / 2),
                    )
                }
            }
            .layout { measurable, constraints ->
                val side = DOCK_SIDE.roundToPx()
                val width = constraints.maxWidth - 2 * side
                val placeable = measurable.measure(
                    Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity),
                )
                cardHeight[0] = placeable.height.toFloat()
                val height = (placeable.height + (touchHeight - placeable.height) * morph.value).roundToInt().coerceAtLeast(0)
                layout(constraints.maxWidth, height) { placeable.place(side, 0) }
            },
    ) {
        Box(Modifier.graphicsLayer { alpha = (1f - morph.value * 2f).coerceIn(0f, 1f) }) { content() }
        // over the hidden controls while minimized so a tap can't press them; absent when open
        if (minimized) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClickLabel = label, role = Role.Button, onClick = onExpand),
            )
        }
    }
}

private val DOCK_SIDE = 16.dp

private class DockShape(val side: Float, val height: Float, val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = (size.height - height) / 2
        return Outline.Rounded(RoundRect(side, top, size.width - side, top + height, CornerRadius(corner)))
    }
}
