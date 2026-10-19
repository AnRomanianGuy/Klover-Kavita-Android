package com.romanianguy.klover.data.chapters

import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private const val READ = 0.99f

private const val MAX_MARKS = 500

fun markKey(fileId: Int, start: Int) = "$fileId/$start"

fun innerReadFraction(chapter: InnerChapter, filePagesRead: Int, marked: Boolean?): Float = when (marked) {
    true -> 1f
    false -> 0f
    null -> if (chapter.pages > 0) ((filePagesRead - chapter.start).toFloat() / chapter.pages).coerceIn(0f, 1f) else 0f
}

enum class Whole { Read, Unread }

// Kavita doesn't know chapters inside a file, so hand marks are kept on the device per account. They only say what
// differs from the file's progress and go away once the whole file is read or unread
class InnerReadMarks(storage: AccountStorage, scope: CoroutineScope) {
    private val store = AccountStore(
        storage, "chapter-marks.json", MapSerializer(String.serializer(), Boolean.serializer()), emptyMap(), scope,
    )

    val marks: StateFlow<Map<String, Boolean>> = store.data

    fun snapshot(): Map<String, Boolean> = store.data.value

    fun restore(marks: Map<String, Boolean>) = store.set(marks)

    // returns Whole.Read or Whole.Unread when every chapter ends up the same, the marks are then dropped
    fun mark(
        fileId: Int,
        chapters: List<InnerChapter>,
        chapter: InnerChapter,
        read: Boolean,
        filePagesRead: Int,
    ): Whole? {
        var whole: Whole? = null
        store.update { current ->
            val next = LinkedHashMap(current)
            val key = markKey(fileId, chapter.start)
            next.remove(key)
            val own = innerReadFraction(chapter, filePagesRead, null)
            val alreadySo = if (read) own >= READ else own <= 0f
            if (!alreadySo) next[key] = read
            val states = chapters.map { innerReadFraction(it, filePagesRead, next[markKey(fileId, it.start)]) }
            whole = when {
                states.all { it >= READ } -> Whole.Read
                states.all { it <= 0f } -> Whole.Unread
                else -> null
            }
            if (whole != null) chapters.forEach { next.remove(markKey(fileId, it.start)) }
            while (next.size > MAX_MARKS) next.remove(next.keys.first())
            next
        }
        return whole
    }
}
