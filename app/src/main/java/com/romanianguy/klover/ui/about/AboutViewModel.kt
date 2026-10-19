package com.romanianguy.klover.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AboutUi(
    val session: Session? = null,
    // `null` while asking; blank when the server could not be asked
    val serverVersion: String? = null,
    val downloadChapters: Int = 0,
    val downloadBytes: Long = 0,
)

class AboutViewModel(
    private val sessions: SessionManager,
    private val api: KavitaApi,
    private val downloads: DownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AboutUi(session = sessions.current))
    val state: StateFlow<AboutUi> = _state

    init {
        viewModelScope.launch {
            val version = runCatching { api.serverInfo().kavitaVersion.orEmpty() }.getOrDefault("")
            _state.value = _state.value.copy(serverVersion = version)
        }
        viewModelScope.launch {
            downloads.entries.collect { list ->
                val done = list.filter { it.isCompleted }
                _state.value = _state.value.copy(downloadChapters = done.size, downloadBytes = done.sumOf { it.bytes })
            }
        }
    }
}
