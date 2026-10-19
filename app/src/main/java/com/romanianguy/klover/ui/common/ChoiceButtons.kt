package com.romanianguy.klover.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.data.prefs.MotionStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    // The button you pick squashes a little and springs back past its size, so the choice lands with a bounce.
    val pop = remember { Animatable(1f) }
    val bouncy = LocalAppSettings.current.motionStyle == MotionStyle.Bouncy
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstComposition) {
            firstComposition = false
            return@LaunchedEffect
        }
        if (selected && bouncy) {
            pop.snapTo(0.95f)
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f))
        }
    }
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .semantics { role = Role.RadioButton },
        shapes = ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.MinHeight),
        colors = ToggleButtonDefaults.colors(),
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Row {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            }
        }
        label()
    }
}

// the selected button is scrolled into view
@Composable
fun <T> ChoiceButtonRow(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    label: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = options.indexOfFirst { it == selected }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween * 4),
    ) {
        items(options.size, key = { key(options[it]) }) { index ->
            val option = options[index]
            ChoiceToggleButton(selected = option == selected, onClick = { onSelect(option) }) { label(option) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (T) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = shapes,
                colors = ToggleButtonDefaults.colors(),
            ) { label(option) }
        }
    }
}
