package com.romanianguy.klover.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.chapters.InnerChapter

@Composable
internal fun ChapterList(chapters: List<InnerChapter>, currentPage: Int, onSelect: (InnerChapter) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (chapters.any { it.estimated }) {
            item(key = "estimated") {
                Text(
                    stringResource(R.string.volume_chapters_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
        itemsIndexed(chapters, key = { _, chapter -> chapter.start }) { _, chapter ->
            val current = currentPage in chapter
            ListItem(
                modifier = Modifier.clickable { onSelect(chapter) },
                headlineContent = { Text(chapter.title) },
                supportingContent = { Text(pluralStringResource(R.plurals.series_pages, chapter.pages, chapter.pages)) },
                colors = ListItemDefaults.colors(
                    containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
            )
        }
    }
}

@Composable
internal fun ChaptersButton(chapters: List<InnerChapter>, onClick: () -> Unit) {
    if (chapters.size < 2) return
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.FormatListBulleted,
            contentDescription = stringResource(R.string.reader_chapters),
        )
    }
}
