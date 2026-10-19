package com.romanianguy.klover.ui.common

import androidx.compose.runtime.mutableStateMapOf

// covers asked for from the quick view despite the data saver, remembered until the app closes
object CoverReveal {
    private val urls = mutableStateMapOf<String, Boolean>()

    fun isRevealed(url: String): Boolean = urls[url] == true

    fun reveal(url: String) {
        urls[url] = true
    }
}
