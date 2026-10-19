package com.romanianguy.klover.data.downloads

import androidx.compose.runtime.Immutable
import com.romanianguy.klover.data.model.ContentFormat
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadState { Queued, Running, Paused, Completed, Failed }

// some pages of a single-file volume (one chapter), label like "Chapter 12"
@Immutable
@Serializable
data class PageRange(val from: Int, val until: Int, val label: String = "") {
    val size: Int get() = (until - from).coerceAtLeast(0)

    operator fun contains(page: Int) = page in from until until
}

fun pagesOf(ranges: List<PageRange>): List<Int> = ranges.flatMap { it.from until it.until }.distinct().sorted()

// same start means same chapter, so it isn't added twice
fun withRange(ranges: List<PageRange>, range: PageRange): List<PageRange> =
    if (ranges.any { it.from == range.from }) ranges else (ranges + range).sortedBy { it.from }

@Immutable
@Serializable
data class DownloadEntry(
    val chapterId: Int,
    val seriesId: Int,
    // chapter ids only mean something on one server
    val server: String,
    val seriesName: String,
    val title: String,
    // raw Kavita format int
    val format: Int,
    val volumeId: Int = 0,
    val libraryId: Int = 0,
    val pages: Int = 0,
    val volumeOrder: Float = 0f,
    val order: Float = 0f,
    val state: DownloadState = DownloadState.Queued,
    val doneItems: Int = 0,
    val totalItems: Int = 0,
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
    val addedAt: Long = 0,
    // -1 means nothing recorded
    val localPagesRead: Int = -1,
    // not synced yet (was offline)
    val pendingSync: Boolean = false,
    // only these get cleaned up by smart downloads
    val auto: Boolean = false,
    // empty means the whole file
    val ranges: List<PageRange> = emptyList(),
    // asked for in incognito, nothing is learned while it downloads
    val incognito: Boolean = false,
) {
    val isWhole: Boolean get() = ranges.isEmpty()

    val wantedPages: Int get() = if (ranges.isEmpty()) pages else pagesOf(ranges).size

    val displayTitle: String
        get() = ranges.mapNotNull { it.label.takeIf(String::isNotBlank) }.takeIf { it.isNotEmpty() }
            ?.let { "$title · ${it.joinToString(", ")}" } ?: title

    val contentFormat: ContentFormat get() = ContentFormat.from(format)
    val isActive: Boolean get() = state == DownloadState.Queued || state == DownloadState.Running
    val isCompleted: Boolean get() = state == DownloadState.Completed

    val progress: Float
        get() = when {
            state == DownloadState.Completed -> 1f
            totalBytes > 0 -> (bytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalItems > 0 -> (doneItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> 0f
        }
}

@Immutable
data class DownloadRequest(
    val range: PageRange? = null,
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryId: Int,
    val seriesName: String,
    val title: String,
    val format: Int,
    val pages: Int,
    val volumeOrder: Float,
    val order: Float,
)

object DownloadError {
    const val Network = "network"
    const val Storage = "storage"
    const val Auth = "auth"
    const val NotFound = "not-found"
    const val Server = "server"
    const val Other = "other"
}

@Immutable
sealed interface DownloadBadge {
    data object None : DownloadBadge

    data object Partial : DownloadBadge

    data class Active(val progress: Float, val paused: Boolean = false, val failed: Boolean = false) : DownloadBadge

    data object Done : DownloadBadge
}

fun summarizeDownloads(entries: List<DownloadEntry?>): DownloadBadge {
    if (entries.isEmpty()) return DownloadBadge.None
    val present = entries.filterNotNull()
    if (present.isEmpty()) return DownloadBadge.None
    // a partial range is never the whole file
    if (present.size == entries.size && present.all { it.isCompleted && it.isWhole }) return DownloadBadge.Done

    val unfinished = present.filter { !it.isCompleted }
    if (unfinished.isNotEmpty()) {
        val inFlight = unfinished.filter { it.state != DownloadState.Failed }
        val failed = unfinished.any { it.state == DownloadState.Failed }
        val progress = (present.sumOf { it.progress.toDouble() } / entries.size).toFloat()
        val allPaused = inFlight.isNotEmpty() && inFlight.all { it.state == DownloadState.Paused }
        return DownloadBadge.Active(progress, paused = allPaused, failed = failed && inFlight.isEmpty())
    }
    return DownloadBadge.Partial
}

fun partBadge(entry: DownloadEntry?, from: Int, until: Int): DownloadBadge {
    if (entry == null) return DownloadBadge.None
    if (entry.isWhole) return summarizeDownloads(listOf(entry))
    val covered = (from until until).all { page -> entry.ranges.any { page in it } }
    return if (covered) summarizeDownloads(listOf(entry.copy(ranges = emptyList()))) else DownloadBadge.None
}

fun neighborsOf(entries: Collection<DownloadEntry>, chapterId: Int): Pair<Int?, Int?> {
    val current = entries.firstOrNull { it.chapterId == chapterId } ?: return null to null
    val ordered = entries
        .filter { it.isCompleted && it.seriesId == current.seriesId && it.server == current.server }
        .sortedWith(compareBy({ it.volumeOrder }, { it.order }, { it.chapterId }))
    val index = ordered.indexOfFirst { it.chapterId == chapterId }
    if (index < 0) return null to null
    return ordered.getOrNull(index - 1)?.chapterId to ordered.getOrNull(index + 1)?.chapterId
}

// read to the end and synced; the latest one per series stays
fun smartCleanupCandidates(entries: Collection<DownloadEntry>): List<Int> =
    entries
        .filter { it.auto && it.isCompleted && it.pages > 0 && it.localPagesRead >= it.pages && !it.pendingSync }
        .groupBy { it.seriesId to it.server }
        .values
        .flatMap { group ->
            group.sortedWith(compareBy({ it.volumeOrder }, { it.order }, { it.chapterId })).dropLast(1).map { it.chapterId }
        }
