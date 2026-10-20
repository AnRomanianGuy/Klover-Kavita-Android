package com.romanianguy.klover.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.update.CheckResult
import com.romanianguy.klover.data.update.UpdateChecker
import com.romanianguy.klover.data.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AboutUi(
    val session: Session? = null,
    // `null` while asking; blank when the server could not be asked
    val serverVersion: String? = null,
    val downloadChapters: Int = 0,
    val downloadBytes: Long = 0,
    val checking: Boolean = false,
    // what the last "Check now" found; null before it was pressed
    val checkResult: CheckResult? = null,
)

private const val MIN_CHECK_MS = 900L

class AboutViewModel(
    private val sessions: SessionManager,
    private val api: KavitaApi,
    private val downloads: DownloadManager,
    private val updates: UpdateChecker,
) : ViewModel() {
    private val _state = MutableStateFlow(AboutUi(session = sessions.current))
    val state: StateFlow<AboutUi> = _state
    val announcement: StateFlow<UpdateInfo?> = updates.announcement

    fun checkForUpdate() {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true)
        updates.clearSkip()
        viewModelScope.launch {
            // an answer in a few milliseconds would only flicker; the spinner stays long enough to be seen
            val started = System.currentTimeMillis()
            val result = updates.check()
            delay((MIN_CHECK_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.value = _state.value.copy(checking = false, checkResult = result)
        }
    }

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
