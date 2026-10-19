package com.romanianguy.klover.data.downloads

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadsTest {
    private fun entry(
        id: Int,
        state: DownloadState = DownloadState.Completed,
        series: Int = 1,
        volume: Float = 0f,
        order: Float = 0f,
        done: Int = 0,
        total: Int = 0,
    ) = DownloadEntry(
        chapterId = id,
        seriesId = series,
        server = "http://s",
        seriesName = "S",
        title = "T$id",
        format = 0,
        volumeOrder = volume,
        order = order,
        state = state,
        doneItems = done,
        totalItems = total,
    )


    @Test
    fun findsEveryBookFileAMarkupTextUses() {
        val html = """
            <img src="/api/book/5/book-resources?file=OEBPS%2Fimg%2Ffig%201.png&apiKey=abc">
            <img src='//host/api/book/5/book-resources?apiKey=abc&file=cover.jpg'>
            <p style="background:url(http://h/api/book/5/book-resources?file=a.png&amp;apiKey=k)">x</p>
            <img src="/api/book/5/book-resources?file=cover.jpg&apiKey=abc">
        """.trimIndent()
        assertEquals(listOf("OEBPS/img/fig 1.png", "cover.jpg", "a.png"), extractBookResourceFiles(html))
    }

    @Test
    fun aLiteralPlusInAFileNameStaysAPlus() {
        assertEquals("a+b.png", fileParam("file=a+b.png&apiKey=k"))
        assertEquals("a b.png", fileParam("apiKey=k&file=a%20b.png"))
    }

    @Test
    fun percentDecodingHandlesUtf8AndBrokenEscapes() {
        assertEquals("café", percentDecode("caf%C3%A9"))
        assertEquals("100%", percentDecode("100%"))
        assertEquals("a%zzb", percentDecode("a%zzb"))
        // A sign is not a hex digit: these stay as typed instead of becoming a stray byte.
        assertEquals("a%+1b", percentDecode("a%+1b"))
        assertEquals("a%-1b", percentDecode("a%-1b"))
        assertEquals("日本語.png", percentDecode("%E6%97%A5%E6%9C%AC%E8%AA%9E.png"))
        assertEquals("😀", percentDecode("😀"))
    }

    @Test
    fun noFileParamMeansNoResource() {
        assertNull(fileParam(null))
        assertNull(fileParam("apiKey=k"))
        assertNull(fileParam("file="))
    }

    @Test
    fun mimeTypesFollowTheExtension() {
        assertEquals("image/png", mimeForBookFile("a/b.PNG"))
        assertEquals("text/css", mimeForBookFile("style.css"))
        assertEquals("font/woff2", mimeForBookFile("f.woff2"))
        assertEquals("application/octet-stream", mimeForBookFile("noextension"))
    }


    @Test
    fun aTileThatWasNeverRequestedHasNoBadge() {
        assertEquals(DownloadBadge.None, summarizeDownloads(listOf(null, null)))
        assertEquals(DownloadBadge.None, summarizeDownloads(emptyList()))
    }

    @Test
    fun allChaptersFinishedIsDone() {
        assertEquals(DownloadBadge.Done, summarizeDownloads(listOf(entry(1), entry(2))))
    }

    @Test
    fun someFinishedAndNothingRunningIsPartial() {
        assertEquals(DownloadBadge.Partial, summarizeDownloads(listOf(entry(1), null)))
    }

    @Test
    fun unfinishedChaptersAreActiveWithAveragedProgress() {
        val badge = summarizeDownloads(listOf(entry(1), entry(2, DownloadState.Running, done = 5, total = 10)))
        assertTrue(badge is DownloadBadge.Active)
        assertEquals(0.75f, (badge as DownloadBadge.Active).progress, 0.001f)
        assertEquals(false, badge.paused)
    }

    @Test
    fun pausedAndFailedAreReported() {
        val paused = summarizeDownloads(listOf(entry(1, DownloadState.Paused)))
        assertTrue((paused as DownloadBadge.Active).paused)
        val failed = summarizeDownloads(listOf(entry(1, DownloadState.Failed)))
        assertTrue((failed as DownloadBadge.Active).failed)
    }


    @Test
    fun progressUsesBytesWhenTheSizeIsKnownElseItems() {
        assertEquals(0.5f, entry(1, DownloadState.Running, done = 3, total = 6).progress, 0.001f)
        assertEquals(0.25f, entry(1, DownloadState.Running).copy(bytes = 25, totalBytes = 100).progress, 0.001f)
        assertEquals(0f, entry(1, DownloadState.Queued).progress, 0f)
        assertEquals(1f, entry(1).progress, 0f)
    }

    @Test
    fun neighboursFollowReadingOrderAndSkipUnfinishedOnes() {
        val entries = listOf(
            entry(10, volume = 2f, order = 1f),
            entry(11, volume = 1f, order = 2f),
            entry(12, volume = 1f, order = 1f),
            entry(13, DownloadState.Running, volume = 1f, order = 3f),
            entry(20, series = 2),
        )
        assertEquals(null to 11, neighborsOf(entries, 12))
        assertEquals(12 to 10, neighborsOf(entries, 11))
        assertEquals(11 to null, neighborsOf(entries, 10))
        assertEquals(null to null, neighborsOf(entries, 20))
        assertEquals(null to null, neighborsOf(entries, 999))
    }


    @Test
    fun theIndexSurvivesARoundTripAndATornWrite() {
        val dir = kotlin.io.path.createTempDirectory("klover").toFile()
        try {
            val store = DownloadStore(File(dir, "index.json"))
            assertEquals(emptyList<DownloadEntry>(), store.load())
            val saved = listOf(entry(1), entry(2, DownloadState.Paused, done = 2, total = 9))
            store.save(saved)
            assertEquals(saved, store.load())

            // A corrupt file must not crash the app; it just reads as empty.
            File(dir, "index.json").writeText("{not json")
            assertEquals(emptyList<DownloadEntry>(), store.load())
        } finally {
            dir.deleteRecursively()
        }
    }
}

