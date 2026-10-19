package com.romanianguy.klover.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.ui.common.PageSheet

class AutoScrollControls(
    val on: Boolean,
    val speed: Int,
    val onToggle: (Boolean) -> Unit,
    val onSpeed: (Int) -> Unit,
)

@Composable
fun BookmarkButton(viewModel: ReaderViewModel, page: Int) {
    val here by viewModel.bookmarksHere.collectAsStateWithLifecycle()
    val marked = here.any { it.page == page }
    var asking by remember { mutableStateOf(false) }
    IconButton(onClick = { if (viewModel.toggleBookmark(page)) asking = true }) {
        AnimatedContent(
            targetState = marked,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) +
                    fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "bookmark",
        ) { flagged ->
            Icon(
                if (flagged) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(if (flagged) R.string.reader_bookmark_remove else R.string.reader_bookmark_add),
                tint = if (flagged) MaterialTheme.colorScheme.primary else LocalContentColorOrDefault(),
            )
        }
    }
    if (asking) {
        NoteDialog(
            initial = "",
            onSave = { note ->
                viewModel.setBookmarkNote(page, note)
                asking = false
            },
            onSkip = { asking = false },
        )
    }
}

@Composable
private fun LocalContentColorOrDefault() = LocalContentColor.current

// A short note for a flagged page (optional: skipping keeps the flag without one)
@Composable
fun NoteDialog(initial: String, onSave: (String) -> Unit, onSkip: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.reader_bookmark_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                placeholder = { Text(stringResource(R.string.reader_bookmark_note_hint)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.reader_bookmark_skip)) } },
    )
}

@Composable
fun ReaderToolsButton(viewModel: ReaderViewModel, onClick: () -> Unit) {
    val left by viewModel.sleepLeft.collectAsStateWithLifecycle()
    val atEnd by viewModel.sleepAtChapterEnd.collectAsStateWithLifecycle()
    IconButton(onClick = onClick) {
        Box {
            Icon(Icons.Outlined.Bedtime, contentDescription = stringResource(R.string.reader_tools))
            if (left != null || atEnd) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderToolsSheet(viewModel: ReaderViewModel, autoScroll: AutoScrollControls?, onDismiss: () -> Unit) {
    val left by viewModel.sleepLeft.collectAsStateWithLifecycle()
    val atEnd by viewModel.sleepAtChapterEnd.collectAsStateWithLifecycle()
    PageSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.reader_sleep_title), style = MaterialTheme.typography.titleMedium)
                val status = when {
                    left != null -> stringResource(R.string.reader_sleep_left, formatMinutesSeconds(left ?: 0))
                    atEnd -> stringResource(R.string.reader_sleep_at_end)
                    else -> stringResource(R.string.reader_sleep_hint)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (minutes in listOf(15, 30, 45, 60)) {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.startSleep(minutes); onDismiss() },
                            label = { Text(stringResource(R.string.reader_sleep_minutes, minutes)) },
                        )
                    }
                    FilterChip(
                        selected = atEnd,
                        onClick = { viewModel.sleepAtEndOfChapter(); onDismiss() },
                        label = { Text(stringResource(R.string.reader_sleep_chapter_end)) },
                    )
                    if (left != null || atEnd) {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.cancelSleep() },
                            label = { Text(stringResource(R.string.reader_sleep_off)) },
                        )
                    }
                }
            }
            if (autoScroll != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.reader_autoscroll_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.reader_autoscroll_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoScroll.on,
                            onCheckedChange = { autoScroll.onToggle(it); if (it) onDismiss() },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((speed, label) in listOf(
                            1 to R.string.reader_autoscroll_slow,
                            2 to R.string.reader_autoscroll_medium,
                            3 to R.string.reader_autoscroll_fast,
                        )) {
                            FilterChip(
                                selected = autoScroll.speed == speed,
                                onClick = { autoScroll.onSpeed(speed) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepTimerHost(viewModel: ReaderViewModel, onStop: () -> Unit) {
    val done by viewModel.sleepDone.collectAsStateWithLifecycle()
    if (!done) return
    AlertDialog(
        onDismissRequest = { viewModel.cancelSleep() },
        title = { Text(stringResource(R.string.reader_sleep_done_title)) },
        text = { Text(stringResource(R.string.reader_sleep_done_body)) },
        confirmButton = {
            TextButton(onClick = { viewModel.cancelSleep(); onStop() }) { Text(stringResource(R.string.reader_sleep_stop)) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.startSleep(5) }) { Text(stringResource(R.string.reader_sleep_more)) }
        },
    )
}

private fun formatMinutesSeconds(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
