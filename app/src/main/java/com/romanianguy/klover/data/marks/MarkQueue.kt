package com.romanianguy.klover.data.marks

import com.romanianguy.klover.data.model.ChapterDto
import com.romanianguy.klover.data.model.SeriesDetailDto
import com.romanianguy.klover.data.model.VolumeDto
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.io.IOException

enum class MarkKind { Series, Volume, Chapter }

@Serializable
data class PendingMark(
    val kind: MarkKind,
    val seriesId: Int,
    val id: Int = 0,
    val read: Boolean,
    val at: Long = 0,
)

enum class MarkResult {
    Done,

    Queued,

    Failed,
}

fun isQueueable(error: Throwable): Boolean = when (error) {
    is HttpException -> error.code() >= 500 || error.code() == 408 || error.code() == 429
    is IOException -> true
    else -> false
}

fun withMark(pending: List<PendingMark>, mark: PendingMark): List<PendingMark> {
    val kept = pending.filterNot { it.kind == mark.kind && it.seriesId == mark.seriesId && it.id == mark.id }
    // marking a whole series makes everything queued for it pointless
    val rest = if (mark.kind == MarkKind.Series) kept.filterNot { it.seriesId == mark.seriesId } else kept
    return (rest + mark).takeLast(MAX_PENDING)
}

private const val MAX_PENDING = 300

// series details as they'll be once the queued marks go through, so a refresh doesn't undo them on screen
fun applyPending(detail: SeriesDetailDto, seriesId: Int, pending: List<PendingMark>): SeriesDetailDto {
    val mine = pending.filter { it.seriesId == seriesId }
    if (mine.isEmpty()) return detail

    fun ChapterDto.after(volumeId: Int): ChapterDto {
        var read: Boolean? = null
        for (m in mine) when (m.kind) {
            MarkKind.Series -> read = m.read
            MarkKind.Volume -> if (m.id == volumeId) read = m.read
            MarkKind.Chapter -> if (m.id == id) read = m.read
        }
        return when (read) {
            true -> copy(pagesRead = pages)
            false -> copy(pagesRead = 0)
            null -> this
        }
    }

    fun VolumeDto.after(): VolumeDto {
        val updated = chapters.map { it.after(id) }
        return copy(chapters = updated, pagesRead = updated.sumOf { it.pagesRead })
    }
    return detail.copy(
        specials = detail.specials.map { it.after(it.volumeId) },
        chapters = detail.chapters.map { it.after(it.volumeId) },
        volumes = detail.volumes.map { it.after() },
        storylineChapters = detail.storylineChapters.map { it.after(it.volumeId) },
    )
}

// marks that survive the server being away: kept per account and retried with growing delays, in the order made.
// [enabled] off keeps nothing new but still sends what's kept
class MarkQueue(
    private val store: AccountStore<List<PendingMark>>,
    private val scope: CoroutineScope,
    private val enabled: suspend () -> Boolean,
    private val accountKey: () -> String?,
    private val send: suspend (PendingMark) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val backoff: (failures: Int) -> Long = { n -> minOf(300_000L, 20_000L shl minOf(n, 4)) },
) {
    val pending: StateFlow<List<PendingMark>> = store.data

    private val nudges = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null

    init {
        if (store.data.value.isNotEmpty()) start()
    }

    suspend fun mark(mark: PendingMark): MarkResult {
        val stamped = mark.copy(at = now())
        // something for this series is waiting, go behind it to keep the order
        val behind = store.data.value.any { it.seriesId == stamped.seriesId }
        if (!behind) {
            try {
                send(stamped)
                return MarkResult.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isQueueable(e) || !enabled()) return MarkResult.Failed
            }
        } else if (!enabled()) {
            return MarkResult.Failed
        }
        store.update { withMark(it, stamped) }
        start()
        nudge()
        return MarkResult.Queued
    }

    fun nudge() {
        if (store.data.value.isNotEmpty()) {
            start()
            nudges.trySend(Unit)
        }
    }

    private fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch { drain() }
    }

    private suspend fun drain() {
        var failures = 0
        while (true) {
            val key = accountKey() ?: return
            val first = store.data.value.firstOrNull() ?: return
            try {
                send(first)
                failures = 0
                removeIfSame(key, first)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isQueueable(e)) {
                    // still away: wait (or a nudge), then the same one again
                    withTimeoutOrNull(backoff(failures++)) { nudges.receive() }
                } else {
                    // refused (series gone, say): it will never go through, drop it
                    removeIfSame(key, first)
                }
            }
        }
    }

    private fun removeIfSame(key: String, mark: PendingMark) {
        // another account may have been switched to meanwhile
        if (accountKey() == key) store.update { list -> list - mark }
    }

    fun clear() = store.set(emptyList())
}
