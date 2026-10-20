package com.romanianguy.klover.ui.reader

import android.content.Context
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

@Stable
interface PageSource {
    val pageCount: Int

    fun isWide(page: Int): Boolean

    // draws page, pushed towards alignment so the two pages of a spread meet at the spine
    @Composable
    fun Page(page: Int, alignment: Alignment, modifier: Modifier)

    // width / height of page when known up front, so the webtoon reader can size its slot before the image arrives
    fun aspectRatio(page: Int): Float? = null

    // one piece of a continuous strip: as wide as the modifier allows, at its natural height
    @Composable
    fun StripPage(page: Int, modifier: Modifier) {
        Page(page, Alignment.Center, modifier.aspectRatio(aspectRatio(page) ?: DEFAULT_PAGE_RATIO))
    }

    // warms caches for a page that will probably be shown soon. Must not throw
    suspend fun prefetch(context: Context, page: Int)
}

internal const val DEFAULT_PAGE_RATIO = 0.7f

// the zoom a page sits in and where the page starts inside the zoomed area, so a page can draw a sharper picture of the
// part that is on screen
internal class PageZoom(val state: ZoomState, val origin: () -> Offset)

internal val LocalPageZoom = compositionLocalOf<PageZoom?> { null }
