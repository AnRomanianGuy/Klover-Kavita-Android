package com.romanianguy.klover.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.romanianguy.klover.R
import com.romanianguy.klover.data.covers.cachedTextPageVerdict
import com.romanianguy.klover.data.covers.checkTextPage
import com.romanianguy.klover.data.prefs.MotionStyle

const val COVER_ASPECT = 2f / 3f

@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    holdToExpand: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val settings = LocalAppSettings.current
    val charging = holdToExpand && onLongClick != null
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // read in the graphics layer, reading it here would recompose the card every frame of the press
    val scale = animateFloatAsState(
        targetValue = when {
            pressed && charging -> PEEK_CHARGE_SCALE
            pressed && settings.motionStyle == MotionStyle.Reduced -> 1f
            pressed -> 0.95f
            else -> 1f
        },
        animationSpec = if (pressed && charging) {
            when (settings.motionStyle) {
                // fast and bouncy so the card is at its swollen size before the long press fires
                MotionStyle.Bouncy -> spring(dampingRatio = 0.42f, stiffness = 800f)
                MotionStyle.Calm -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
                MotionStyle.Reduced -> tween(durationMillis = 120)
            }
        } else {
            spring(
                dampingRatio = if (settings.motionStyle == MotionStyle.Bouncy) {
                    Spring.DampingRatioMediumBouncy
                } else {
                    Spring.DampingRatioNoBouncy
                },
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .combinedClickable(
            interactionSource = source,
            // no ripple: its shader made every frame of the quick view lift heavy
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick?.let { action ->
                {
                    if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                }
            },
            onClick = onClick,
        )
}

private const val COVER_DECODE_WIDTH = 480
private const val COVER_DECODE_HEIGHT = 720

@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    aspectRatio: Float? = COVER_ASPECT,
    placeholderTitle: String? = contentDescription,
) {
    // one fixed decode size and cache key so the card and the lifted copy share a bitmap, otherwise the copy
    // cross-fades in washed out
    val context = LocalContext.current
    val request = remember(url, context) {
        url?.let {
            ImageRequest.Builder(context)
                .data(it)
                .memoryCacheKey(it)
                .size(COVER_DECODE_WIDTH, COVER_DECODE_HEIGHT)
                .build()
        }
    }
    // a missing cover is made up instead of left blank
    var failed by remember(url) { mutableStateOf(false) }
    val frame = modifier
        .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
        .clip(shape)
    // a first page of text is not a cover, so the made-up one shows (unless switched off in Settings)
    val textPagesToo = LocalAppSettings.current.textCoverPlaceholder
    var textPage by remember(url) { mutableStateOf(url?.let { cachedTextPageVerdict(it) }) }
    // data saver: not fetched unless asked for in the quick view
    val saving = LocalAppSettings.current.dataSaver && url != null && url.startsWith("http", ignoreCase = true) &&
        !CoverReveal.isRevealed(url)
    if (url == null || failed || saving || (textPagesToo && textPage == true)) {
        PlaceholderCover(placeholderTitle, frame)
    } else {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            onSuccess = { success ->
                if (textPage == null) {
                    (success.result.image as? coil3.BitmapImage)?.bitmap?.let { textPage = checkTextPage(url, it) }
                }
            },
            modifier = frame.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.CloudOff,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    )
}

@Composable
fun themeCornerDp(shape: CornerBasedShape = MaterialTheme.shapes.extraLarge): Dp {
    val density = LocalDensity.current
    return with(density) { shape.topStart.toPx(Size(1000f, 1000f), density).toDp() }
}
