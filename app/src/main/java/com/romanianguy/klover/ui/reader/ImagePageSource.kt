package com.romanianguy.klover.ui.reader

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.network.ServerUrls
import java.io.File

private const val PAGE_DECODE_EDGE = 2400

// widest a webtoon piece is decoded; the strip is never wider than a tablet column, so this is already sharp
private const val STRIP_DECODE_WIDTH = 1600

class ImagePageSource(
    private val chapterId: Int,
    private val info: ChapterInfoDto,
    private val urls: ServerUrls,
    private val localDir: File? = null,
) : PageSource {
    override val pageCount: Int get() = info.pages

    private val dimensions = info.pageDimensions.orEmpty().associateBy { it.pageNumber }

    override fun isWide(page: Int): Boolean =
        dimensions[page]?.let { it.isWide || it.width > it.height } ?: false

    private fun url(page: Int): String? = urls.readerPage(chapterId, page)

    private fun localFile(page: Int): File? =
        localDir?.let { File(it, "p$page") }?.takeIf { it.isFile && it.length() > 0 }

    private fun source(page: Int): Any? = localFile(page) ?: url(page)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Page(page: Int, alignment: Alignment, modifier: Modifier) {
        val context = LocalContext.current
        val data = source(page)
        var state by remember(data) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

        Box(modifier, contentAlignment = Alignment.Center) {
            AsyncImage(
                model = data?.let { request(context, it) },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = alignment,
                onState = { state = it },
                modifier = Modifier.fillMaxSize(),
            )
            when (state) {
                is AsyncImagePainter.State.Loading -> LoadingIndicator()
                is AsyncImagePainter.State.Error -> Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Unit
            }
        }
    }

    override fun aspectRatio(page: Int): Float? =
        dimensions[page]?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }

    // decoded at the screen's width and any height (a webtoon page can be several screens tall); the slot has the
    // right shape up front when the server gave the size
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun StripPage(page: Int, modifier: Modifier) {
        val context = LocalContext.current
        val data = source(page)
        var ratio by remember(data) { mutableStateOf(aspectRatio(page)) }
        var state by remember(data) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        val width = LocalWindowInfo.current.containerSize.width.coerceIn(1, STRIP_DECODE_WIDTH)

        Box(
            modifier.then(ratio.let { r -> if (r != null) Modifier.aspectRatio(r) else Modifier.heightIn(min = 360.dp) }),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = data?.let {
                    ImageRequest.Builder(context).data(it).size(Size(width, Dimension.Undefined)).build()
                },
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                onState = { loaded ->
                    state = loaded
                    if (ratio == null && loaded is AsyncImagePainter.State.Success) {
                        val size = loaded.painter.intrinsicSize
                        if (size.isSpecified && size.height > 0f) ratio = size.width / size.height
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            when (state) {
                is AsyncImagePainter.State.Loading -> LoadingIndicator()
                is AsyncImagePainter.State.Error -> Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Unit
            }
        }
    }

    override suspend fun prefetch(context: Context, page: Int) {
        if (localFile(page) != null) return // already on disk
        url(page)?.let { SingletonImageLoader.get(context).enqueue(request(context, it)) }
    }

    private fun request(context: Context, data: Any) =
        ImageRequest.Builder(context)
            .data(data)
            .size(Size(PAGE_DECODE_EDGE, PAGE_DECODE_EDGE))
            .build()
}
