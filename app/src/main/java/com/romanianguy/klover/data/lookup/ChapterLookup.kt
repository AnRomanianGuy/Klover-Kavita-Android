package com.romanianguy.klover.data.lookup

import com.romanianguy.klover.data.chapters.InnerChapter
import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

@Serializable
data class LookupChapter(val number: String, val title: String?, val pages: Int)

data class Candidate(val id: String, val titles: List<String>)

internal fun normalizeTitle(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }

private fun words(title: String): Set<String> = title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()

// same title as a query (ignoring case and punctuation) or sharing 80% of its words; a wrong match shows wrong
// chapters, so unsure is null
internal fun bestMatch(queries: List<String>, candidates: List<Candidate>): Candidate? {
    val wanted = queries.map(::normalizeTitle).filter { it.isNotEmpty() }
    candidates.firstOrNull { c -> c.titles.any { normalizeTitle(it) in wanted } }?.let { return it }
    var best: Candidate? = null
    var bestScore = 0.8f
    for (c in candidates) for (title in c.titles) for (q in queries) {
        val a = words(q)
        val b = words(title)
        if (a.isEmpty() || b.isEmpty()) continue
        val score = a.intersect(b).size.toFloat() / a.union(b).size
        if (score >= bestScore) {
            best = c
            bestScore = score
        }
    }
    return best
}

internal fun parseSearch(json: String): List<Candidate> = runCatching {
    KavitaJson.parseToJsonElement(json).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { item ->
        val obj = item.jsonObject
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val attributes = obj["attributes"]?.jsonObject ?: return@mapNotNull null
        val titles = ArrayList<String>()
        attributes["title"]?.jsonObject?.values?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(titles::add) }
        (attributes["altTitles"] as? JsonArray)?.forEach { alt ->
            (alt as? JsonObject)?.values?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(titles::add) }
        }
        Candidate(id, titles)
    }
}.getOrDefault(emptyList())

internal fun parseVolumeChapters(json: String): List<LookupChapter> = runCatching {
    val all = KavitaJson.parseToJsonElement(json).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { item ->
        val attributes = item.jsonObject["attributes"]?.jsonObject ?: return@mapNotNull null
        val number = attributes["chapter"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val pages = attributes["pages"]?.jsonPrimitive?.intOrNull ?: 0
        if (pages <= 0) return@mapNotNull null
        LookupChapter(
            number,
            attributes["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            pages,
        )
    }
    all.groupBy { it.number }.values.map { copies -> copies.maxBy { it.pages } }
        .sortedBy { it.number.toFloatOrNull() ?: Float.MAX_VALUE }
}.getOrDefault(emptyList())

private const val MIN_PAGES = 3

// the catalogue's page counts share out the file's pages in proportion, so starts are only close (marked as
// estimates)
fun estimateInnerChapters(chapters: List<LookupChapter>, filePages: Int): List<InnerChapter> {
    if (chapters.size < 2 || filePages < chapters.size * MIN_PAGES) return emptyList()
    val total = chapters.sumOf { it.pages }
    if (total <= 0) return emptyList()
    // A file of a single chapter of the volume is not the whole volume: the pages must be about as many as the
    // volume's.
    val ratio = filePages.toFloat() / total
    if (ratio < 0.5f || ratio > 2f) return emptyList()
    val starts = IntArray(chapters.size)
    var cumulative = 0
    for (i in chapters.indices) {
        starts[i] = if (i == 0) 0 else maxOf(
            (filePages * cumulative.toFloat() / total).roundToInt(),
            starts[i - 1] + MIN_PAGES,
        )
        cumulative += chapters[i].pages
    }
    if (filePages - starts.last() < MIN_PAGES) return emptyList()
    return chapters.mapIndexed { i, c ->
        InnerChapter(
            start = starts[i],
            endExclusive = if (i + 1 < chapters.size) starts[i + 1] else filePages,
            title = "Chapter ${c.number}" + (c.title?.let { " · $it" } ?: ""),
            number = c.number,
            estimated = true,
        )
    }
}

// MangaDex's public catalogue, no key. Only the title and volume are sent, through its own plain client, never
// Kavita's. Results are cached
class ChapterLookup(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val listSerializer = ListSerializer(LookupChapter.serializer())

    suspend fun volumeChapters(titles: List<String>, volume: Int, network: Boolean): List<LookupChapter>? {
        val usable = titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (usable.isEmpty()) return null
        val file = File(cacheDir, key(usable.first(), volume) + ".json")
        readCache(file)?.let { return it.takeIf { list -> list.isNotEmpty() } }
        if (!network) return null
        return withContext(Dispatchers.IO) {
            val found = runCatching { fetch(usable, volume) }.getOrNull()
            // A failed request is not kept (it may be only the network); a successful empty answer is.
            if (found != null) writeCache(file, found)
            found?.takeIf { it.isNotEmpty() }
        }
    }

    private fun fetch(titles: List<String>, volume: Int): List<LookupChapter> {
        val search = "https://api.mangadex.org/manga".toHttpUrl().newBuilder()
            .addQueryParameter("title", titles.first())
            .addQueryParameter("limit", "6")
            .addQueryParameter("order[relevance]", "desc")
            .build()
        val match = bestMatch(titles, parseSearch(get(search))) ?: return emptyList()
        val chapters = "https://api.mangadex.org/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("manga", match.id)
            .addQueryParameter("volume[]", volume.toString())
            .addQueryParameter("translatedLanguage[]", "en")
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("order[chapter]", "asc")
            .addQueryParameter("limit", "100")
            .build()
        return parseVolumeChapters(get(chapters))
    }

    private fun get(url: okhttp3.HttpUrl): String {
        val request = Request.Builder().url(url).header("User-Agent", "Klover (Android Kavita client)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body.string()
        }
    }

    private fun key(title: String, volume: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${normalizeTitle(title)}|$volume".toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun readCache(file: File): List<LookupChapter>? {
        if (!file.isFile) return null
        val list = runCatching { KavitaJson.decodeFromString(listSerializer, file.readText()) }.getOrNull() ?: return null
        val age = now() - file.lastModified()
        val keep = if (list.isEmpty()) 3L * DAY else 60L * DAY
        return list.takeIf { age < keep }
    }

    private fun writeCache(file: File, list: List<LookupChapter>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(KavitaJson.encodeToString(listSerializer, list))
        }
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
