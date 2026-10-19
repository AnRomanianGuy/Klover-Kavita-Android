package com.romanianguy.klover.data.network

import com.romanianguy.klover.data.session.Session
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

const val PLACEHOLDER_HOST = "kavita.klover.invalid"
const val PLACEHOLDER_BASE_URL = "https://$PLACEHOLDER_HOST/"

// candidate base URLs, most likely first: an explicit scheme is kept, public hosts try https then http, LAN ones the
// other way round
fun candidateServerUrls(input: String): List<String> {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return emptyList()
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
            listOf(trimmed)
        // Home servers are usually plain http, public ones https: try the likelier scheme first so the
        // fallback is rarely needed (a wrong https guess against an http server costs a timeout).
        isLikelyLocalHost(trimmed) -> listOf("http://$trimmed", "https://$trimmed")
        else -> listOf("https://$trimmed", "http://$trimmed")
    }
    return withScheme.mapNotNull { it.toHttpUrlOrNull() }.map { it.toString().trimEnd('/') }
}

fun isLikelyLocalHost(input: String): Boolean {
    val host = input.substringBefore('/').substringBefore(':').lowercase()
    if (host.isEmpty()) return false
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")) return true
    if (!host.contains('.')) return true
    val octets = host.split('.').mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) return false
    val (a, b) = octets
    return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || a == 127
}

fun HttpUrl.isSameServerAs(session: Session): Boolean {
    val base = session.serverUrl.toHttpUrlOrNull() ?: return false
    return scheme == base.scheme && host == base.host && port == base.port &&
        encodedPath.startsWith(base.encodedPath.trimEnd('/'))
}

// absolute URLs for image endpoints; the API key is included so they work without auth headers
class ServerUrls(private val sessionProvider: () -> Session?) {
    fun seriesCover(seriesId: Int): String? = image("series-cover", "seriesId", seriesId)
    fun volumeCover(volumeId: Int): String? = image("volume-cover", "volumeId", volumeId)
    fun chapterCover(chapterId: Int): String? = image("chapter-cover", "chapterId", chapterId)
    fun libraryCover(libraryId: Int): String? = image("library-cover", "libraryId", libraryId)

    // one page image of an image/archive chapter. Pages are zero-based
    fun readerPage(chapterId: Int, page: Int): String? = sessionProvider()?.let {
        "${it.serverUrl}/api/Reader/image?chapterId=$chapterId&page=$page&apiKey=${it.apiKey.urlEncoded()}"
    }

    fun pdf(chapterId: Int): String? = sessionProvider()?.let {
        "${it.serverUrl}/api/Reader/pdf?chapterId=$chapterId&apiKey=${it.apiKey.urlEncoded()}"
    }

    fun bookResource(chapterId: Int, file: String): String? = sessionProvider()?.let {
        "${it.serverUrl}/api/book/$chapterId/book-resources?file=${file.urlEncoded().replace("+", "%20")}" +
            "&apiKey=${it.apiKey.urlEncoded()}"
    }

    private fun image(endpoint: String, idName: String, id: Int): String? = sessionProvider()?.let {
        "${it.serverUrl}/api/Image/$endpoint?$idName=$id&apiKey=${it.apiKey.urlEncoded()}"
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
}
