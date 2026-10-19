package com.romanianguy.klover.data.network

import com.romanianguy.klover.data.model.BookChapterItem
import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.model.GroupedSeriesDto
import com.romanianguy.klover.data.model.LibraryDto
import com.romanianguy.klover.data.model.LoginRequest
import com.romanianguy.klover.data.model.MarkChapterRequest
import com.romanianguy.klover.data.model.MarkMultipleRequest
import com.romanianguy.klover.data.model.MarkSeriesRequest
import com.romanianguy.klover.data.model.MarkVolumeRequest
import com.romanianguy.klover.data.model.ProgressDto
import com.romanianguy.klover.data.model.SearchResultGroupDto
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.model.SeriesFilter
import com.romanianguy.klover.data.model.SeriesMetadataDto
import com.romanianguy.klover.data.model.ServerInfoDto
import com.romanianguy.klover.data.model.TagItem
import com.romanianguy.klover.data.model.UserDto
import com.romanianguy.klover.data.model.WantToReadRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

// paths resolve against the signed-in server (ServerInterceptor); the sign-in calls take an absolute Url
interface KavitaApi {
    @POST
    suspend fun login(@Url url: String, @Body body: LoginRequest): UserDto

    @POST
    suspend fun pluginAuthenticate(@Url url: String): UserDto

    @GET("api/Server/server-info-slim")
    suspend fun serverInfo(): ServerInfoDto

    @GET("api/Library/libraries")
    suspend fun libraries(): List<LibraryDto>

    @GET("api/Metadata/genres")
    suspend fun genres(): List<TagItem>

    @GET("api/Metadata/tags")
    suspend fun tags(): List<TagItem>

    @GET("api/Search/search")
    suspend fun search(
        @Query("queryString") query: String,
        @Query("includeChapterAndFiles") includeChapterAndFiles: Boolean = false,
    ): SearchResultGroupDto

    @POST("api/Series/on-deck")
    suspend fun onDeck(
        @Query("pageNumber") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("libraryId") libraryId: Int = 0,
    ): Response<List<SeriesDto>>

    @POST("api/Series/recently-updated-series")
    suspend fun recentlyUpdated(
        @Query("pageNumber") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): List<GroupedSeriesDto>

    @POST("api/Series/recently-added-v2")
    suspend fun recentlyAdded(
        @Body filter: SeriesFilter,
        @Query("pageNumber") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<List<SeriesDto>>

    @POST("api/Series/all-v2")
    suspend fun allSeries(
        @Body filter: SeriesFilter,
        @Query("pageNumber") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
    ): Response<List<SeriesDto>>

    @POST("api/want-to-read/v2")
    suspend fun wantToRead(
        @Body filter: SeriesFilter,
        @Query("pageNumber") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
    ): Response<List<SeriesDto>>

    @GET("api/want-to-read")
    suspend fun isWantToRead(@Query("seriesId") seriesId: Int): Boolean

    @POST("api/want-to-read/add-series")
    suspend fun addWantToRead(@Body body: WantToReadRequest): Response<Unit>

    @POST("api/want-to-read/remove-series")
    suspend fun removeWantToRead(@Body body: WantToReadRequest): Response<Unit>

    @GET("api/Series/{seriesId}")
    suspend fun series(@Path("seriesId") seriesId: Int): SeriesDto

    @GET("api/Series/metadata")
    suspend fun seriesMetadata(@Query("seriesId") seriesId: Int): SeriesMetadataDto

    @GET("api/Series/series-detail")
    suspend fun seriesDetail(@Query("seriesId") seriesId: Int): SeriesDetailDto

    @POST("api/Reader/mark-read")
    suspend fun markSeriesRead(@Body body: MarkSeriesRequest): Response<Unit>

    @POST("api/Reader/mark-unread")
    suspend fun markSeriesUnread(@Body body: MarkSeriesRequest): Response<Unit>

    @POST("api/Reader/mark-volume-read")
    suspend fun markVolumeRead(@Body body: MarkVolumeRequest): Response<Unit>

    @POST("api/Reader/mark-volume-unread")
    suspend fun markVolumeUnread(@Body body: MarkVolumeRequest): Response<Unit>

    @POST("api/Reader/mark-chapter-read")
    suspend fun markChapterRead(@Body body: MarkChapterRequest): Response<Unit>

    @POST("api/Reader/mark-multiple-unread")
    suspend fun markMultipleUnread(@Body body: MarkMultipleRequest): Response<Unit>

    @GET("api/Reader/continue-point")
    suspend fun continuePoint(@Query("seriesId") seriesId: Int): ChapterDto

    @GET("api/Series/chapter")
    suspend fun chapter(@Query("chapterId") chapterId: Int): ChapterDto

    @GET("api/Reader/chapter-info")
    suspend fun chapterInfo(
        @Query("chapterId") chapterId: Int,
        @Query("includeDimensions") includeDimensions: Boolean = true,
    ): ChapterInfoDto

    @GET("api/Reader/get-progress")
    suspend fun progress(@Query("chapterId") chapterId: Int): ProgressDto

    @POST("api/Reader/progress")
    suspend fun saveProgress(@Body progress: ProgressDto): Response<Unit>

    // one EPUB section as an HTML fragment (the server answers with plain text, not JSON)
    @Headers("Accept: text/html, text/plain, */*")
    @GET("api/Book/{chapterId}/book-page")
    suspend fun bookPage(
        @Path("chapterId") chapterId: Int,
        @Query("page") page: Int,
    ): ResponseBody

    @GET("api/Book/{chapterId}/chapters")
    suspend fun bookChapters(@Path("chapterId") chapterId: Int): List<BookChapterItem>

    @GET("api/Reader/next-chapter")
    suspend fun nextChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") currentChapterId: Int,
    ): Int

    @GET("api/Reader/prev-chapter")
    suspend fun prevChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") currentChapterId: Int,
    ): Int
}
