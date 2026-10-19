package com.romanianguy.klover.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.history.LastReadStore
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.bouncyClickable
import com.romanianguy.klover.ui.profiles.ProfileAvatar
import com.romanianguy.klover.ui.profiles.rememberCurrentProfile
import com.romanianguy.klover.ui.stats.rememberStatsSummary
import org.koin.compose.koinInject

// left page in book posture: who's reading (tap to switch profile), the app's places low where the thumb rests, then
// Continue.
// onSwitchProfile is null when there's nothing to switch to
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookContentsPage(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onContinue: (chapterId: Int) -> Unit,
    onSwitchProfile: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val last by koinInject<LastReadStore>().data.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxSize(), color = scheme.surfaceContainerLow) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            // A big soft cookie in the corner, turning very slowly: the page is not flat paper.
            Backdrop(Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-50).dp))
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 28.dp, top = 20.dp, bottom = 24.dp),
            ) {
                ProfileHeader(onSwitchProfile)
                // The rows start lower on the page, where the thumb is.
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (destination in TopLevelDestination.entries) {
                        ContentsRow(destination, selected = destination == current, onClick = { onSelect(destination) })
                    }
                }
                if (last.exists) {
                    Spacer(Modifier.height(20.dp))
                    ContinueRow(last.label, onClick = { onContinue(last.chapterId) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Backdrop(modifier: Modifier) {
    val motion = LocalAppSettings.current.motionStyle
    val turn by rememberInfiniteTransition(label = "backdrop").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(if (motion == MotionStyle.Calm) 140_000 else 80_000, easing = LinearEasing)),
        label = "turn",
    )
    Box(
        modifier
            .size(260.dp)
            .graphicsLayer { rotationZ = if (motion == MotionStyle.Reduced) 14f else turn }
            .clip(MaterialShapes.Cookie12Sided.toShape())
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    )
}

@Composable
private fun ProfileHeader(onSwitchProfile: (() -> Unit)?) {
    val profile = rememberCurrentProfile()
    val streak = rememberStatsSummary().currentStreak
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onSwitchProfile != null) Modifier.bouncyClickable(onClick = onSwitchProfile) else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (profile != null) ProfileAvatar(profile, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    profile?.displayName ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (streak > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            pluralStringResource(R.plurals.stats_days, streak, streak),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            if (onSwitchProfile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = stringResource(R.string.profile_switch),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContentsRow(destination: TopLevelDestination, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.surfaceContainerHigh else Color.Transparent,
        label = "row-bg",
    )
    val content by animateColorAsState(if (selected) scheme.onSurface else scheme.onSurfaceVariant, label = "row-fg")
    val grow by animateFloatAsState(
        if (selected) 1f else 0.97f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "row-grow",
    )
    // Each place has its own colour from the app's palette. The tile keeps it when it morphs into the place's shape:
    // the shape of the
    // place you are in is its own colour, not the primary one.
    val (tile, onTile) = when (destination) {
        TopLevelDestination.Home -> scheme.primaryContainer to scheme.onPrimaryContainer
        TopLevelDestination.Library -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TopLevelDestination.WantToRead -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TopLevelDestination.Downloads -> scheme.primaryContainer to scheme.onPrimaryContainer
        TopLevelDestination.Settings -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val motion = LocalAppSettings.current.motionStyle
    // Only the tile of the place you are in turns; the others have no animation running at all.
    val turn = if (selected && motion != MotionStyle.Reduced) {
        rememberInfiniteTransition(label = "tile").animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "tile-turn",
        )
    } else {
        null
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = grow; scaleY = grow }.bouncyClickable(onClick = onClick),
    ) {
        Row(
            Modifier.height(72.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                // The tile morphs between a rounded square and the shape this place has when it is the one you are
                // in, with a
                // bounce; the shape keeps turning slowly while it is.
                val morph = remember(destination) {
                    Morph(MaterialShapes.Square.normalized(), destination.selectedPolygon().normalized())
                }
                val progress by animateFloatAsState(
                    if (selected) 1f else 0f,
                    if (motion == MotionStyle.Reduced) tween(120) else spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "morph",
                )
                val fill = tile
                Box(
                    Modifier
                        .size(if (selected) 48.dp else 44.dp)
                        .graphicsLayer { rotationZ = turn?.value ?: 0f }
                        .clip(MorphShape(morph) { progress })
                        .background(fill),
                )
                Icon(
                    if (selected) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                    tint = onTile,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ContinueRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().bouncyClickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_on_deck), style = MaterialTheme.typography.labelLarge)
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun TopLevelDestination.selectedPolygon(): RoundedPolygon = when (this) {
    TopLevelDestination.Home -> MaterialShapes.Cookie9Sided
    TopLevelDestination.Library -> MaterialShapes.Puffy
    TopLevelDestination.WantToRead -> MaterialShapes.Clover4Leaf
    TopLevelDestination.Downloads -> MaterialShapes.Cookie6Sided
    TopLevelDestination.Settings -> MaterialShapes.Cookie12Sided
}

private class MorphShape(private val morph: Morph, private val progress: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        var first = true
        for (cubic in morph.asCubics(progress().coerceIn(0f, 1f))) {
            if (first) {
                path.moveTo(cubic.anchor0X, cubic.anchor0Y)
                first = false
            }
            path.cubicTo(
                cubic.control0X,
                cubic.control0Y,
                cubic.control1X,
                cubic.control1Y,
                cubic.anchor1X,
                cubic.anchor1Y,
            )
        }
        path.close()
        val scale = Matrix().apply { scale(size.width, size.height) }
        path.transform(scale)
        return Outline.Generic(path)
    }
}
