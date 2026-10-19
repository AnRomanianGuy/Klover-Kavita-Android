package com.romanianguy.klover.data.chapters

import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.model.ContentFormat

// Kavita counts a volume's file as one chapter, but the page names often show the real ones (a folder each, or
// c012_p004.jpg)
data class InnerChapter(
    val start: Int,
    val endExclusive: Int,
    val title: String,
    val number: String?,
    // estimated from a catalogue, so the start is only close
    val estimated: Boolean = false,
) {
    val pages: Int get() = endExclusive - start

    operator fun contains(page: Int) = page in start until endExclusive
}

fun detectInnerChapters(info: ChapterInfoDto): List<InnerChapter> {
    if (!ContentFormat.from(info.seriesFormat).isPaged) return emptyList()
    val pages = info.pageDimensions.orEmpty().sortedBy { it.pageNumber }
    if (pages.size != info.pages) return emptyList()
    return detectInnerChapters(pages.map { it.fileName.orEmpty() })
}

fun innerChapterAt(chapters: List<InnerChapter>, page: Int): InnerChapter? = chapters.firstOrNull { page in it }

private const val MIN_PAGES = 3

private val EXTENSION = Regex("\\.[A-Za-z0-9]{2,5}$")

private val CHAPTER_TOKEN = Regex("(?i)(?:^|[^a-z])(?:chapter|chap|ch|episode|ep|c)[\\s._-]*0*(\\d{1,4}(?:\\.\\d{1,2})?)(?![a-z0-9])")

private val VOLUME_TOKEN = Regex("(?i)(?:^|[^a-z])(?:volume|vol|v)[\\s._-]*0*(\\d{1,3})(?![a-z0-9])")

private val PAGE_TAIL = Regex("(?i)[\\s._-]*(?:pages?|pg|p|img|image|scan)?[\\s._-]*\\d+$")

private fun baseName(name: String) = name.replace('/', '_').replace('\\', '_').replace(EXTENSION, "").trim()

// from the page names: a changing chapter number (c012_p004) or a changing name part before the page number. Plain
// 001.jpg says nothing
fun detectInnerChapters(fileNames: List<String>): List<InnerChapter> {
    if (fileNames.size < MIN_PAGES * 2) return emptyList()
    val names = fileNames.map(::baseName)
    return fromNumbers(names) ?: fromPrefixes(names) ?: emptyList()
}

private fun fromNumbers(names: List<String>): List<InnerChapter>? {
    // "volume|chapter": a file holding several volumes may number its chapters again in each, so the volume is part
    // of the key.
    val numbers = names.map { name ->
        CHAPTER_TOKEN.find(name)?.let { chapter ->
            val volume = VOLUME_TOKEN.find(name)?.groupValues?.get(1)?.let(::plain).orEmpty()
            "$volume|${chapter.groupValues[1]}"
        }
    }
    // Most pages must say a chapter, or the matches are a coincidence (a "c" in a name).
    if (numbers.count { it != null } < names.size * 0.8) return null
    // Pages that say nothing (a cover first, say) belong to the chapter they sit next to.
    val labelled = ArrayList<String?>(numbers)
    var carry: String? = null
    for (i in labelled.indices) if (labelled[i] != null) carry = labelled[i] else labelled[i] = carry
    val firstKnown = labelled.firstOrNull { it != null } ?: return null
    for (i in labelled.indices) if (labelled[i] == null) labelled[i] = firstKnown
    val keys = labelled.map { it!! }
    val severalVolumes = keys.map { it.substringBefore('|') }.filter { it.isNotEmpty() }.distinct().size > 1
    return build(keys) { key ->
        val volume = key.substringBefore('|')
        val number = plain(key.substringAfter('|'))
        val title = if (severalVolumes && volume.isNotEmpty()) "Vol. $volume · Chapter $number" else "Chapter $number"
        title to number
    }
}

private fun fromPrefixes(names: List<String>): List<InnerChapter>? {
    val prefixes = names.map { PAGE_TAIL.replace(it, "").trim(' ', '_', '-', '.') }
    if (prefixes.any { it.isEmpty() }) return null
    val distinct = prefixes.distinct()
    if (distinct.size < 2) return null
    // A folder per chapter named by its number alone ("21", "21.5", "10.99"), perhaps inside one shared parent
    // folder.
    numberedFolders(prefixes)?.let { numbers -> build(numbers) { "Chapter ${plain(it)}" to plain(it) }?.let { return it } }
    return build(prefixes) { label ->
        val number = (CHAPTER_TOKEN.find(label)?.groupValues?.get(1) ?: label.takeIf { NUMBER_ONLY.matches(it) })?.let(::plain)
        label.replace('_', ' ').replace(Regex("\\s+"), " ").trim() to number
    }
}

private fun plain(number: String): String = number.trimStart('0').let { if (it.isEmpty() || it.startsWith('.')) "0$it" else it }

private val NUMBER_ONLY = Regex("\\d{1,4}(?:\\.\\d{1,2})?")

private fun numberedFolders(labels: List<String>): List<String>? {
    val tokens = labels.map { label -> label.split('_').filter { it.isNotBlank() } }
    val first = tokens.first()
    var shared = 0
    while (shared < first.size && tokens.all { shared < it.size && it[shared] == first[shared] }) shared++
    val rest = tokens.map { it.drop(shared).joinToString("_") }
    return rest.takeIf { list -> list.all { NUMBER_ONLY.matches(it) } }
}

private fun build(labels: List<String>, describe: (String) -> Pair<String, String?>): List<InnerChapter>? {
    val runs = ArrayList<Pair<Int, String>>() // start page, label
    for (i in labels.indices) if (i == 0 || labels[i] != labels[i - 1]) runs += i to labels[i]
    // The same label coming back later means the label does not tell chapters apart.
    if (runs.map { it.second }.distinct().size != runs.size) return null
    if (runs.size < 2) return null
    val chapters = runs.mapIndexed { index, (start, label) ->
        val end = runs.getOrNull(index + 1)?.first ?: labels.size
        val (title, number) = describe(label)
        InnerChapter(start, end, title, number)
    }
    // mostly tiny chapters means the names were read wrongly; a few short ones are real
    if (chapters.count { it.pages < MIN_PAGES } > maxOf(1, chapters.size / 4)) return null
    return chapters
}
