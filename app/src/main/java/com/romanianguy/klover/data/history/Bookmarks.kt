package com.romanianguy.klover.data.history

import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Bookmark(
    val chapterId: Int,
    val seriesId: Int,
    val seriesName: String,
    val title: String,
    val page: Int,
    val note: String = "",
    val at: Long = 0,
)

private const val MAX_BOOKMARKS = 400

fun toggled(bookmarks: List<Bookmark>, bookmark: Bookmark): List<Bookmark> {
    val there = bookmarks.any { it.chapterId == bookmark.chapterId && it.page == bookmark.page }
    return if (there) {
        bookmarks.filterNot { it.chapterId == bookmark.chapterId && it.page == bookmark.page }
    } else {
        (bookmarks + bookmark).takeLast(MAX_BOOKMARKS)
    }
}

class Bookmarks(storage: AccountStorage, scope: CoroutineScope) {
    private val store = AccountStore(
        storage,
        "bookmarks.json",
        ListSerializer(Bookmark.serializer()),
        emptyList(),
        scope,
    )

    val items: StateFlow<List<Bookmark>> = store.data

    fun toggle(bookmark: Bookmark): Boolean {
        store.update { toggled(it, bookmark) }
        return store.data.value.any { it.chapterId == bookmark.chapterId && it.page == bookmark.page }
    }

    fun setNote(chapterId: Int, page: Int, note: String) = store.update { list ->
        list.map { if (it.chapterId == chapterId && it.page == page) it.copy(note = note.take(300)) else it }
    }

    fun remove(bookmark: Bookmark) = store.update { list -> list - bookmark }

    fun flush() = store.flush()
}
