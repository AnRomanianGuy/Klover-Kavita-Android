package com.romanianguy.klover.ui.series

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.ui.common.LocalAppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal enum class SwipeAction { MarkRead, MarkUnread, Download, RemoveDownload }

private class Latest(
    val read: Boolean,
    val downloaded: Boolean,
    val readLabel: String,
    val readDone: String,
    val downloadLabel: String,
    val downloadDone: String,
)

private class SwipeDone(val action: SwipeAction, val label: String, val doneLabel: String)

private const val THRESHOLD = 0.28f

// right marks read (unread once read), left downloads (removes once downloaded); the card springs back and the art
// grows with the pull
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableChapter(
    read: Boolean,
    download: DownloadBadge,
    canDownload: Boolean,
    removesWholeVolume: Boolean,
    shape: Shape,
    onToggleRead: () -> Unit,
    onToggleDownload: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val settings = LocalAppSettings.current
    val toggleRead by rememberUpdatedState(onToggleRead)
    val toggleDownload by rememberUpdatedState(onToggleDownload)
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * THRESHOLD })

    val readLabel = stringResource(if (read) R.string.swipe_mark_unread else R.string.swipe_mark_read)
    val readDone = stringResource(if (read) R.string.swipe_done_unread else R.string.swipe_done_read)
    // a download that gave up isn't "on the device", swiping again retries
    val failedDownload = download is DownloadBadge.Active && download.failed
    val downloaded = (download is DownloadBadge.Done || download is DownloadBadge.Active) && !failedDownload
    val downloadLabel = stringResource(
        when {
            failedDownload -> R.string.swipe_retry_download
            downloaded && removesWholeVolume -> R.string.swipe_remove_volume_download
            downloaded -> R.string.swipe_remove_download
            else -> R.string.swipe_download
        },
    )
    val downloadDone = stringResource(if (downloaded) R.string.swipe_done_remove else R.string.swipe_done_download)
    // fixed once the finger lets go: doing it flips the state the label and colour come from while the card is still
    // sweeping back
    var done by remember { mutableStateOf<SwipeDone?>(null) }
    // the one moment the action is done; collected rather than keyed on, so the value going back to settled can't cut
    // the return short
    val latest by rememberUpdatedState(Latest(read, downloaded, readLabel, readDone, downloadLabel, downloadDone))
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { value ->
            val toggle = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> toggleRead
                SwipeToDismissBoxValue.EndToStart -> toggleDownload
                SwipeToDismissBoxValue.Settled -> return@collect
            }
            val now = latest
            done = if (value == SwipeToDismissBoxValue.StartToEnd) {
                SwipeDone(if (now.read) SwipeAction.MarkUnread else SwipeAction.MarkRead, now.readLabel, now.readDone)
            } else {
                SwipeDone(
                    if (now.downloaded) SwipeAction.RemoveDownload else SwipeAction.Download,
                    now.downloadLabel,
                    now.downloadDone,
                )
            }
            toggle()
            if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(420)
            // a finger on the card holds it; retried until the card is home so a card left aside can't swallow later
            // swipes
            var tries = 0
            while (true) {
                try {
                    state.reset()
                    break
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (++tries >= 40) break
                    delay(100)
                }
            }
            done = null
        }
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = canDownload,
        modifier = modifier.semantics {
            // the only way in for someone who can't swipe
            customActions = buildList {
                add(CustomAccessibilityAction(readLabel) { onToggleRead(); true })
                if (canDownload) add(CustomAccessibilityAction(downloadLabel) { onToggleDownload(); true })
            }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val live = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd ->
                        SwipeDone(if (read) SwipeAction.MarkUnread else SwipeAction.MarkRead, readLabel, readDone)
                    else ->
                        SwipeDone(
                            if (downloaded) SwipeAction.RemoveDownload else SwipeAction.Download,
                            downloadLabel,
                            downloadDone,
                        )
                }
                val shown = done ?: live
                SwipeBackground(
                    action = shown.action,
                    label = shown.label,
                    doneLabel = shown.doneLabel,
                    locked = done != null,
                    pull = state.progress,
                    towardsEnd = direction == SwipeToDismissBoxValue.StartToEnd,
                    shape = shape,
                )
            }
        },
        content = { content() },
    )
}

