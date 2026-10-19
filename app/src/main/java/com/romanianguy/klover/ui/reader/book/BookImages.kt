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
            if (file != null) {
                BitmapFactory.decodeFile(file.path, options)
            } else {
                val response = try {
                    client.newCall(Request.Builder().url(url).build()).await()
                } catch (e: IOException) {
                    return@withContext null
                }
                response.use { r ->
                    val body = r.body
                    if (!r.isSuccessful) return@withContext null
                    BitmapFactory.decodeStream(body.byteStream(), null, options)
                }
            }
            if (options.outWidth > 0 && options.outHeight > 0) IntSize(options.outWidth, options.outHeight) else null
        }
        if (found != null) sizes[src] = found
        return found
    }
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
