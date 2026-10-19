package com.romanianguy.klover.ui.reader.book

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.prefs.BookFont
import com.romanianguy.klover.data.prefs.BookTheme

data class BookPalette(val background: Color, val text: Color, val link: Color, val selection: Color)

@Composable
fun BookTheme.palette(): BookPalette = when (this) {
    BookTheme.App -> MaterialTheme.colorScheme.let {
        BookPalette(it.surface, it.onSurface, it.primary, it.primaryContainer)
    }

    BookTheme.Paper -> BookPalette(Color(0xFFFAF7F0), Color(0xFF1C1B18), Color(0xFF1A5FB4), Color(0xFFCFE0F7))
    BookTheme.Sepia -> BookPalette(Color(0xFFF1E4C8), Color(0xFF4A3520), Color(0xFF8A4B14), Color(0xFFE2C48F))
    BookTheme.Dark -> BookPalette(Color(0xFF1B1B1F), Color(0xFFD9D9DE), Color(0xFF8AB4F8), Color(0xFF3B4A66))
    BookTheme.Black -> BookPalette(Color.Black, Color(0xFFC6C6CA), Color(0xFF8AB4F8), Color(0xFF2B3A55))
}

fun BookFont.composeFamily(): FontFamily = when (this) {
    BookFont.Serif -> FontFamily.Serif
    BookFont.Sans -> FontFamily.SansSerif
    BookFont.Mono -> FontFamily.Monospace
}

private fun BookTheme.labelRes(): Int = when (this) {
    BookTheme.App -> R.string.book_theme_app
    BookTheme.Paper -> R.string.book_theme_paper
    BookTheme.Sepia -> R.string.book_theme_sepia
    BookTheme.Dark -> R.string.book_theme_dark
    BookTheme.Black -> R.string.book_theme_black
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookThemePicker(selected: BookTheme, onSelect: (BookTheme) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookTheme.entries.forEach { theme ->
            val palette = theme.palette()
            val isSelected = selected == theme
            val ring by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "bookThemeRing",
            )
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .clip(shape)
                    .border(BorderStroke(if (isSelected) 3.dp else 1.dp, ring), shape)
                    .background(palette.background)
                    .toggleable(isSelected, role = Role.RadioButton, onValueChange = { onSelect(theme) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aa", color = palette.text, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(theme.labelRes()),
                    color = palette.text,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
