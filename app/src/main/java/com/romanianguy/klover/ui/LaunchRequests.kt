package com.romanianguy.klover.ui

import kotlinx.coroutines.flow.MutableStateFlow

object LaunchRequests {
    data class Continue(val chapterId: Int, val account: String)

    val pending = MutableStateFlow<Continue?>(null)
}
