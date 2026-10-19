package com.romanianguy.klover.data.downloads

import com.romanianguy.klover.data.model.BookChapterItem
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

// one folder per chapter: info.json, cover, p0, p1... (comic pages), book.pdf, or for EPUB s0.html... with toc.json,
// res.json and r/. index.json beside the folders lists them (DownloadStore)
class DownloadFiles(val root: File) {
    fun dir(chapterId: Int) = File(root, chapterId.toString())
    fun info(chapterId: Int) = File(dir(chapterId), "info.json")
    fun cover(chapterId: Int) = File(dir(chapterId), "cover")
    fun page(chapterId: Int, page: Int) = File(dir(chapterId), "p$page")
    fun pdf(chapterId: Int) = File(dir(chapterId), "book.pdf")
    fun section(chapterId: Int, section: Int) = File(dir(chapterId), "s$section.html")
    fun toc(chapterId: Int) = File(dir(chapterId), "toc.json")
    private fun sizesFile(chapterId: Int) = File(dir(chapterId), "sizes.txt")
    private fun resourceIndex(chapterId: Int) = File(dir(chapterId), "res.json")
    fun resource(chapterId: Int, name: String) = File(File(dir(chapterId), "r"), name)

    private val resourcesSerializer = MapSerializer(String.serializer(), ResourceRef.serializer())

    fun size(chapterId: Int): Long =
        dir(chapterId).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun delete(chapterId: Int) {
        dir(chapterId).deleteRecursively()
    }

    fun clear() {
        root.listFiles()?.filter { it.isDirectory }?.forEach { it.deleteRecursively() }
    }

    fun hasContent(file: File): Boolean = file.isFile && file.length() > 0

    fun readInfo(chapterId: Int): ChapterInfoDto? = runCatching {
        KavitaJson.decodeFromString(ChapterInfoDto.serializer(), info(chapterId).readText())
    }.getOrNull()

    fun writeInfo(chapterId: Int, info: ChapterInfoDto) {
        dir(chapterId).mkdirs()
        info(chapterId).writeText(KavitaJson.encodeToString(ChapterInfoDto.serializer(), info))
    }

    fun readToc(chapterId: Int): List<BookChapterItem>? = runCatching {
        KavitaJson.decodeFromString(ListSerializer(BookChapterItem.serializer()), toc(chapterId).readText())
    }.getOrNull()

    fun writeToc(chapterId: Int, items: List<BookChapterItem>) {
        toc(chapterId).writeText(KavitaJson.encodeToString(ListSerializer(BookChapterItem.serializer()), items))
    }

    fun readSizes(chapterId: Int): List<Int>? = runCatching {
        sizesFile(chapterId).readText().split(',').map { it.trim().toInt() }
    }.getOrNull()

    fun writeSizes(chapterId: Int, sizes: List<Int>) {
        runCatching { sizesFile(chapterId).writeText(sizes.joinToString(",")) }
    }

    fun readResources(chapterId: Int): Map<String, ResourceRef> = runCatching {
        KavitaJson.decodeFromString(resourcesSerializer, resourceIndex(chapterId).readText())
    }.getOrDefault(emptyMap())

    fun writeResources(chapterId: Int, resources: Map<String, ResourceRef>) {
        resourceIndex(chapterId).writeText(KavitaJson.encodeToString(resourcesSerializer, resources))
    }
}
