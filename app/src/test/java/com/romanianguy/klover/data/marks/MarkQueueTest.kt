package com.romanianguy.klover.data.marks

import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.VolumeDto
import com.romanianguy.klover.data.session.AccountStore
import com.romanianguy.klover.data.session.SingleFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.nio.file.Files

class MarkQueueTest {
    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody("text/plain".toMediaType())))

    private val chapterMark = PendingMark(MarkKind.Chapter, seriesId = 1, id = 11, read = true)

    @Test
    fun onlyTheServerBeingAwayIsWorthTryingAgain() {
        assertTrue(isQueueable(IOException("offline")))
        assertTrue(isQueueable(http(503)))
        assertTrue(isQueueable(http(429)))
        assertFalse(isQueueable(http(404)))
        assertFalse(isQueueable(http(401)))
        assertFalse(isQueueable(IllegalStateException()))
    }

    @Test
    fun theSameThingMarkedAgainReplacesTheEarlierMark() {
        val list = withMark(listOf(chapterMark), chapterMark.copy(read = false))
        assertEquals(listOf(chapterMark.copy(read = false)), list)
    }

    @Test
    fun aWholeSeriesMarkedMakesEarlierMarksOfItPointless() {
        val list = withMark(
            listOf(chapterMark, PendingMark(MarkKind.Chapter, 2, 21, true)),
            PendingMark(MarkKind.Series, 1, read = false),
        )
        assertEquals(
            listOf(PendingMark(MarkKind.Chapter, 2, 21, true), PendingMark(MarkKind.Series, 1, read = false)),
            list,
        )
    }

    @Test
    fun marksKeptForALaterTimeAlreadyShowOnTheSeries() {
        val detail = SeriesDetailDto(
            volumes = listOf(
                VolumeDto(
                    id = 5, pages = 20, pagesRead = 0,
                    chapters = listOf(
                        ChapterDto(id = 11, pages = 10, volumeId = 5),
                        ChapterDto(id = 12, pages = 10, volumeId = 5),
                    ),
                ),
                VolumeDto(
                    id = 6,
                    pages = 10,
                    pagesRead = 10,
                    chapters = listOf(ChapterDto(id = 21, pages = 10, pagesRead = 10, volumeId = 6)),
                ),
            ),
            chapters = listOf(ChapterDto(id = 30, pages = 8, volumeId = 0)),
        )
        val shown = applyPending(
            detail, 1,
            listOf(
                PendingMark(MarkKind.Chapter, 1, 11, read = true),
                PendingMark(MarkKind.Volume, 1, 6, read = false),
                PendingMark(MarkKind.Chapter, 2, 12, read = true), // another series: not this one's
            ),
        )
        assertEquals(10, shown.volumes[0].chapters[0].pagesRead)
        assertEquals(0, shown.volumes[0].chapters[1].pagesRead)
        assertEquals(10, shown.volumes[0].pagesRead)
        assertEquals(0, shown.volumes[1].pagesRead)
        assertEquals(0, shown.chapters[0].pagesRead)
    }

    @Test
    fun theWholeSeriesMarkedReadShowsEverythingRead() {
        val detail = SeriesDetailDto(
            volumes = listOf(VolumeDto(id = 5, pages = 10, chapters = listOf(ChapterDto(id = 11, pages = 10, volumeId = 5)))),
            specials = listOf(ChapterDto(id = 40, pages = 4)),
        )
        val shown = applyPending(detail, 1, listOf(PendingMark(MarkKind.Series, 1, read = true)))
        assertEquals(10, shown.volumes[0].pagesRead)
        assertEquals(4, shown.specials[0].pagesRead)
    }

    private class Rig(var enabled: Boolean = true, var account: String? = "a") {
        val sent = ArrayList<PendingMark>()
        var failWith: Exception? = null
        val store = AccountStore(
            SingleFile(Files.createTempFile("pending", ".json").toFile()), "pending.json",
            ListSerializer(PendingMark.serializer()), emptyList(), CoroutineScope(Job() + Dispatchers.Unconfined),
        )
        val queue = MarkQueue(
            store = store,
            scope = CoroutineScope(Job() + Dispatchers.Default),
            enabled = { enabled },
            accountKey = { account },
            send = { m -> failWith?.let { throw it }; synchronized(sent) { sent += m } },
            backoff = { 30L },
        )
    }

    @Test
    fun whenTheServerAnswersAMarkIsJustSent() = runBlocking {
        val rig = Rig()
        assertEquals(MarkResult.Done, rig.queue.mark(chapterMark))
        assertEquals(1, rig.sent.size)
        assertTrue(rig.queue.pending.value.isEmpty())
    }

    @Test
    fun whenTheServerIsAwayItIsKeptAndSentWhenItIsBack() = runBlocking {
        val rig = Rig()
        rig.failWith = IOException("offline")
        assertEquals(MarkResult.Queued, rig.queue.mark(chapterMark))
        assertEquals(1, rig.queue.pending.value.size)
        rig.failWith = null
        rig.queue.nudge()
        repeat(100) { if (rig.queue.pending.value.isNotEmpty()) delay(20) }
        assertTrue(rig.queue.pending.value.isEmpty())
        assertEquals(listOf(chapterMark.seriesId), rig.sent.map { it.seriesId })
    }

    @Test
    fun aServerThatSaysNoIsNotWaitedFor() = runBlocking {
        val rig = Rig()
        rig.failWith = http(404)
        assertEquals(MarkResult.Failed, rig.queue.mark(chapterMark))
        assertTrue(rig.queue.pending.value.isEmpty())
    }

    @Test
    fun withTheSwitchOffNothingIsKept() = runBlocking {
        val rig = Rig(enabled = false)
        rig.failWith = IOException("offline")
        assertEquals(MarkResult.Failed, rig.queue.mark(chapterMark))
        assertTrue(rig.queue.pending.value.isEmpty())
    }

    @Test
    fun marksOfOneSeriesReachTheServerInTheOrderTheyWereMade() = runBlocking {
        val rig = Rig()
        rig.failWith = IOException("offline")
        rig.queue.mark(PendingMark(MarkKind.Chapter, 1, 11, true))
        rig.failWith = null
        // the server is back, but something for this series is still waiting: this goes behind it
        rig.queue.mark(PendingMark(MarkKind.Chapter, 1, 12, true))
        repeat(100) { if (rig.queue.pending.value.isNotEmpty()) delay(20) }
        assertEquals(listOf(11, 12), rig.sent.map { it.id })
    }

    @Test
    fun everythingKeptIsSentOnceTheServerIsBack() = runBlocking {
        val rig = Rig()
        rig.failWith = IOException("offline")
        rig.queue.mark(PendingMark(MarkKind.Chapter, 1, 11, true))
        rig.queue.mark(PendingMark(MarkKind.Chapter, 1, 12, true))
        rig.failWith = null
        rig.queue.nudge()
        repeat(100) { if (rig.queue.pending.value.isNotEmpty()) delay(20) }
        assertTrue(rig.queue.pending.value.isEmpty())
        assertEquals(listOf(11, 12), rig.sent.map { it.id })
    }
}
