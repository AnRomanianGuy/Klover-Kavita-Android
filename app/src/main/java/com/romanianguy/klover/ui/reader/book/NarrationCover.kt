package com.romanianguy.klover.ui.reader.book

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.romanianguy.klover.data.covers.checkTextPage
import com.romanianguy.klover.ui.common.CoverReveal
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.PlaceholderCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ART_WIDTH = 480
private const val ART_HEIGHT = 720

// The picture for the media control while reading aloud: the book's cover, or the made-up cover where the app would show
// one (no cover, a first page of text, the data saver). Nothing is drawn; the picture goes to [onArt].
@Composable
fun NarrationCover(enabled: Boolean, url: String?, title: String, onArt: (Bitmap?) -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    var art by remember(url, title) { mutableStateOf<Bitmap?>(null) }
    var needsPlaceholder by remember(url, title) { mutableStateOf(false) }
    val saving = settings.dataSaver && url != null && url.startsWith("http", ignoreCase = true) && !CoverReveal.isRevealed(url)
    LaunchedEffect(enabled, url, title, saving) {
        if (!enabled || art != null) return@LaunchedEffect
        if (url == null || saving) {
            needsPlaceholder = true
            return@LaunchedEffect
        }
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(url).size(ART_WIDTH, ART_HEIGHT).allowHardware(false).build(),
        )
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        val textPage = bitmap != null && settings.textCoverPlaceholder &&
            withContext(Dispatchers.Default) { checkTextPage(url, bitmap) }
        if (bitmap != null && !textPage) art = bitmap else needsPlaceholder = true
    }
    LaunchedEffect(enabled, art) { onArt(if (enabled) art else null) }
    // drawn for a moment to be photographed, then gone: it holds no animation open
    if (enabled && needsPlaceholder && art == null) {
        PlaceholderPhoto(title) {
            art = it
            needsPlaceholder = false
        }
    }
}

@Composable
private fun PlaceholderPhoto(title: String, onDone: (Bitmap) -> Unit) {
    val layer = rememberGraphicsLayer()
    Box(
        Modifier
            .size(240.dp, 360.dp)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
            },
    ) {
        PlaceholderCover(title, Modifier.size(240.dp, 360.dp))
    }
    LaunchedEffect(layer) {
        withFrameNanos { }
        withFrameNanos { }
        onDone(layer.toImageBitmap().asAndroidBitmap())
    }
}
