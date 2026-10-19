package com.romanianguy.klover.ui.downloads

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.downloads.DownloadEntry
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.downloads.DownloadState
import com.romanianguy.klover.data.network.ServerUrls
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SeriesGroup(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val items: List<DownloadEntry>,
    val bytes: Long,
)

@Immutable
data class DownloadsUi(
    val active: List<DownloadEntry> = emptyList(),
    val groups: List<SeriesGroup> = emptyList(),
    val totalBytes: Long = 0,
    val waitingForWifi: Boolean = false,
    val offline: Boolean = false,
    val canPause: Boolean = false,
    val canResume: Boolean = false,
) {
    val isEmpty: Boolean get() = active.isEmpty() && groups.isEmpty()
}

class DownloadsViewModel(
    private val manager: DownloadManager,
    private val urls: ServerUrls,
) : ViewModel() {
    val state: StateFlow<DownloadsUi> = combine(
        manager.entries,
        manager.waitingForWifi,
        manager.offline,
    ) { entries, wifi, offline ->
        val active = entries.filter { !it.isCompleted }.sortedBy { it.addedAt }
        val groups = entries.filter { it.isCompleted }
            .groupBy { it.seriesId }
            .map { (seriesId, items) ->
                val sorted = items.sortedWith(compareBy({ it.volumeOrder }, { it.order }, { it.chapterId }))
                SeriesGroup(
                    seriesId = seriesId,
                    name = sorted.first().seriesName,
                    cover = cover(sorted.first().chapterId),
                    items = sorted,
                    bytes = sorted.sumOf { it.bytes },
                )
            }
            .sortedBy { it.name.lowercase() }
        DownloadsUi(
            active = active,
            groups = groups,
            totalBytes = entries.sumOf { it.bytes },
            waitingForWifi = wifi,
            offline = offline,
            canPause = active.any { it.isActive },
            canResume = active.any { it.state == DownloadState.Paused || it.state == DownloadState.Failed },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUi())

    // the cover saved with the download (works offline), else the server's
    fun cover(chapterId: Int): String? =
        manager.coverFile(chapterId)?.let { Uri.fromFile(it).toString() } ?: urls.chapterCover(chapterId)

    init {
        // Positions read offline go to the server as soon as it can be reached.
        viewModelScope.launch { runCatching { manager.syncPending() } }
    }

    fun pause(id: Int) = manager.pause(id)
    fun resume(id: Int) = manager.resume(id)
    fun remove(id: Int) = manager.remove(listOf(id))
    fun removeSeries(seriesId: Int) = manager.removeSeries(seriesId)
    fun removeAll() = manager.removeAll()
    fun pauseAll() = manager.pauseAll()
    fun resumeAll() = manager.resumeAll()
}
