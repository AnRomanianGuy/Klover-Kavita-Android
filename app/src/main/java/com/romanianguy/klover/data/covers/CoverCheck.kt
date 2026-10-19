package com.romanianguy.klover.data.covers

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

private const val SAMPLE_W = 64
private const val SAMPLE_H = 96

private const val GREY_SPREAD = 26

// Kavita makes a cover for a book or PDF from its first page: white or grey, with text that shrunk is only a light
// haze.
// a real cover has colour, dark areas or solid black art (a black and white manga cover), so it isn't taken for a
// page
fun looksLikeTextPage(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return false
    var grey = 0
    var dark = 0
    var luminance = 0L
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        if (max(r, max(g, b)) - min(r, min(g, b)) <= GREY_SPREAD) grey++
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        luminance += lum
        if (lum < 70) dark++
    }
    val n = pixels.size
    val allGrey = grey >= n * 0.97
    val bright = luminance / n >= 190
    val littleBlack = dark <= n * 0.03
    return allGrey && bright && littleBlack
}

// hardware bitmaps can't be read, so they're copied to an ordinary one first
fun bitmapLooksLikeTextPage(bitmap: Bitmap): Boolean {
    if (bitmap.width < 8 || bitmap.height < 8) return false
    val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false else bitmap
    val small = Bitmap.createScaledBitmap(readable, SAMPLE_W, SAMPLE_H, true)
    val pixels = IntArray(SAMPLE_W * SAMPLE_H)
    small.getPixels(pixels, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
    if (small !== readable) small.recycle()
    if (readable !== bitmap) readable.recycle()
    return looksLikeTextPage(pixels)
}

private val verdicts = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 600
}

fun cachedTextPageVerdict(url: String): Boolean? = synchronized(verdicts) { verdicts[url] }

fun checkTextPage(url: String, bitmap: Bitmap): Boolean {
    cachedTextPageVerdict(url)?.let { return it }
    val verdict = runCatching { bitmapLooksLikeTextPage(bitmap) }.getOrDefault(false)
    synchronized(verdicts) { verdicts[url] = verdict }
    return verdict
}
