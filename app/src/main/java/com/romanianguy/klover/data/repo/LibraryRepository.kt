package com.romanianguy.klover.data.repo

import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.FilterComparison
import com.romanianguy.klover.data.model.FilterStatement
import com.romanianguy.klover.data.model.GroupedSeriesDto
import com.romanianguy.klover.data.model.LibraryDto
import com.romanianguy.klover.data.model.MarkChapterRequest
import com.romanianguy.klover.data.model.MarkMultipleRequest
import com.romanianguy.klover.data.model.MarkSeriesRequest
import com.romanianguy.klover.data.model.MarkVolumeRequest
import com.romanianguy.klover.data.model.PagedResult
import com.romanianguy.klover.data.model.SearchResultGroupDto
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.model.SeriesFilter
import com.romanianguy.klover.data.model.SeriesFilterField
import com.romanianguy.klover.data.model.SeriesMetadataDto
import com.romanianguy.klover.data.model.SeriesSortField
import com.romanianguy.klover.data.model.SeriesSortOptions
import com.romanianguy.klover.data.model.TagItem
import com.romanianguy.klover.data.model.WantToReadRequest
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.Response

class LibraryRepository(private val api: KavitaApi) {
    suspend fun libraries(): List<LibraryDto> = api.libraries()

    suspend fun onDeck(): List<SeriesDto> = api.onDeck().body().orEmpty()

    suspend fun recentlyUpdated(): List<GroupedSeriesDto> = api.recentlyUpdated()

    suspend fun recentlyAdded(): List<SeriesDto> = api.recentlyAdded(SeriesFilter()).body().orEmpty()

    // libraryId of `null` means "all libraries". query filters by series name
    suspend fun series(
        libraryId: Int?,
        query: String = "",
        sort: LibrarySort = LibrarySort.Name,
        page: Int = 1,
        pageSize: Int = 30,
        // list the user's "Want to read" shelf instead of the whole library (same filters and sorting apply)
        wantToRead: Boolean = false,
        filters: LibraryFilters = LibraryFilters(),
    ): PagedResult<SeriesDto> {
        val statements = buildList {
            addAll(filters.toStatements())
            if (libraryId != null) {
                add(FilterStatement(FilterComparison.Equal, SeriesFilterField.Libraries, libraryId.toString()))
            }
            if (query.isNotBlank()) {
                add(FilterStatement(FilterComparison.Contains, SeriesFilterField.SeriesName, query.trim()))
            }
        }
        val filter = SeriesFilter(
            statements = statements,
            sortOptions = SeriesSortOptions(sort.field, sort.ascending),
        )
        val response = if (wantToRead) api.wantToRead(filter, page, pageSize) else api.allSeries(filter, page, pageSize)
        return response.toPagedResult(page)
    }

    suspend fun genres(): List<TagItem> = api.genres()

    suspend fun tags(): List<TagItem> = api.tags()

    suspend fun search(query: String): SearchResultGroupDto = api.search(query.trim())

    suspend fun isWantToRead(seriesId: Int): Boolean = api.isWantToRead(seriesId)

    // adds / removes a series from the Want to read shelf. Throws if the server refuses
    suspend fun setWantToRead(seriesId: Int, add: Boolean) {
        val body = WantToReadRequest(listOf(seriesId))
        (if (add) api.addWantToRead(body) else api.removeWantToRead(body)).requireSuccess()
    }

    suspend fun series(seriesId: Int): SeriesDto = api.series(seriesId)

    suspend fun seriesMetadata(seriesId: Int): SeriesMetadataDto = api.seriesMetadata(seriesId)

    suspend fun seriesDetail(seriesId: Int): SeriesDetailDto = api.seriesDetail(seriesId)

    // marks a whole series read or unread. Throws if the server refuses
    suspend fun markSeries(seriesId: Int, read: Boolean) {
        val body = MarkSeriesRequest(seriesId)
        (if (read) api.markSeriesRead(body) else api.markSeriesUnread(body)).requireSuccess()
    }

    suspend fun markVolume(seriesId: Int, volumeId: Int, read: Boolean) {
        val body = MarkVolumeRequest(seriesId, volumeId)
        (if (read) api.markVolumeRead(body) else api.markVolumeUnread(body)).requireSuccess()
    }

    suspend fun markChapter(seriesId: Int, chapterId: Int, read: Boolean) {
        if (read) {
            api.markChapterRead(MarkChapterRequest(seriesId, chapterId)).requireSuccess()
        } else {
            api.markMultipleUnread(MarkMultipleRequest(seriesId, chapterIds = listOf(chapterId))).requireSuccess()
        }
    }

    suspend fun continuePoint(seriesId: Int): ChapterDto = api.continuePoint(seriesId)
}

enum class LibrarySort(val field: Int, val ascending: Boolean) {
    Name(SeriesSortField.SortName, true),
    RecentlyAdded(SeriesSortField.CreatedDate, false),
    RecentlyUpdated(SeriesSortField.LastChapterAdded, false),
    ReleaseYear(SeriesSortField.ReleaseYear, false),
}

@Serializable
private data class PaginationHeader(
    val currentPage: Int = 1,
    val itemsPerPage: Int = 0,
    val totalItems: Int = 0,
    val totalPages: Int = 1,
)

private fun Response<Unit>.requireSuccess() {
    if (!isSuccessful) throw HttpException(this)
}

private fun Response<List<SeriesDto>>.toPagedResult(requestedPage: Int): PagedResult<SeriesDto> {
    val items = body().orEmpty()
    val header = headers()["Pagination"]?.let {
        runCatching { KavitaJson.decodeFromString(PaginationHeader.serializer(), it) }.getOrNull()
    }
    return PagedResult(
        items = items,
        currentPage = header?.currentPage ?: requestedPage,
        totalPages = header?.totalPages ?: requestedPage,
        totalItems = header?.totalItems ?: items.size,
    )
}
