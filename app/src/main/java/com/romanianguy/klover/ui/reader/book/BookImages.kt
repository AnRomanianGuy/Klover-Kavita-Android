package com.romanianguy.klover.ui.reader.book

import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntSize
import com.romanianguy.klover.data.downloads.LocalResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

// the links Kavita wrote (book-resources?file=..., sometimes scheme-less or relative); a downloaded book answers from
// disk,
// otherwise the app's own client fetches them
internal class BookImages(
    serverUrl: String,
    private val client: OkHttpClient,
    private val local: (rawQuery: String?) -> LocalResource?,
) {
    private val base: HttpUrl? = "${serverUrl.trimEnd('/')}/".toHttpUrlOrNull()
    private val sizes = ConcurrentHashMap<String, IntSize>()

    private fun resolve(src: String): HttpUrl? = base?.resolve(src)

    private fun localFile(url: HttpUrl): LocalResource? =
        if (url.encodedPath.contains("book-resources", ignoreCase = true)) local(url.encodedQuery) else null

    fun model(src: String): Any? {
        val url = resolve(src) ?: return null
        return localFile(url)?.file ?: url.toString()
    }

    // the picture's size in pixels, read from its header without decoding it, or `null` if that cannot be told
    suspend fun size(src: String): IntSize? {
        sizes[src]?.let { return it }
        val url = resolve(src) ?: return null
        val found = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val file = localFile(url)?.file
            // the start of the picture is all it takes to tell its size
            val head: ByteArray = if (file != null) {
                file.inputStream().use { it.readNBytes(HEAD_BYTES) }
            } else {
                val response = try {
                    client.newCall(Request.Builder().url(url).build()).await()
                } catch (e: IOException) {
                    return@withContext null
                }
                response.use { r ->
                    if (!r.isSuccessful) return@withContext null
                    r.body.byteStream().readNBytes(HEAD_BYTES)
                }
            }
            BitmapFactory.decodeByteArray(head, 0, head.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                IntSize(options.outWidth, options.outHeight)
            } else {
                svgFileSize(head.decodeToString())
            }
        }
        if (found != null) sizes[src] = found
        return found
    }
}

private const val HEAD_BYTES = 64 * 1024

// the size an SVG file asks for in its first tag, or the shape of its view box; null when it is not an SVG
internal fun svgFileSize(head: String): IntSize? {
    val tag = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE).find(head)?.value ?: return null
    fun attr(name: String) = Regex("[\\s\"']$name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
    fun px(value: String?) = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && !it.endsWith("%") }
        ?.let { it.removeSuffix("px").toFloatOrNull() ?: lengthEm(it)?.times(16f) }
    val box = attr("viewBox")?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }.orEmpty()
    var w = px(attr("width"))
    var h = px(attr("height"))
    val vw = box.getOrNull(2)
    val vh = box.getOrNull(3)
    if (vw != null && vh != null && vw > 0f && vh > 0f) {
        if (w == null && h == null) {
            w = vw
            h = vh
        } else if (w != null && h == null) {
            h = w * vh / vw
        } else if (h != null && w == null) {
            w = h * vw / vh
        }
    }
    return IntSize((w ?: 300f).toInt().coerceAtLeast(1), (h ?: 150f).toInt().coerceAtLeast(1))
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
