package com.romanianguy.klover.ui.reader

data class Spread(val pages: List<Int>) {
    val first: Int get() = pages.first()
    val last: Int get() = pages.last()
    val isDouble: Boolean get() = pages.size == 2
}

// dual mode pairs consecutive pages except: the first (cover) stays alone, a wide page takes a whole screen, a lone
// trailing page stays alone,
// and a chapter's first page ([breaks]) never joins the one before. Greedy from the start, so a wide page shifts the
// pairing after it, like printed books
fun buildSpreads(
    pageCount: Int,
    dual: Boolean,
    coverAlone: Boolean = true,
    // pages that begin a chapter: a spread never holds the end of one chapter and the start of the next
    breaks: Set<Int> = emptySet(),
    isWide: (Int) -> Boolean = { false },
): List<Spread> {
    if (pageCount <= 0) return emptyList()
    if (!dual) return List(pageCount) { Spread(listOf(it)) }

    val spreads = ArrayList<Spread>()
    var page = 0
    if (coverAlone) {
        spreads += Spread(listOf(0))
        page = 1
    }
    while (page < pageCount) {
        val next = page + 1
        if (isWide(page) || next >= pageCount || isWide(next) || next in breaks) {
            spreads += Spread(listOf(page))
            page = next
        } else {
            spreads += Spread(listOf(page, next))
            page = next + 1
        }
    }
    return spreads
}

fun List<Spread>.indexOfPage(page: Int): Int {
    if (isEmpty()) return 0
    val index = indexOfFirst { page in it.pages }
    return if (index >= 0) index else if (page < first().first) 0 else lastIndex
}
