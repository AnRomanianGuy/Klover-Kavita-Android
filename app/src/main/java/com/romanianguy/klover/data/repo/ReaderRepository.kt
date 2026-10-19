package com.romanianguy.klover.data.repo

import com.romanianguy.klover.data.model.BookChapterItem
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.model.ProgressDto
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer

data class TocEntry(val title: String, val page: Int, val depth: Int)

fun flattenToc(items: List<BookChapterItem>, depth: Int = 0): List<TocEntry> =
    items.flatMap { listOf(TocEntry(it.title, it.page, depth)) + flattenToc(it.children, depth + 1) }

class ReaderRepository(private val api: KavitaApi) {
    suspend fun chapterInfo(chapterId: Int): ChapterInfoDto = api.chapterInfo(chapterId)

    // words in the book, as the server counted them; 0 when it did not or cannot be asked (offline)
    suspend fun chapterWordCount(chapterId: Int): Long = runCatching { api.chapter(chapterId).wordCount }.getOrDefault(0L)

    // saved position of chapterId, or `null` if the server has none / the call fails
    suspend fun progress(chapterId: Int): ProgressDto? = runCatching { api.progress(chapterId) }.getOrNull()

    suspend fun saveProgress(info: ChapterInfoDto, chapterId: Int, page: Int) {
        api.saveProgress(
            ProgressDto(
                volumeId = info.volumeId,
                chapterId = chapterId,
                pageNum = page,
                seriesId = info.seriesId,
                libraryId = info.libraryId,
            ),
        )
    }

    suspend fun bookPage(chapterId: Int, page: Int): String = withContext(Dispatchers.IO) {
        val text = api.bookPage(chapterId, page).string()
        // ASP.NET may JSON-encode a string result depending on content negotiation; undo that if it did.
        if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
            runCatching { KavitaJson.decodeFromString(String.serializer(), text) }.getOrDefault(text)
        } else {
            text
        }
    }

    suspend fun bookToc(chapterId: Int): List<TocEntry> = flattenToc(api.bookChapters(chapterId))

    suspend fun bookChapters(chapterId: Int): List<BookChapterItem> = api.bookChapters(chapterId)

    // neighbouring chapter ids; `null` when there is none (Kavita answers -1)
    suspend fun nextChapter(info: ChapterInfoDto, chapterId: Int): Int? =
        runCatching { api.nextChapter(info.seriesId, info.volumeId, chapterId) }.getOrNull()?.takeIf { it > 0 }

    suspend fun previousChapter(info: ChapterInfoDto, chapterId: Int): Int? =
        runCatching { api.prevChapter(info.seriesId, info.volumeId, chapterId) }.getOrNull()?.takeIf { it > 0 }
}