class SeriesDownloadTest {
    private fun entry(id: Int, pages: Int, state: DownloadState = DownloadState.Completed) = DownloadEntry(
        chapterId = id, seriesId = 1, server = "s", seriesName = "S", title = "T", format = 0, pages = pages, state = state,
    )

    @Test
    fun aSeriesIsDoneOnlyWhenTheDownloadedPagesCoverIt() {
        assertEquals(DownloadBadge.None, seriesDownloadBadge(emptyList(), 60))
        assertEquals(DownloadBadge.Done, seriesDownloadBadge(listOf(entry(1, 30), entry(2, 30)), 60))
        assertEquals(DownloadBadge.Partial, seriesDownloadBadge(listOf(entry(1, 30)), 60))
        // Without a known page count a finished download can only be called partial: more might exist.
        assertEquals(DownloadBadge.Partial, seriesDownloadBadge(listOf(entry(1, 30)), 0))
    }

    @Test
    fun aSeriesWithSomethingInFlightIsActive() {
        val badge = seriesDownloadBadge(listOf(entry(1, 30), entry(2, 30, DownloadState.Running)), 60)
        assertTrue(badge is DownloadBadge.Active)
    }

    @Test
    fun requestsAreTitledLikeTheTiles() {
        val chapters = { range: IntRange -> range.map { com.romanianguy.klover.data.model.ChapterDto(id = it, number = "$it", pages = 10, pagesRead = if (it == 1) 10 else 0, sortOrder = it.toFloat()) } }
        val detail = com.romanianguy.klover.data.model.SeriesDetailDto(
            volumes = listOf(
                com.romanianguy.klover.data.model.VolumeDto(
                    id = 1,
                    name = "1",
                    minNumber = 1f,
                    chapters = chapters(1..2),
                ),
                com.romanianguy.klover.data.model.VolumeDto(
                    id = 2,
                    name = "2",
                    minNumber = 2f,
                    chapters = chapters(3..3),
                ),
            ),
            chapters = chapters(4..4),
        )
        val series = com.romanianguy.klover.data.model.SeriesDto(id = 7, name = "Clover", libraryId = 2, format = 1)

        val all = requestsForDetail(series, detail, "Volume", "Chapter", onlyUnread = false)
        assertEquals(
            listOf("Volume 1 · Chapter 1", "Volume 1 · Chapter 2", "Volume 2", "Chapter 4"),
            all.map { it.title },
        )
        assertEquals(listOf(1f, 1f, 2f, 0f), all.map { it.volumeOrder })
        assertEquals("Clover", all.first().seriesName)

        val unread = requestsForDetail(series, detail, "Volume", "Chapter", onlyUnread = true)
        assertEquals(listOf(2, 3, 4), unread.map { it.chapterId })
    }


    @Test
    fun theSizesOfABooksSectionsAreKeptBesideIt() {
        val root = kotlin.io.path.createTempDirectory("downloads").toFile()
        try {
            val files = DownloadFiles(root)
            assertNull(files.readSizes(7))
            files.dir(7).mkdirs()
            files.writeSizes(7, listOf(0, 3600, 4100, 12))
            assertEquals(listOf(0, 3600, 4100, 12), files.readSizes(7))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun anUnreadableSizesFileIsNoSizesAtAll() {
        val root = kotlin.io.path.createTempDirectory("downloads").toFile()
        try {
            val files = DownloadFiles(root)
            files.dir(7).mkdirs()
            File(files.dir(7), "sizes.txt").writeText("12,abc,40")
            assertNull(files.readSizes(7))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aDownloadAskedForInIncognitoIsMarkedAndOldIndexesStillLoad() {
        assertTrue(entry(1, 10).copy(incognito = true).incognito)
        // An index written before the flag existed has no such field: the download is not incognito.
        val json = com.romanianguy.klover.data.network.KavitaJson
        val written = json.parseToJsonElement(json.encodeToString(DownloadEntry.serializer(), entry(2, 10).copy(incognito = true)))
        val old = kotlinx.serialization.json.JsonObject(written.jsonObject.filterKeys { it != "incognito" })
        assertEquals(false, json.decodeFromJsonElement(DownloadEntry.serializer(), old).incognito)
        assertEquals(true, json.decodeFromJsonElement(DownloadEntry.serializer(), written).incognito)
    }
}
