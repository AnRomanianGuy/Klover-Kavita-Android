package com.romanianguy.klover.ui.profiles

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.romanianguy.klover.R
import com.romanianguy.klover.data.profiles.PROFILE_SHAPES
import com.romanianguy.klover.data.profiles.PROFILE_TONES
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.profiles.SavedProfile
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.reducedMotion
import com.romanianguy.klover.ui.events.LocalActiveEvent
import com.romanianguy.klover.ui.events.drawPumpkin
import org.koin.compose.koinInject
import java.io.File

// opens the profile picker; `null` where there is none (while signed out the picker is the screen itself)
val LocalProfilePicker = staticCompositionLocalOf<(() -> Unit)?> { null }

internal val ToneNames = listOf(
    R.string.tone_clover, R.string.tone_coral, R.string.tone_teal, R.string.tone_amber, R.string.tone_violet,
    R.string.tone_sky, R.string.tone_rose, R.string.tone_lime, R.string.tone_sunset, R.string.tone_plum,
    R.string.tone_mint, R.string.tone_indigo, R.string.tone_magenta, R.string.tone_cyan, R.string.tone_cocoa, R.string.tone_slate,
)

// ring colours: the first follows the theme, the next few are clearly different hues so early profiles don't look
// alike,
// 8 and 9 are other theme colours, later ones are fixed hues (earlier numbers keep their colour)
fun toneColor(tone: Int, scheme: ColorScheme): Color = when (tone.mod(PROFILE_TONES)) {
    0 -> scheme.primary
    1 -> Color(0xFFE5634D)
    2 -> Color(0xFF1FA08F)
    3 -> Color(0xFFF2A100)
    4 -> Color(0xFF8E5CF0)
    5 -> Color(0xFF2F9BE8)
    6 -> Color(0xFFE6498F)
    7 -> Color(0xFF7DB82B)
    8 -> scheme.tertiary
    9 -> scheme.secondary
    10 -> Color(0xFF2FC98E)
    11 -> Color(0xFF4458E8)
    12 -> Color(0xFFC026D3)
    13 -> Color(0xFF18B7D9)
    14 -> Color(0xFF9A6B45)
    else -> Color(0xFF5F7384)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun profileShape(index: Int): Shape {
    val polygon = when (index.mod(PROFILE_SHAPES)) {
        0 -> MaterialShapes.Circle
        1 -> MaterialShapes.Cookie9Sided
        2 -> MaterialShapes.Cookie12Sided
        3 -> MaterialShapes.Clover4Leaf
        4 -> MaterialShapes.Flower
        5 -> MaterialShapes.SoftBurst
        6 -> MaterialShapes.Sunny
        7 -> MaterialShapes.Puffy
        8 -> MaterialShapes.Cookie4Sided
        9 -> MaterialShapes.Cookie6Sided
        10 -> MaterialShapes.Cookie7Sided
        11 -> MaterialShapes.Clover8Leaf
        12 -> MaterialShapes.VerySunny
        13 -> MaterialShapes.Burst
        14 -> MaterialShapes.SoftBoom
        15 -> MaterialShapes.Boom
        16 -> MaterialShapes.PuffyDiamond
        17 -> MaterialShapes.Pentagon
        18 -> MaterialShapes.Gem
        else -> MaterialShapes.Heart
    }
    return polygon.toShape()
}

@Composable
fun ProfileAvatar(profile: SavedProfile, size: Dp, modifier: Modifier = Modifier) {
    val shape = profileShape(profile.shape)
    val store = koinInject<ProfileStore>()
    val scheme = MaterialTheme.colorScheme
    val tone = toneColor(profile.tone, scheme)
    val ring = (size * 0.055f).coerceAtLeast(2.dp)
    val gap = (size * 0.045f).coerceAtLeast(1.5.dp)
    val photo = remember(profile.photo) { profile.photo?.let { File(store.photoDir, it) }?.takeIf { it.isFile } }
    // the border of the event that is on, if the profile chose it
    val event = LocalActiveEvent.current
    val seasonal = event != null && profile.seasonal == event.id
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (seasonal) Modifier.eventRing(ring, shape) else Modifier.border(ring, tone, shape))
                .padding(ring + gap)
                .clip(shape)
                .background(tone.copy(alpha = 0.16f).compositeOver(scheme.surface)),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(photo).memoryCacheKey(photo.name).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                val fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
                Text(profile.initial, color = tone, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
            }
        }
        if (seasonal) {
            Canvas(Modifier.matchParentSize()) { drawPumpkin(Offset(this.size.width * 0.8f, this.size.height * 0.8f), this.size.width * 0.38f) }
        }
    }
}

private val EventColors = listOf(
    Color(0xFFF28C1C), Color(0xFF7A4DD6), Color(0xFFF28C1C), Color(0xFF7A4DD6), Color(0xFFF28C1C),
)

// the ring of a Halloween profile: orange and purple running slowly round it, and standing still with reduced motion.
// The turn is read while drawing, so only the ring is drawn again, not the avatar composed again
@Composable
private fun Modifier.eventRing(ring: Dp, shape: Shape): Modifier {
    val still = LocalAppSettings.current.reducedMotion()
    val turn = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "event-ring").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing)),
            label = "turn",
        )
    }
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        val shader = SweepGradientShader(center, EventColors)
        turn?.let { degrees ->
            shader.setLocalMatrix(android.graphics.Matrix().apply { setRotate(degrees.value, center.x, center.y) })
        }
        // a stroke twice the ring wide, cut to the shape: the ring lies inside the edge, as a border does
        clipPath(path) { drawOutline(outline, ShaderBrush(shader), style = Stroke(ring.toPx() * 2f)) }
    }
}

// the signed-in profile, following the store (`null` for the first moments, or while signed out)
@Composable
fun rememberCurrentProfile(): SavedProfile? {
    val store = koinInject<ProfileStore>()
    val profiles by store.profiles.collectAsState()
    val key by store.activeKey.collectAsState()
    return profiles.firstOrNull { it.key == key }
}

@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val open = LocalProfilePicker.current ?: return
    val profile = rememberCurrentProfile() ?: return
    IconButton(onClick = open, modifier = modifier) {
        ProfileAvatar(profile, size = 34.dp)
    }
}

@Composable
internal fun toneName(tone: Int): String = stringResource(ToneNames[tone.mod(PROFILE_TONES)])
