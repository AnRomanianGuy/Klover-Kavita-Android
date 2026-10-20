package com.romanianguy.klover.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.io.Closeable
import java.io.File

private const val RENDER_WIDTH = 1500

// how long the zoom has to rest before the sharper picture is rendered
private const val SETTLE_MILLIS = 140L

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

// zoomed past this, the page picture is soft and a sharper one of the visible part is drawn over it
internal const val SHARP_FROM = 1.15f

// a render of the part of a page that is on screen
internal class TileSpec(
    // where it goes, in the zoomed area's coordinates
    val area: Rect,
    // the same part of the page, in PDF points
    val points: Rect,
    // the size to render it at, so one pixel is one screen pixel
    val pixels: IntSize,
)

// [image] is the page picture and [view] the part of the zoomed area that is on screen, both in the area's
// coordinates; the zoom turns the area around [center] by [scale] and moves it by [offset]. A bit more than the visible
// part is taken, so a small pan stays sharp
internal fun tileSpec(
    image: Rect,
    pageSize: Size,
    view: Rect,
    center: Offset,
    scale: Float,
    offset: Offset,
    margin: Float = 0.1f,
    maxPixels: Long = 16_000_000L,
): TileSpec? {
    if (scale < SHARP_FROM || image.width < 1f || image.height < 1f) return null
    fun toArea(p: Offset) = center + (p - center - offset) / scale
    val a = toArea(view.topLeft)
    val b = toArea(view.bottomRight)
    val mx = (b.x - a.x) * margin
    val my = (b.y - a.y) * margin
    val tile = Rect(a.x - mx, a.y - my, b.x + mx, b.y + my).intersect(image)
    if (tile.width < 1f || tile.height < 1f) return null
    var w = tile.width * scale
    var h = tile.height * scale
    if (w * h > maxPixels) {
        val shrink = sqrt(maxPixels / (w * h))
        w *= shrink
        h *= shrink
    }
    val points = Rect(
        (tile.left - image.left) / image.width * pageSize.width,
        (tile.top - image.top) / image.height * pageSize.height,
        (tile.right - image.left) / image.width * pageSize.width,
        (tile.bottom - image.top) / image.height * pageSize.height,
    )
    return TileSpec(tile, points, IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)))
}

private class Tile(val bitmap: Bitmap, val area: Rect)

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
        var box by remember { mutableStateOf(IntSize.Zero) }

        Box(modifier.onSizeChanged { box = it }, contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: LoadingIndicator()
            val zoom = LocalPageZoom.current
            if (zoom != null && bitmap != null) SharpPart(page, alignment, box, zoom)
        }
    }

    // while zoomed in, the visible part of the page is rendered again at screen resolution and drawn over the soft one;
    // it is anchored to the page, so it stays in place while zooming and panning until a new one replaces it
    @OptIn(FlowPreview::class)
    @Composable
    private fun SharpPart(page: Int, alignment: Alignment, box: IntSize, zoom: PageZoom) {
        var tile by remember(page) { mutableStateOf<Tile?>(null) }
        val state = zoom.state
        LaunchedEffect(page, box) {
            val size = pageSizes.getOrNull(page) ?: return@LaunchedEffect
            if (box.width < 1 || box.height < 1 || size.width < 1 || size.height < 1) return@LaunchedEffect
            val fit = min(box.width.toFloat() / size.width, box.height.toFloat() / size.height)
            val shown = IntSize((size.width * fit).roundToInt(), (size.height * fit).roundToInt())
            val at = alignment.align(shown, box, LayoutDirection.Ltr)
            snapshotFlow {
                val origin = zoom.origin()
                val image = Rect(origin + Offset(at.x.toFloat(), at.y.toFloat()), Size(shown.width.toFloat(), shown.height.toFloat()))
                val view = state.region ?: Rect(Offset.Zero, Size(state.size.width.toFloat(), state.size.height.toFloat()))
                tileSpec(image, Size(size.width.toFloat(), size.height.toFloat()), view, state.bounds.center, state.scale, state.offset)
            }
                .distinctUntilChanged { a, b -> a?.area == b?.area && a?.pixels == b?.pixels }
                .debounce(SETTLE_MILLIS)
                .collectLatest { spec ->
                    if (spec == null) {
                        tile = null
                    } else {
                        val rendered = renderPart(page, spec)
                        if (rendered != null) tile = Tile(rendered, spec.area.translate(-zoom.origin()))
                    }
                }
        }
        tile?.let { t ->
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    t.bitmap.asImageBitmap(),
                    dstOffset = IntOffset(t.area.left.roundToInt(), t.area.top.roundToInt()),
                    dstSize = IntSize(t.area.width.roundToInt(), t.area.height.roundToInt()),
                )
            }
        }
    }

    private suspend fun renderPart(page: Int, spec: TileSpec): Bitmap? = withContext(Dispatchers.Default) {
        lock.withLock {
            if (page !in 0 until pageCount) return@withLock null
            val target = try {
                createBitmap(spec.pixels.width, spec.pixels.height)
            } catch (e: OutOfMemoryError) {
                return@withLock null
            }
            target.eraseColor(Color.WHITE)
            renderer.openPage(page).use { pdfPage ->
                val sx = spec.pixels.width / spec.points.width
                val sy = spec.pixels.height / spec.points.height
                val matrix = Matrix().apply {
                    setScale(sx, sy)
                    postTranslate(-spec.points.left * sx, -spec.points.top * sy)
                }
                pdfPage.render(target, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            target
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
