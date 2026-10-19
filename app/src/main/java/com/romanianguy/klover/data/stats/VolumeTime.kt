package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.model.LibraryKind

// pace for a kind of pages; null for books, whose time is in characters and needs the word count
fun paceKindForSeries(format: ContentFormat, library: LibraryKind?): PaceKind? = when {
    format == ContentFormat.Epub -> null
    format == ContentFormat.Pdf -> when (library) {
        LibraryKind.Manga -> PaceKind.Manga
        LibraryKind.Comic, LibraryKind.ComicVine -> PaceKind.Pages
        else -> PaceKind.Pdf
    }
    else -> if (library == LibraryKind.Manga) PaceKind.Manga else PaceKind.Pages
}

// null when nothing is left or there's no pace; a half read chapter has half its pages left
fun volumeSecondsLeft(unreadPages: Float, secondsPerPage: Float?): Long? {
    if (secondsPerPage == null || unreadPages < 0.5f) return null
    return secondsLeft(unreadPages, secondsPerPage)
}
