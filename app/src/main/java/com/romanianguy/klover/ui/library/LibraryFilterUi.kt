package com.romanianguy.klover.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.model.SeriesFilterField
import com.romanianguy.klover.data.model.TagItem
import com.romanianguy.klover.data.repo.FilterRef
import com.romanianguy.klover.data.repo.LibraryFilters
import com.romanianguy.klover.ui.common.ChoiceToggleButton
import com.romanianguy.klover.ui.common.ConnectedChoiceGroup
import com.romanianguy.klover.ui.common.PageSheet

private val Statuses = listOf(
    0 to R.string.status_ongoing,
    1 to R.string.status_hiatus,
    2 to R.string.status_completed,
    3 to R.string.status_cancelled,
    4 to R.string.status_ended,
)

private val RatingSteps = listOf(0, 3, 4, 5)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterBar(
    filters: LibraryFilters,
    genres: List<TagItem>,
    onOpen: () -> Unit,
    onToggleRef: (FilterRef) -> Unit,
    onToggleStatus: (Int) -> Unit,
    onUnreadOnly: (Boolean) -> Unit,
    onMinRating: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "filters") {
            ToggleButton(
                checked = !filters.isEmpty,
                onCheckedChange = { onOpen() },
                modifier = Modifier.animateItem(),
                shapes = ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.MinHeight),
                colors = ToggleButtonDefaults.colors(),
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                Text(
                    if (filters.isEmpty) stringResource(R.string.filters_button)
                    else stringResource(R.string.filters_button_count, filters.activeCount),
                )
            }
        }
        // The one that matters most gets its own button, on or off.
        item(key = "unread") {
            ChoiceToggleButton(
                selected = filters.unreadOnly,
                onClick = { onUnreadOnly(!filters.unreadOnly) },
                modifier = Modifier.animateItem(),
            ) { Text(stringResource(R.string.filters_unread_only)) }
        }
        items(Statuses.filter { it.first in filters.statuses }, key = { "status-${it.first}" }) { (status, label) ->
            ChoiceToggleButton(
                selected = true,
                onClick = { onToggleStatus(status) },
                modifier = Modifier.animateItem(),
            ) {
                Text(stringResource(label))
            }
        }
        if (filters.minRating > 0) {
            item(key = "rating") {
                ChoiceToggleButton(selected = true, onClick = { onMinRating(0) }, modifier = Modifier.animateItem()) {
                    Text(stringResource(R.string.filters_rating_min, filters.minRating))
                }
            }
        }
        items(filters.refs.toList(), key = { "ref-${it.field}-${it.id}" }) { ref ->
            ChoiceToggleButton(selected = true, onClick = { onToggleRef(ref) }, modifier = Modifier.animateItem()) {
                Text(ref.label)
            }
        }
    }
}

@Composable
internal fun SearchSuggestions(
    suggestions: List<FilterRef>,
    onPick: (FilterRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        modifier = modifier,
        enter = expandVertically(spatial) + fadeIn(effects),
        exit = shrinkVertically(spatial) + fadeOut(effects),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "label") {
                Text(
                    stringResource(R.string.filters_also_matches),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(suggestions, key = { "s-${it.field}-${it.id}" }) { ref ->
                ChoiceToggleButton(selected = false, onClick = { onPick(ref) }, modifier = Modifier.animateItem()) {
                    Text("${stringResource(ref.kindLabel())} · ${ref.label}")
                }
            }
        }
    }
}

private fun FilterRef.kindLabel(): Int = when (field) {
    SeriesFilterField.Genres -> R.string.filters_kind_genre
    SeriesFilterField.Tags -> R.string.filters_kind_tag
    SeriesFilterField.Writers -> R.string.filters_kind_writer
    SeriesFilterField.Penciller, SeriesFilterField.Inker, SeriesFilterField.Colorist,
    SeriesFilterField.Letterer, SeriesFilterField.CoverArtist -> R.string.filters_kind_artist
    else -> R.string.filters_kind_person
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    filters: LibraryFilters,
    genres: List<TagItem>,
    onDismiss: () -> Unit,
    onToggleRef: (FilterRef) -> Unit,
    onToggleStatus: (Int) -> Unit,
    onUnreadOnly: (Boolean) -> Unit,
    onMinRating: (Int) -> Unit,
    onClear: () -> Unit,
) {
    PageSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)

            SectionTitle(R.string.filters_status)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Statuses.forEach { (status, label) ->
                    ChoiceToggleButton(selected = status in filters.statuses, onClick = { onToggleStatus(status) }) {
                        Text(stringResource(label))
                    }
                }
            }

            SectionTitle(R.string.filters_genre)
            if (genres.isEmpty()) {
                Text(
                    stringResource(R.string.filters_no_genres),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.forEach { genre ->
                        val ref = FilterRef.genre(genre.id, genre.title)
                        ChoiceToggleButton(
                            selected = ref in filters.refs,
                            onClick = { onToggleRef(ref) },
                        ) { Text(genre.title) }
                    }
                }
            }

            SectionTitle(R.string.filters_reading)
            Row {
                ChoiceToggleButton(selected = filters.unreadOnly, onClick = { onUnreadOnly(!filters.unreadOnly) }) {
                    Text(stringResource(R.string.filters_unread_only))
                }
            }

            SectionTitle(R.string.filters_rating)
            ConnectedChoiceGroup(options = RatingSteps, selected = filters.minRating, onSelect = onMinRating) { stars ->
                Text(
                    if (stars == 0) stringResource(R.string.filters_rating_any)
                    else stringResource(R.string.filters_rating_min, stars),
                    maxLines = 1,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClear, enabled = !filters.isEmpty) { Text(stringResource(R.string.filters_clear_all)) }
                Button(onClick = onDismiss) { Text(stringResource(R.string.filters_done)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
