package com.romanianguy.klover.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// PdfRenderer needs a seekable local file and Kavita only serves the whole document
class PdfCache(
    private val client: OkHttpClient,
    private val dir: File,
) {
    suspend fun fetch(chapterId: Int, url: String): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        pruneOld()

        val target = File(dir, "chapter-$chapterId.pdf")
        if (target.isFile && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }

        // Write to a temp file and rename, so a cancelled / failed download never leaves a half-written PDF.
        val partial = File(dir, "chapter-$chapterId.pdf.part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PDF download failed: HTTP ${response.code}")
            response.body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        }
        if (!partial.renameTo(target)) throw IOException("Could not store the downloaded PDF")
        target
    }

    // keeps the cache from growing without bound: PDFs not opened for a week are removed
    private fun pruneOld() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_AGE_DAYS)
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private companion object {
        const val MAX_AGE_DAYS = 7L
    }
}
