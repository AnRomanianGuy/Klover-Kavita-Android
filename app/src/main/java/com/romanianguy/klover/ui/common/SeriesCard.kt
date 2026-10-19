package com.romanianguy.klover.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.model.SeriesDto

// a fully read card can only be marked unread, an untouched one only read; unknown offers both
@Immutable
class ReadActions(
    val isRead: Boolean,
    val isUnread: Boolean,
    val onMarkRead: () -> Unit,
    val onMarkUnread: () -> Unit,
    val wantToRead: WantToReadControl? = null,
    val download: DownloadControl? = null,
    val downloadSeries: DownloadControl? = null,
)

fun readActions(
    progress: Float?,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    wantToRead: WantToReadControl? = null,
    download: DownloadControl? = null,
    downloadSeries: DownloadControl? = null,
) = ReadActions(
    isRead = progress != null && progress >= 1f,
    isUnread = progress == null || progress <= 0f,
    onMarkRead = onMarkRead,
    onMarkUnread = onMarkUnread,
    wantToRead = wantToRead,
    download = download,
    downloadSeries = downloadSeries,
)

@Composable
fun SeriesCard(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    actions: ReadActions? = null,
    downloadBadge: DownloadBadge = DownloadBadge.None,
    opensSeries: Boolean = false,
    // identifies the card across a rebuilt screen, so the cover that flew away is recognised when it lands
    openKey: Any? = null,
) {
    val peek = LocalPeekController.current
    val openTransition = LocalOpenTransition.current
    val key = remember { Any() }
    // only updated while not pressed: the card swells during a hold and measuring that would make the cover return to
    // a too big slot
    val pressSource = remember { MutableInteractionSource() }
    val pressedNow = pressSource.collectIsPressedAsState()
    // plain holder, not state: changes every frame of a scroll and nothing draws from it
    val bounds = remember { LiveBounds { pressedNow.value } }
    val originKey = openKey ?: key
    val lifted = peek?.activeKey == key || openTransition?.hiddenKey == originKey
    val coverAlpha = originCoverAlpha(lifted, openTransition)
    // worked out when needed; reading bounds while composing would recompose every card on each scroll frame
    val density = LocalDensity.current
    val coverShape = MaterialTheme.shapes.medium
    val coverCorner = { with(density) { coverShape.topStart.toPx(Size(bounds.rect.width, bounds.rect.height), density).toDp() } }
    val settings = LocalAppSettings.current
    val canPeek = peek != null && actions != null && settings.holdToPeek
    DisposableEffect(openTransition, originKey, bounds) {
        openTransition?.registerCard(originKey, bounds)
        onDispose { openTransition?.unregisterCard(originKey, bounds) }
    }
    // if the card leaves while lifted, the quick view must not fly it home
    DisposableEffect(peek, key) { onDispose { peek?.cardGone(key) } }
    val flightFromQuickView: ((Rect, Dp) -> Unit)? =
        if (opensSeries && openTransition != null && settings.flightEnabled()) {
            { from, corner ->
                if (openTransition.isBusy) {
                    onClick()
                } else {
                    openTransition.open(
                        OpenRequest(originKey, bounds.rect, coverUrl, coverCorner(), onClick, title).also {
                            it.startRect = from
                            it.startCorner = corner
                        },
                    )
                }
            }
        } else {
            null
        }

    Column(
        modifier = modifier.bouncyClickable(
            holdToExpand = canPeek,
            interactionSource = pressSource,
            onLongClick = if (canPeek) {
                {
                    peek.show(
                        PeekRequest(
                            key = key,
                            origin = bounds.rect,
                            originNow = { bounds.current() },
                            cornerRadius = coverCorner(),
                            coverUrl = coverUrl,
                            title = title,
                            subtitle = subtitle,
                            actions = actions,
                            onOpen = onClick,
                            openFlight = flightFromQuickView,
                        ),
                    )
                }
            } else {
                null
            },
            onClick = if (opensSeries && openTransition != null && settings.flightEnabled()) {
                { openTransition.open(OpenRequest(originKey, bounds.rect, coverUrl, coverCorner(), onClick, title)) }
            } else {
                onClick
            },
        ),
    ) {
        Box(Modifier.onGloballyPositioned { bounds.record(it) }) {
            Box(Modifier.graphicsLayer { alpha = coverAlpha() }) {
                CoverImage(
                    url = coverUrl,
                    contentDescription = null,
                    placeholderTitle = title,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // progress bar and badge fade on their own (BadgeLayer) instead of blinking with the cover
            BadgeLayer(lifted) {
                DownloadBadgeIcon(if (settings.downloadBadges) downloadBadge else DownloadBadge.None)
                if (progress != null && settings.showProgress) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .height(4.dp),
                        drawStopIndicator = {},
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank() && settings.showSubtitles) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun Rect.scaledAroundCenter(factor: Float): Rect {
    val c = center
    val halfW = width * factor / 2f
    val halfH = height * factor / 2f
    return Rect(c.x - halfW, c.y - halfH, c.x + halfW, c.y + halfH)
}

fun SeriesDto.displayName(): String = name?.takeIf { it.isNotBlank() } ?: localizedName.orEmpty()

fun SeriesDto.progress(): Float? =
    if (pages > 0 && pagesRead > 0) (pagesRead.toFloat() / pages).coerceIn(0f, 1f) else null