@Composable
private fun SwipeBackground(
    action: SwipeAction,
    label: String,
    doneLabel: String,
    locked: Boolean,
    pull: Float,
    towardsEnd: Boolean,
    shape: Shape,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when (action) {
        SwipeAction.MarkRead -> scheme.primaryContainer to scheme.onPrimaryContainer
        SwipeAction.MarkUnread -> scheme.surfaceVariant to scheme.onSurfaceVariant
        SwipeAction.Download -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SwipeAction.RemoveDownload -> scheme.errorContainer to scheme.onErrorContainer
    }
    val accents = when (action) {
        SwipeAction.RemoveDownload -> listOf(scheme.error, scheme.outline, scheme.onErrorContainer)
        SwipeAction.MarkUnread -> listOf(scheme.outline, scheme.secondary, scheme.onSurfaceVariant)
        else -> listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    }
    val reveal = (pull / THRESHOLD).coerceIn(0f, 1f)
    // stays "counted" while the card returns
    val counts = locked || pull >= THRESHOLD

    val haptics = LocalHapticFeedback.current
    val settings = LocalAppSettings.current
    val style = settings.motionStyle
    LaunchedEffect(counts, action) {
        if (counts && settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    // the picture jumps when it counts, as the motion setting says
    val popSpec: FiniteAnimationSpec<Float> = when (style) {
        MotionStyle.Bouncy -> spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)
        MotionStyle.Calm -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        MotionStyle.Reduced -> snap()
    }
    val pop by animateFloatAsState(targetValue = if (counts) 1f else 0f, animationSpec = popSpec, label = "swipe-pop")
    val burst = remember(action) { Animatable(0f) }
    LaunchedEffect(counts, action) {
        if (counts && style != MotionStyle.Reduced) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
        } else {
            burst.snapTo(0f)
        }
    }
    // a little tilt, only while counted and the motion is Bouncy
    val tilt = if (counts && style == MotionStyle.Bouncy) {
        rememberInfiniteTransition(label = "swipe-tilt").animateFloat(
            initialValue = -7f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 520, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "swipe-tilt-angle",
        )
    } else {
        null
    }
    val wiggle = tilt?.value ?: 0f

    val color by animateColorAsState(
        targetValue = if (counts) container else androidx.compose.ui.graphics.lerp(
            scheme.surfaceContainerHigh,
            container,
            reveal,
        ),
        label = "swipe-color",
    )

    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .drawBehind { drawRect(color) },
        contentAlignment = if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .graphicsLayer {
                    alpha = reveal
                    val scale = lerp(0.55f, 1f, reveal)
                    scaleX = scale
                    scaleY = scale
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!towardsEnd) SwipeLabel(label, doneLabel, counts, onContainer)
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            val swell = 1f + 0.16f * pop
                            scaleX = swell
                            scaleY = swell
                        }
                        .clip(CircleShape)
                        .background(onContainer.copy(alpha = 0.10f)),
                )
                SwipeArt(
                    action = action,
                    reveal = reveal,
                    pop = pop,
                    burst = burst.value,
                    wiggle = wiggle,
                    color = onContainer,
                    accents = accents,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (towardsEnd) SwipeLabel(label, doneLabel, counts, onContainer)
        }
    }
}

@Composable
private fun SwipeLabel(label: String, doneLabel: String, counts: Boolean, color: Color) {
    AnimatedContent(
        targetState = counts,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "swipe-label",
    ) { counted ->
        Text(
            text = if (counted) doneLabel else label,
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}
