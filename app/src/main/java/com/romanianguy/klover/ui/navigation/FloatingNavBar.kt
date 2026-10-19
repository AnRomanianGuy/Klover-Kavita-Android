package com.romanianguy.klover.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.prefs.NavLabels
import com.romanianguy.klover.ui.common.LocalAppSettings

// space the bar takes at the bottom, scrolling screens pad by it; zero without a floating bar
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

val FloatingBarFootprint: Dp = 88.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingNavBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavItem(
                destination = destination,
                selected = destination == current,
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "nav-item-container",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics {
            role = Role.Tab
            this.selected = selected
        },
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.icon,
                // The label is shown next to the icon when selected; otherwise the icon needs a description.
                contentDescription = if (selected && LocalAppSettings.current.navLabels == NavLabels.Selected) {
                    null
                } else {
                    stringResource(destination.label)
                },
            )
            AnimatedVisibility(
                visible = when (LocalAppSettings.current.navLabels) {
                    NavLabels.Selected -> selected
                    NavLabels.Always -> true
                    NavLabels.Never -> false
                },
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Text(
                    text = stringResource(destination.label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
