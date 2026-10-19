package com.romanianguy.klover.data.model

import kotlinx.serialization.Serializable

// wire models: every field has a default and enums are raw ints, so slightly different server versions still
// deserialize

@Serializable
data class LoginRequest(
    val username: String = "",
    val password: String = "",
    val apiKey: String? = null,
)

@Serializable
data class UserDto(
    val id: Int = 0,
    val username: String = "",
    val email: String? = null,
    val token: String = "",
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val kavitaVersion: String? = null,
)

@Serializable
data class ServerInfoDto(
    val kavitaVersion: String? = null,
    val installId: String? = null,
)

@Serializable
data class TokenRequest(
    val token: String,
    val refreshToken: String,
)

@Serializable
data class LibraryDto(
    val id: Int = 0,
    val name: String? = null,
    val type: Int = 0,
    val coverImage: String? = null,
)

@Serializable
data class SeriesDto(
    val id: Int = 0,
    val name: String? = null,
    val originalName: String? = null,
    val localizedName: String? = null,
    val sortName: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val format: Int = 0,
    val libraryId: Int = 0,
    val libraryName: String? = null,
    val userRating: Float = 0f,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
)

@Serializable
data class GroupedSeriesDto(
    val seriesName: String = "",
    val localizedSeriesName: String? = null,
    val seriesId: Int = 0,
    val libraryId: Int = 0,
    val libraryType: Int = 0,
    val chapterId: Int = 0,
    val volumeId: Int = 0,
    val id: Int = 0,
    val format: Int = 0,
    val count: Int = 0,
)

@Serializable
data class MangaFileDto(
    val id: Int = 0,
    val filePath: String? = null,
    val pages: Int = 0,
    val bytes: Long = 0,
    val format: Int = 0,
)

@Serializable
data class ChapterDto(
    val id: Int = 0,
    val range: String? = null,
    val number: String? = null,
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val sortOrder: Float = 0f,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isSpecial: Boolean = false,
    val title: String? = null,
    val titleName: String? = null,
    val summary: String? = null,
    val volumeId: Int = 0,
    val files: List<MangaFileDto> = emptyList(),
    val wordCount: Long = 0,
)

@Serializable
data class VolumeDto(
    val id: Int = 0,
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val name: String? = null,
    val number: Int = 0,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val seriesId: Int = 0,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class SeriesDetailDto(
    val specials: List<ChapterDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val volumes: List<VolumeDto> = emptyList(),
    val storylineChapters: List<ChapterDto> = emptyList(),
    val libraryType: Int = 0,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
)

@Serializable
data class FileDimensionDto(
    val width: Int = 0,
    val height: Int = 0,
    val pageNumber: Int = 0,
    val fileName: String? = null,
    val isWide: Boolean = false,
)

@Serializable
data class ChapterInfoDto(
    val chapterNumber: String? = null,
    val volumeNumber: String? = null,
    val volumeId: Int = 0,
    val seriesName: String? = null,
    val seriesFormat: Int = 0,
    val seriesId: Int = 0,
    val libraryId: Int = 0,
    val libraryType: Int = 0,
    val chapterTitle: String? = null,
    val pages: Int = 0,
    val fileName: String? = null,
    val isSpecial: Boolean = false,
    val title: String? = null,
    val pageDimensions: List<FileDimensionDto>? = null,
    val doublePairs: Map<Int, Int>? = null,
)

@Serializable
data class ProgressDto(
    val volumeId: Int,
    val chapterId: Int,
    val pageNum: Int,
    val seriesId: Int,
    val libraryId: Int,
    val bookScrollId: String? = null,
)

@Serializable
data class TagItem(val id: Int = 0, val title: String = "")

@Serializable
data class PersonItem(val name: String = "", val id: Int = 0, val role: Int = 0)

@Serializable
data class SeriesMetadataDto(
    val summary: String = "",
    val genres: List<TagItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val writers: List<PersonItem> = emptyList(),
    val releaseYear: Int = 0,
    val publicationStatus: Int = 0,
)

@Serializable
data class BookChapterItem(
    val title: String = "",
    val part: String = "",
    val page: Int = 0,
    val children: List<BookChapterItem> = emptyList(),
)

/* ---- request bodies ---- */

@Serializable
data class MarkSeriesRequest(val seriesId: Int)

@Serializable
data class WantToReadRequest(val seriesIds: List<Int>)

@Serializable
data class MarkVolumeRequest(val seriesId: Int, val volumeId: Int)

@Serializable
data class MarkChapterRequest(val seriesId: Int, val chapterId: Int)

@Serializable
data class MarkMultipleRequest(
    val seriesId: Int,
    val volumeIds: List<Int> = emptyList(),
    val chapterIds: List<Int> = emptyList(),
)

@Serializable
data class FilterStatement(
    val comparison: Int,
    val field: Int,
    val value: String,
)

@Serializable
data class SeriesSortOptions(
    val sortField: Int,
    val isAscending: Boolean = true,
)

@Serializable
data class SeriesFilter(
    val id: Int = 0,
    val name: String? = null,
    val statements: List<FilterStatement> = emptyList(),
    val combination: Int = FilterCombination.And,
    val sortOptions: SeriesSortOptions? = null,
    val limitTo: Int = 0,
)

/* ---- enum values (raw ints on the wire) ---- */

object FilterCombination {
    const val Or = 0
    const val And = 1
}

object FilterComparison {
    const val Equal = 0
    const val GreaterThanEqual = 2
    const val Contains = 5
    const val Matches = 7
}

object SeriesFilterField {
    const val SeriesName = 1
    const val PublicationStatus = 2
    const val UserRating = 5
    const val Tags = 6
    const val Translators = 8
    const val Characters = 9
    const val Publisher = 10
    const val Editor = 11
    const val CoverArtist = 12
    const val Letterer = 13
    const val Colorist = 14
    const val Inker = 15
    const val Penciller = 16
    const val Writers = 17
    const val Genres = 18
    const val Libraries = 19
    const val ReadProgress = 20
}

@Serializable
data class SearchResultGroupDto(
    val series: List<SearchSeriesDto> = emptyList(),
    val persons: List<SearchPersonDto> = emptyList(),
    val genres: List<TagItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
)

@Serializable
data class SearchSeriesDto(
    val seriesId: Int = 0,
    val name: String? = null,
    val libraryId: Int = 0,
    val libraryName: String? = null,
)

@Serializable
data class SearchPersonDto(
    val id: Int = 0,
    val name: String = "",
    val role: Int = 0,
)

object SeriesSortField {
    const val SortName = 1
    const val CreatedDate = 2
    const val LastChapterAdded = 4
    const val ReleaseYear = 6
    const val ReadProgress = 7
}

enum class ContentFormat {
    Image, Archive, Unknown, Epub, Pdf;

    val isPaged: Boolean get() = this == Image || this == Archive || this == Unknown

    companion object {
        fun from(raw: Int): ContentFormat = entries.getOrElse(raw) { Unknown }
    }
}

enum class LibraryKind {
    Manga, Comic, Book, Image, LightNovel, ComicVine;

    companion object {
        fun from(raw: Int): LibraryKind = entries.getOrElse(raw) { Manga }
    }
}

data class PagedResult<T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
) {
    val hasMore: Boolean get() = currentPage < totalPages
}
