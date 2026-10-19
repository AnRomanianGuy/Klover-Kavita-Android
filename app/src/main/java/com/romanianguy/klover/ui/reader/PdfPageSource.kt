package com.romanianguy.klover.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

private const val RENDER_WIDTH = 1500

// no rendered page is taller than this: a long-strip PDF page would otherwise ask for a bitmap of hundreds of
// megabytes
private const val RENDER_MAX_HEIGHT = 6000

// RENDER_WIDTH wide, as tall as the shape asks but capped at RENDER_MAX_HEIGHT (the page shrinks past that); never
// zero
internal fun pdfRenderSize(pageWidth: Int, pageHeight: Int): Pair<Int, Int> {
    val w = pageWidth.coerceAtLeast(1)
    val h = pageHeight.coerceAtLeast(1)
    val height = RENDER_WIDTH.toLong() * h / w
    if (height <= RENDER_MAX_HEIGHT) return RENDER_WIDTH to height.toInt().coerceAtLeast(1)
    val width = (RENDER_WIDTH.toLong() * RENDER_MAX_HEIGHT / height).toInt().coerceAtLeast(1)
    return width to RENDER_MAX_HEIGHT
}

// PdfRenderer allows one open page at a time and isn't thread-safe, hence the mutex
class PdfPageSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val pageSizes: List<IntSize>,
) : PageSource, Closeable {
    override val pageCount: Int get() = pageSizes.size

    override fun isWide(page: Int): Boolean = pageSizes.getOrNull(page)?.let { it.width > it.height } ?: false

    override fun aspectRatio(page: Int): Float? =
        pageSizes.getOrNull(page)?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height }

    private val lock = Mutex()
    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Page(page: Int, alignment: Alignment, modifier: Modifier) {
        var bitmap by remember(page) { mutableStateOf(cache[page]) }
        LaunchedEffect(page) { if (bitmap == null) bitmap = render(page) }

        Box(modifier, contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: LoadingIndicator()
        }
    }

    override suspend fun prefetch(context: Context, page: Int) {
        if (page in 0 until pageCount && cache[page] == null) runCatching { render(page) }
    }

    private suspend fun render(page: Int): Bitmap? = withContext(Dispatchers.Default) {
        cache[page]?.let { return@withContext it }
        val bitmap = lock.withLock {
            cache[page]?.let { return@withLock it }
            if (page !in 0 until pageCount) return@withLock null
            renderer.openPage(page).use { pdfPage ->
                val (width, height) = pdfRenderSize(pdfPage.width, pdfPage.height)
                // A page that still does not fit in memory is shown as a page that did not load, not as a crash.
                val target = try {
                    createBitmap(width, height)
                } catch (e: OutOfMemoryError) {
                    return@use null
                }
                target.also {
                    it.eraseColor(Color.WHITE) // PDFs have no background of their own
                    pdfPage.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
        bitmap?.also { cache.put(page, it) }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        cache.evictAll()
    }

    companion object {
        // opens file and measures every page up front so wide pages can be detected for spreads
        suspend fun open(file: File): PdfPageSource = withContext(Dispatchers.IO) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                val sizes = List(renderer.pageCount) { index ->
                    renderer.openPage(index).use { IntSize(it.width, it.height) }
                }
                PdfPageSource(descriptor, renderer, sizes)
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
        }
    }
}
