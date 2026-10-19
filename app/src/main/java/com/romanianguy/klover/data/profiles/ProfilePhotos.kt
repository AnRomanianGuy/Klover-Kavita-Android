package com.romanianguy.klover.data.profiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private const val SIDE = 384

// centre-cropped square, scaled down and copied into the app's folder so it doesn't depend on the original; null if
// unreadable
suspend fun importProfilePhoto(context: Context, store: ProfileStore, key: String, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val shortest = min(info.size.width, info.size.height)
            if (shortest > SIDE) {
                val scale = SIDE.toFloat() / shortest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        // A new name each time, so a changed picture is not shown from the cache of the old one.
        val name = "${key.take(16)}-${System.currentTimeMillis()}.jpg"
        store.photoDir.mkdirs()
        File(store.photoDir, name).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        store.setPhoto(key, name)
        name
    }.getOrNull()
}
