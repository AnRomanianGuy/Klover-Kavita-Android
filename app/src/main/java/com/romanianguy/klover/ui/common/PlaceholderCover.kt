package com.romanianguy.klover.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.prefs.MotionStyle

// made-up cover: gradient picked by the title (same title, same colours), a scalloped shape and a little book.
// the shapes turn slowly, each from a different angle; still on tiny thumbnails and in Reduced, Calm is slower
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaceholderCover(title: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pairs = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val hash = title.orEmpty().hashCode() and 0x7fffffff
    val (container, onContainer) = pairs[hash % pairs.size]
    val motion = LocalAppSettings.current.motionStyle
    BoxWithConstraints(
        modifier
            .background(Brush.verticalGradient(listOf(container, container.copy(alpha = 0.72f))))
            .clipToBounds(),
    ) {
        val width = maxWidth
        val roomForText = width >= 96.dp && !title.isNullOrBlank()
        val moving = motion != MotionStyle.Reduced && width >= 56.dp
        // Two animations per cover, and a grid has dozens: they exist only where a cover really moves (not on a
        // thumbnail too small to
        // notice it, nor with Reduced motion), because even an unread animation wakes the frame clock on every frame.
        // They are only read
        // while drawing, so turning and breathing recompose nothing.
        var turn: State<Float>? = null
        var breath: State<Float>? = null
        if (moving) {
            val transition = rememberInfiniteTransition(label = "placeholder")
            turn = transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(if (motion == MotionStyle.Calm) 90_000 else 55_000, easing = LinearEasing)),
                label = "turn",
            )
            breath = transition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "breath",
            )
        }
        // A start angle from the title: covers side by side are never turned the same way.
        val start = (hash % 360).toFloat()
        Box(
            Modifier
                .size(width * 0.95f)
                .align(Alignment.TopEnd)
                .offset(x = width * 0.28f, y = -width * 0.22f)
                .graphicsLayer {
                    val t = turn
                    val b = breath
                    rotationZ = 14f + if (t != null) start + t.value else 0f
                    if (b != null) {
                        scaleX = b.value
                        scaleY = b.value
                    }
                }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(onContainer.copy(alpha = 0.10f)),
        )
        if (width >= 96.dp) {
            // A smaller one in the other corner, turning the other way: depth, and never the same picture twice.
            Box(
                Modifier
                    .size(width * 0.62f)
                    .align(Alignment.BottomStart)
                    .offset(x = -width * 0.2f, y = width * 0.24f)
                    .graphicsLayer {
                        val t = turn
                        val b = breath
                        rotationZ = if (t != null) -(start * 0.5f + t.value * 0.8f) else 0f
                        if (b != null) {
                            scaleX = 2f - b.value
                            scaleY = 2f - b.value
                        }
                    }
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(onContainer.copy(alpha = 0.07f)),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (roomForText) 12.dp else 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val initial = title?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
            if (!roomForText && initial != null && width >= 28.dp) {
                // Too narrow for words: the title's first letter, big, stands in for the picture.
                Text(
                    initial,
                    color = onContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = with(LocalDensity.current) { (width * 0.5f).toSp() },
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = onContainer.copy(alpha = 0.85f),
                    modifier = Modifier.size(if (roomForText) 36.dp else (width * 0.4f).coerceAtLeast(12.dp)),
                )
            }
            if (roomForText) {
                Text(
                    title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}
