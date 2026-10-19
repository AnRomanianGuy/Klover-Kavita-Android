package com.romanianguy.klover.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.prefs.MotionStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Immutable
class PeekRequest(
    val key: Any,
    val origin: Rect,
    val originNow: (() -> Rect)? = null,
    val cornerRadius: Dp,
    val coverUrl: String?,
    val title: String,
    val subtitle: String?,
    val actions: ReadActions? = null,
    val onOpen: () -> Unit,
    val options: List<PeekAction> = emptyList(),
    val showOpen: Boolean = true,
    @StringRes val openLabel: Int = R.string.action_open,
    val openFlight: ((from: Rect, corner: Dp) -> Unit)? = null,
)

@Immutable
class PeekAction(
    val icon: ImageVector,
    @StringRes val label: Int,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private val NoReadActions = ReadActions(isRead = true, isUnread = true, onMarkRead = {}, onMarkUnread = {})

@Stable
class PeekController {
    var request by mutableStateOf<PeekRequest?>(null)
        private set

    var activeKey by mutableStateOf<Any?>(null)
        internal set

    var handedOff by mutableStateOf(false)
        internal set

    var originGone by mutableStateOf(false)
        internal set

    fun show(request: PeekRequest) {
        handedOff = false
        originGone = false
        this.request = request
    }

    fun dismiss() {
        request = null
    }

    fun handOff() {
        handedOff = true
        request = null
    }

    fun cardGone(key: Any) {
        if (activeKey == key) originGone = true
    }
}

val LocalPeekController = staticCompositionLocalOf<PeekController?> { null }

const val PEEK_CHARGE_SCALE = 1.08f

@Composable
fun PeekHost(content: @Composable () -> Unit) {
    val controller = remember { PeekController() }
    val request = controller.request
    val motion = LocalAppSettings.current.motionStyle

    // 0 = in the card, 1 = lifted; may overshoot 1
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<PeekRequest?>(null) }
    // read while drawing, not in composition, so a frame doesn't recompose the overlay
    val progressOf = remember(progress) { { progress.value } }

    LaunchedEffect(request, motion) {
        if (request != null) {
            shown = request
            controller.activeKey = request.key
            progress.snapTo(0f)
            progress.animateTo(1f, when (motion) {
                MotionStyle.Bouncy -> spring(dampingRatio = 0.5f, stiffness = 360f)
                MotionStyle.Calm -> spring(dampingRatio = 1f, stiffness = 320f, visibilityThreshold = 0.0003f)
                MotionStyle.Reduced -> tween(durationMillis = 160)
            })
        } else if (shown != null) {
            // tiny threshold on purpose so the hand-over back to the card can't be seen
            progress.animateTo(
                targetValue = 0f,
                animationSpec = when {
                    controller.handedOff -> tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    motion == MotionStyle.Bouncy -> spring(
                        dampingRatio = 0.8f,
                        stiffness = 210f,
                        visibilityThreshold = 0.0003f,
                    )
                    motion == MotionStyle.Calm -> spring(
                        dampingRatio = 1f,
                        stiffness = 240f,
                        visibilityThreshold = 0.0003f,
                    )
                    else -> tween(durationMillis = 140)
                },
            )
            progress.snapTo(0f)
            shown = null
            controller.activeKey = null
            controller.handedOff = false
            controller.originGone = false
        }
    }

    CompositionLocalProvider(LocalPeekController provides controller) {
        Box(Modifier.fillMaxSize()) {
            // the blur follows the animation in the draw phase
            val blurEnabled = LocalAppSettings.current.peekBlur
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (blurEnabled) {
                            Modifier.graphicsLayer {
                                val radius = 14f * progress.value.coerceIn(0f, 1f) * density
                                renderEffect = if (radius > 0.5f * density) BlurEffect(
                                    radius,
                                    radius,
                                    TileMode.Clamp,
                                ) else null
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
            }
            shown?.let { PeekOverlay(it, progressOf, closing = request == null, controller) }
        }
        // registered after the content's handlers so Back closes the peek first
        PredictiveBackHandler(enabled = request != null) { events ->
            try {
                events.collect { progress.snapTo(1f - 0.3f * it.progress) }
                controller.dismiss()
            } catch (e: CancellationException) {
                withContext(NonCancellable) { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                throw e
            }
        }
    }
}

@Composable
private fun PeekOverlay(request: PeekRequest, progress: () -> Float, closing: Boolean, controller: PeekController) {
    val handoff = controller.handedOff
    val gone = controller.originGone
    val actions = request.actions ?: NoReadActions
    val wantToRead = actions.wantToRead
    // the pill is there from the start (disabled) so the layout doesn't jump when the answer arrives
    var onShelf by remember(request) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(request) { onShelf = wantToRead?.load?.invoke() }
    val download = actions.download
    val dataSaver = LocalAppSettings.current.dataSaver
    val options = buildList {
        if (request.showOpen) {
            add(PeekOption(Icons.AutoMirrored.Outlined.MenuBook, request.openLabel, primary = true, opens = true) { request.onOpen() })
        }
        val coverUrl = request.coverUrl
        if (dataSaver && coverUrl != null && coverUrl.startsWith("http", ignoreCase = true)) {
            val loaded = CoverReveal.isRevealed(coverUrl)
            add(
                PeekOption(
                    icon = Icons.Outlined.Image,
                    label = if (loaded) R.string.action_cover_loaded else R.string.action_load_cover,
                    enabled = !loaded,
                    keepsOpen = true,
                ) { CoverReveal.reveal(coverUrl) },
            )
        }
        request.options.forEach { add(PeekOption(it.icon, it.label, it.primary, it.enabled, onClick = it.onClick)) }
        if (download != null) {
            val badge = download.badge
            add(
                PeekOption(
                    icon = when (badge) {
                        DownloadBadge.Done -> Icons.Outlined.DeleteOutline
                        is DownloadBadge.Active -> Icons.Outlined.Close
                        else -> Icons.Outlined.Download
                    },
                    label = when (badge) {
                        DownloadBadge.Done -> R.string.action_download_remove
                        is DownloadBadge.Active -> R.string.action_download_stop
                        DownloadBadge.Partial -> R.string.action_download_remaining
                        DownloadBadge.None -> R.string.action_download
                    },
                ) { download.onToggle() },
            )
        }
        actions.downloadSeries?.let { series ->
            val badge = series.badge
            add(
                PeekOption(
                    icon = when (badge) {
                        DownloadBadge.Done -> Icons.Outlined.DeleteOutline
                        is DownloadBadge.Active -> Icons.Outlined.Close
                        else -> Icons.Outlined.DownloadForOffline
                    },
                    label = when (badge) {
                        DownloadBadge.Done -> R.string.action_download_series_remove
                        is DownloadBadge.Active -> R.string.action_download_series_stop
                        DownloadBadge.Partial -> R.string.action_download_series_rest
                        DownloadBadge.None -> R.string.action_download_series
                    },
                ) { series.onToggle() },
            )
        }
        if (!actions.isRead) add(PeekOption(Icons.Outlined.DoneAll, R.string.action_mark_read) { actions.onMarkRead() })
        if (!actions.isUnread) add(PeekOption(Icons.Outlined.RemoveDone, R.string.action_mark_unread) { actions.onMarkUnread() })
        if (wantToRead != null) {
            add(
                PeekOption(
                    icon = when (onShelf) {
                        true -> Icons.Outlined.BookmarkRemove
                        false -> Icons.Outlined.BookmarkAdd
                        null -> Icons.Outlined.BookmarkBorder
                    },
                    label = when (onShelf) {
                        true -> R.string.action_want_remove
                        false -> R.string.action_want_add
                        null -> R.string.nav_want_to_read
                    },
                    enabled = onShelf != null,
                ) { onShelf?.let { wantToRead.set(!it) } },
            )
        }
    }
    val density = LocalDensity.current
    val dismissSource = remember { MutableInteractionSource() }
    val window = LocalWindowInfo.current.containerSize

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * progress().coerceIn(0f, 1f))) }
            .clickable(interactionSource = dismissSource, indication = null) { controller.dismiss() }
            .semantics { contentDescription = request.title },
    ) {
        // half open like a book: centre on the right page, like the sheets
        val hinge = rememberBookHinge()
        val rootH = window.height.toFloat()
        val stageLeft = (hinge?.right ?: 0f).coerceIn(0f, window.width * 0.9f)
        val rootW = window.width.toFloat() - stageLeft
        val inset = with(density) { 24.dp.toPx() }
        val availH = (rootH - 2 * inset).coerceAtLeast(1f)
        val titleH = with(density) { 56.dp.toPx() }
        val count = options.size
        val normalOptionH = with(density) { OPTION_HEIGHT.toPx() }
        val normalGap = with(density) { 12.dp.toPx() }
        val comfortableW = min(rootW * 0.6f, with(density) { 280.dp.toPx() })

        // everything must fit: a wide window puts the options beside the cover, otherwise they stack
        val sideBySide = rootW > rootH * 1.25f
        val gap = if (sideBySide) with(density) { 8.dp.toPx() } else normalGap
        val optionH = if (sideBySide) {
            val fits = titleH + count * normalOptionH + count * gap <= availH
            if (fits) {
                normalOptionH
            } else {
                ((availH - titleH - count * gap) / count.coerceAtLeast(1)).coerceAtLeast(with(density) { 36.dp.toPx() })
            }
        } else {
            normalOptionH
        }
        val columnH = titleH + count * optionH + count * gap

        val coverW: Float
        val coverH: Float
        val coverLeft: Float
        val coverTop: Float
        val optionsLeft: Float
        val optionsTop: Float
        val optionsW: Float
        if (sideBySide) {
            coverH = min(availH, comfortableW * 1.6f / COVER_ASPECT)
            coverW = min(coverH * COVER_ASPECT, rootW * 0.4f)
            optionsW = min(rootW * 0.34f, with(density) { 320.dp.toPx() })
            val spacing = with(density) { 28.dp.toPx() }
            val groupLeft = stageLeft + (rootW - (coverW + spacing + optionsW)) / 2f
            coverLeft = groupLeft
            coverTop = (rootH - coverH) / 2f
            optionsLeft = groupLeft + coverW + spacing
            optionsTop = ((rootH - columnH) / 2f).coerceAtLeast(inset)
        } else {
            val belowCover = gap + columnH
            coverH = min(comfortableW / COVER_ASPECT, availH - belowCover).coerceAtLeast(with(density) { 96.dp.toPx() })
            coverW = coverH * COVER_ASPECT
            // wide enough for "Download rest of series" on one line
            optionsW = min(rootW * 0.82f, with(density) { 340.dp.toPx() })
            coverLeft = stageLeft + (rootW - coverW) / 2f
            coverTop = ((rootH - (coverH + belowCover)) / 2f).coerceAtLeast(inset)
            optionsLeft = stageLeft + (rootW - optionsW) / 2f
            optionsTop = coverTop + coverH + gap
        }
        val target = Rect(coverLeft, coverTop, coverLeft + coverW, coverTop + coverH)

        // opening starts from the swollen size the card had while held, closing from its true size
        val startScale = animateFloatAsState(
            targetValue = if (closing) 1f else PEEK_CHARGE_SCALE,
            animationSpec = tween(durationMillis = 120),
            label = "peek-start-scale",
        )
        // not clamped, so the spring overshoot doesn't swell it past its final size
        val originAt = { request.originNow?.invoke()?.takeIf { it.width > 1f && it.height > 1f } ?: request.origin }
        val coverAt = { p: Float -> lerp(originAt().scaledAroundCenter(startScale.value), target, p) }
        val bouncy = LocalAppSettings.current.motionStyle == MotionStyle.Bouncy
        val cornerFrom = with(density) { request.cornerRadius.toPx() }
        val cornerTo = with(density) { themeCornerDp().toPx() }
        // if the card is gone, fade out in place
        val vanish = animateFloatAsState(
            targetValue = if (closing && gone) 0f else 1f,
            animationSpec = tween(durationMillis = 160),
            label = "peek-vanish",
        )

        val openNow = {
            val flight = request.openFlight
            if (flight != null) {
                val p = progress()
                val corner = lerp(request.cornerRadius, with(density) { cornerTo.toDp() }, p.coerceIn(0f, 1f))
                controller.handOff()
                flight(coverAt(p), corner)
            } else {
                controller.dismiss()
                request.onOpen()
            }
        }

        Box(
            Modifier
                .offset {
                    val r = coverAt(progress())
                    IntOffset(r.left.roundToInt(), r.top.roundToInt())
                }
                .layout { measurable, _ ->
                    val r = coverAt(progress())
                    val w = r.width.roundToInt().coerceAtLeast(1)
                    val h = r.height.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val p = progress()
                    val c = p.coerceIn(0f, 1f)
                    // a wobble, zero at both ends and strongest half-way
                    rotationZ = if (bouncy) PEEK_TILT_DEGREES * sin(PI.toFloat() * p) else 0f
                    alpha = if (handoff) 0f else vanish.value
                    shape = RoundedCornerShape(cornerFrom + (cornerTo - cornerFrom) * c)
                    clip = true
                    shadowElevation = 28.dp.toPx() * c
                }
                .clickable(interactionSource = dismissSource, indication = null) { openNow() },
        ) {
            CoverImage(
                url = request.coverUrl,
                contentDescription = null,
                placeholderTitle = request.title,
                shape = RectangleShape,
                aspectRatio = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // placed a frame later: composing them in the first frame was the heaviest part
        var optionsReady by remember(request) { mutableStateOf(false) }
        LaunchedEffect(request) {
            withFrameNanos { }
            optionsReady = true
        }

        if (optionsReady) Column(
            modifier = Modifier
                .offset { IntOffset(optionsLeft.roundToInt(), optionsTop.roundToInt()) }
                .width(with(density) { optionsW.toDp() }),
            verticalArrangement = Arrangement.spacedBy(with(density) { gap.toDp() }),
        ) {
            val titleIn = optionAppearance(index = 0, closing = closing)
            Column(
                Modifier
                    .height(with(density) { titleH.toDp() })
                    .graphicsLayer {
                        val a = titleIn.value
                        alpha = a
                        translationY = (1f - a) * 10.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                request.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            options.forEachIndexed { index, option ->
                val enter = optionAppearance(index = index + 1, closing = closing)
                OptionPill(
                    option = option,
                    height = with(density) { optionH.toDp() },
                    modifier = Modifier.graphicsLayer {
                        val a = enter.value
                        alpha = a
                        translationY = (1f - a) * 16.dp.toPx()
                    },
                    onClick = {
                        if (option.opens) {
                            openNow()
                        } else {
                            if (!option.keepsOpen) controller.dismiss()
                            option.onClick()
                        }
                    },
                )
            }
        }
    }
}

private val OPTION_HEIGHT = 52.dp
private const val PEEK_TILT_DEGREES = -5f

@Composable
private fun optionAppearance(index: Int, closing: Boolean): State<Float> {
    val reduced = LocalAppSettings.current.motionStyle == MotionStyle.Reduced
    // starts hidden: animateFloatAsState would skip the animation if the first target were 1f
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val value = animateFloatAsState(
        targetValue = if (shown && !closing) 1f else 0f,
        animationSpec = when {
            closing -> tween(durationMillis = 140)
            reduced -> tween(durationMillis = 160)
            else -> tween(durationMillis = 300, delayMillis = 90 + index * 45, easing = FastOutSlowInEasing)
        },
        label = "peek-option-$index",
    )
    return value
}

private class PeekOption(
    val icon: ImageVector,
    val label: Int,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val opens: Boolean = false,
    val keepsOpen: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun OptionPill(option: PeekOption, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = option.enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (option.primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        // loading shows as a quieter label, not a see-through pill
        contentColor = (if (option.primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            .copy(alpha = if (option.enabled) 1f else 0.5f),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            Icon(option.icon, contentDescription = null)
            Text(stringResource(option.label), style = MaterialTheme.typography.titleMedium)
        }
    }
}
