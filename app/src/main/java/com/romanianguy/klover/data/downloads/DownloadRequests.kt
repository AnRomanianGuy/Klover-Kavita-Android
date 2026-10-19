package com.romanianguy.klover.data.downloads

import com.romanianguy.klover.data.chapters.numberedTitle
import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.SeriesDto

fun chapterRequest(series: SeriesDto, chapter: ChapterDto, title: String, volumeOrder: Float) = DownloadRequest(
    chapterId = chapter.id,
    seriesId = series.id,
    volumeId = chapter.volumeId,
    libraryId = series.libraryId,
    seriesName = series.name?.takeIf { it.isNotBlank() } ?: series.localizedName.orEmpty(),
    title = title,
    format = series.format,
    pages = chapter.pages,
    volumeOrder = volumeOrder,
    order = chapter.sortOrder,
)

fun requestsForDetail(
    series: SeriesDto,
    detail: SeriesDetailDto,
    volumeLabel: String,
    chapterLabel: String,
    onlyUnread: Boolean,
): List<DownloadRequest> {
    fun keep(chapter: ChapterDto) = !onlyUnread || chapter.pagesRead < chapter.pages
    val out = ArrayList<DownloadRequest>()
    for (volume in detail.volumes) {
        val base = numberedTitle(volumeLabel, volume.name)
        for (chapter in volume.chapters.filter(::keep)) {
            val title = if (volume.chapters.size > 1) "$base · ${numberedTitle(chapterLabel, chapter.number)}" else base
            out += chapterRequest(series, chapter, title, volume.minNumber)
        }
    }
    for (chapter in (detail.chapters + detail.storylineChapters).filter(::keep)) {
        out += chapterRequest(series, chapter, numberedTitle(chapterLabel, chapter.number), 0f)
    }
    for (chapter in detail.specials.filter(::keep)) {
        out += chapterRequest(
            series,
            chapter,
            chapter.titleName?.takeIf { it.isNotBlank() } ?: chapter.title.orEmpty(),
            0f,
        )
    }
    return out.distinctBy { it.chapterId }
}

// small value type on purpose: progress is rounded to 5% so a card redraws about twenty times per download
data class SeriesDownloadSummary(
    val completedPages: Int,
    val unfinished: Int,
    val paused: Boolean,
    val failed: Boolean,
    val progressStep: Int,
) {
    fun toBadge(totalPages: Int): DownloadBadge = when {
        unfinished > 0 -> DownloadBadge.Active(progressStep / 20f, paused = paused, failed = failed)
        totalPages > 0 && completedPages >= totalPages -> DownloadBadge.Done
        else -> DownloadBadge.Partial
    }
}

fun summarizeSeriesDownloads(entries: List<DownloadEntry>): SeriesDownloadSummary? {
    if (entries.isEmpty()) return null
    val unfinished = entries.filter { !it.isCompleted }
    val inFlight = unfinished.filter { it.state != DownloadState.Failed }
    return SeriesDownloadSummary(
        completedPages = entries.filter { it.isCompleted }.sumOf { it.wantedPages },
        unfinished = unfinished.size,
        paused = inFlight.isNotEmpty() && inFlight.all { it.state == DownloadState.Paused },
        failed = unfinished.any { it.state == DownloadState.Failed } && inFlight.isEmpty(),
        progressStep = Math.round(entries.sumOf { it.progress.toDouble() } / entries.size * 20).toInt(),
    )
}

fun seriesDownloadBadge(entries: List<DownloadEntry>, totalPages: Int): DownloadBadge =
    summarizeSeriesDownloads(entries)?.toBadge(totalPages) ?: DownloadBadge.None

// ordered is every chapter in reading order, unread the ids not read to the end; read ones are skipped, not counted
fun chaptersAhead(ordered: List<DownloadRequest>, unread: Set<Int>, currentId: Int, count: Int): List<DownloadRequest> {
    val index = ordered.indexOfFirst { it.chapterId == currentId }
    if (index < 0 || count <= 0) return emptyList()
    return ordered.drop(index + 1).filter { it.chapterId in unread }.take(count)
}
