package com.romanianguy.klover.ui.reader

// Kavita stores pages read (PagesRead = pageNum) and finishes a chapter when PagesRead >= Pages. The reader is
// zero-based:
// page 1 -> index 0 -> saved 1, page 6 -> 5 -> 6, last -> n-1 -> n (this marks the chapter read)

// the value to save for a reader whose last visible page has zero-based index lastVisibleIndex
fun pagesRead(lastVisibleIndex: Int, pageCount: Int): Int = (lastVisibleIndex + 1).coerceIn(
    0,
    pageCount.coerceAtLeast(0),
)

// nothing read starts at the beginning, and so does a finished chapter
fun resumeIndex(savedPagesRead: Int, pageCount: Int): Int =
    if (pageCount <= 0 || savedPagesRead >= pageCount) 0 else (savedPagesRead - 1).coerceAtLeast(0)
