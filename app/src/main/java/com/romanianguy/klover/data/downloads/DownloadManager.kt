package com.romanianguy.klover.data.downloads

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.romanianguy.klover.data.model.ChapterInfoDto
import com.romanianguy.klover.data.model.ContentFormat
import com.romanianguy.klover.data.model.SeriesDto
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.repo.ReaderRepository
import com.romanianguy.klover.data.repo.TocEntry
import com.romanianguy.klover.data.repo.flattenToc
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.session.SessionState
import com.romanianguy.klover.data.stats.ReadingAccuracy
import com.romanianguy.klover.data.stats.observedWordFactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadHttpException(val code: Int) : IOException("HTTP $code")

data class NetworkStatus(val online: Boolean, val unmetered: Boolean)

class LocalResource(val file: File, val mime: String)

// runs on the app scope so downloads continue in the background (DownloadService).
// everything comes from the endpoints the readers use, so no extra server permission is needed
@OptIn(FlowPreview::class)
class DownloadManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val api: KavitaApi,
    private val readerRepo: ReaderRepository,
    private val urls: ServerUrls,
    private val sessions: SessionManager,
    private val settings: SettingsStore,
    private val files: DownloadFiles,
    private val store: DownloadStore,
    private val accuracy: ReadingAccuracy,
    private val measure: (html: String) -> Int,
) {
    private val lock = Any()
    private val all = MutableStateFlow<Map<Int, DownloadEntry>>(emptyMap())
    private val jobs = HashMap<Int, Job>()

    @Volatile
    private var serviceRunning = false

    private val maxParallel = settings.settings.map { it.downloadParallel }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 2)

    private val wifiOnly = settings.settings.map { it.downloadWifiOnly }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, true)

    private val incognito = settings.settings.map { it.incognito }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _network = MutableStateFlow(NetworkStatus(online = true, unmetered = true))
    val network: StateFlow<NetworkStatus> = _network

    val entries: StateFlow<List<DownloadEntry>> = combine(all, sessions.state) { map, session ->
        val server = (session as? SessionState.SignedIn)?.session?.serverUrl
        map.values.filter { it.server == server }.sortedByDescending { it.addedAt }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val running: StateFlow<List<DownloadEntry>> = entries
        .map { list -> list.filter { it.state == DownloadState.Running } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val waitingForWifi: StateFlow<Boolean> = combine(_network, wifiOnly) { net, only -> net.online && only && !net.unmetered }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val offline: StateFlow<Boolean> = _network.map { !it.online }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        files.root.mkdirs()
        // anything in flight when the app stopped starts again
        all.value = store.load().associateBy { it.chapterId }.mapValues { (_, e) ->
            if (e.state == DownloadState.Running) e.copy(state = DownloadState.Queued) else e
        }
        scope.launch {
            all.drop(1).debounce(400).collect { runCatching { store.save(it.values.toList()) } }
        }
        watchNetwork()
        sweepOrphans()
        scope.launch { maxParallel.drop(1).collect { pump() } }
        scope.launch {
            sessions.state.first { it is SessionState.SignedIn }
            pump()
        }
        scope.launch {
            sessions.state.collect { state ->
                if (state is SessionState.SignedOut) requeueRunning()
            }
        }
        // smart downloads follow Wi-Fi even if the general setting allows mobile data
        scope.launch {
            _network.map { it.online && it.unmetered }.distinctUntilChanged().drop(1).collect { unmetered ->
                if (unmetered) pump() else requeueRunning(onlyAuto = true)
            }
        }
        scope.launch {
            combine(_network, wifiOnly) { net, only -> net.online && (!only || net.unmetered) }
                .distinctUntilChanged()
                .collect { allowed ->
                    if (allowed) {
                        pump()
                        runCatching { syncPending() }
                    } else {
                        requeueRunning()
                    }
                }
        }
    }

    // queues requests. auto marks smart downloads: they never touch a chapter that already has an entry
    fun enqueue(requests: List<DownloadRequest>, auto: Boolean = false) {
        val server = sessions.current?.serverUrl ?: return
        val now = System.currentTimeMillis()
        all.update { map ->
            var result = map
            requests.forEachIndexed { index, request ->
                val existing = result[request.chapterId]
                val range = request.range
                when {
                    existing == null || existing.server != server -> {
                        if (existing != null) files.delete(request.chapterId)
                        result = result + (request.chapterId to request.toEntry(server, now + index, auto, incognito.value))
                    }

                    auto -> Unit

                    range == null && !existing.isWhole ->
                        result = result + (
                            request.chapterId to existing.copy(
                                ranges = emptyList(),
                                state = DownloadState.Queued,
                                error = null,
                                auto = false,
                            )
                        )

                    range != null && existing.isWhole -> Unit

                    range != null ->
                        result = result + (
                            request.chapterId to existing.copy(
                                ranges = withRange(existing.ranges, range),
                                state = if (existing.state == DownloadState.Running) existing.state else DownloadState.Queued,
                                error = null,
                                auto = false,
                            )
                            )

                    existing.state == DownloadState.Paused || existing.state == DownloadState.Failed ->
                        result = result + (request.chapterId to existing.copy(state = DownloadState.Queued, error = null, auto = false))

                    existing.auto -> result = result + (request.chapterId to existing.copy(auto = false))

                    else -> Unit // already queued, running or finished
                }
            }
            result
        }
        pump()
    }

    fun planAhead(info: ChapterInfoDto, chapterId: Int) {
        scope.launch(Dispatchers.IO) { runCatching { planAheadNow(info, chapterId) } }
    }

    private suspend fun planAheadNow(info: ChapterInfoDto, chapterId: Int) {
        val s = settings.settings.first()
        if (!s.smartDownloads || s.incognito || info.seriesId <= 0) return
        val net = _network.value
        if (!net.online || !net.unmetered) return
        if (files.root.usableSpace < SMART_MIN_FREE_BYTES) return
        val series = api.series(info.seriesId)
        val detail = api.seriesDetail(info.seriesId)
        val volume = context.getString(com.romanianguy.klover.R.string.series_volume)
        val chapter = context.getString(com.romanianguy.klover.R.string.series_chapter)
        val ordered = requestsForDetail(series, detail, volume, chapter, onlyUnread = false)
        val unread = requestsForDetail(series, detail, volume, chapter, onlyUnread = true).map { it.chapterId }.toSet()
        val ahead = chaptersAhead(ordered, unread, chapterId, s.smartAhead)
        if (ahead.isNotEmpty()) enqueue(ahead, auto = true)
    }

    suspend fun enqueueSeries(
        series: SeriesDto,
        volumeLabel: String,
        chapterLabel: String,
        onlyUnread: Boolean = false,
    ) {
        val detail = api.seriesDetail(series.id)
        enqueue(requestsForDetail(series, detail, volumeLabel, chapterLabel, onlyUnread))
    }

    fun pause(chapterId: Int) {
        synchronized(lock) {
            update(chapterId) { if (isActive) copy(state = DownloadState.Paused) else this }
            jobs[chapterId]?.cancel()
        }
    }

    fun resume(chapterId: Int) {
        update(chapterId) {
            if (state == DownloadState.Paused || state == DownloadState.Failed) copy(state = DownloadState.Queued, error = null) else this
        }
        pump()
    }

    fun pauseAll() = entries.value.filter { it.isActive }.forEach { pause(it.chapterId) }

    fun resumeAll() {
        entries.value.filter { it.state == DownloadState.Paused || it.state == DownloadState.Failed }.forEach { resume(it.chapterId) }
    }

    fun remove(chapterIds: Collection<Int>) {
        val stopping = synchronized(lock) {
            val running = chapterIds.mapNotNull { jobs[it] }
            running.forEach { it.cancel() }
            all.update { it - chapterIds.toSet() }
            running
        }
        scope.launch(Dispatchers.IO) {
            // wait for the cancelled job to stop writing, and skip chapters that were queued again
            stopping.forEach { it.join() }
            chapterIds.filter { it !in all.value }.forEach { files.delete(it) }
        }
    }

    private fun sweepOrphans() {
        scope.launch(Dispatchers.IO) {
            delay(ORPHAN_SWEEP_DELAY_MS) // not while the app is starting
            if (!store.isReadable()) return@launch
            files.root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val id = dir.name.toIntOrNull() ?: return@forEach
                if (id !in all.value) dir.deleteRecursively()
            }
        }
    }

    fun removePart(chapterId: Int, range: PageRange) {
        val entry = all.value[chapterId] ?: return
        val rest = entry.ranges.filter { it.from != range.from }
        if (entry.isWhole || rest.isEmpty()) {
            remove(listOf(chapterId))
            return
        }
        update(chapterId) { copy(ranges = rest) }
        scope.launch(Dispatchers.IO) {
            val keep = pagesOf(rest).toSet()
            for (page in range.from until range.until) if (page !in keep) files.page(chapterId, page).delete()
            update(chapterId) { copy(bytes = files.size(chapterId)) }
        }
    }

    fun removeSeries(seriesId: Int) = remove(entries.value.filter { it.seriesId == seriesId }.map { it.chapterId })

    fun removeAll() = remove(entries.value.map { it.chapterId })

    fun completed(chapterId: Int): DownloadEntry? =
        all.value[chapterId]?.takeIf { it.isCompleted && it.server == sessions.current?.serverUrl }

    fun info(chapterId: Int) = completed(chapterId)?.let { files.readInfo(chapterId) }

    fun pageDir(chapterId: Int): File? = completed(chapterId)?.let { files.dir(chapterId) }

    fun pdfFile(chapterId: Int): File? = completed(chapterId)?.let { files.pdf(chapterId) }?.takeIf(files::hasContent)

    fun bookSection(chapterId: Int, section: Int): String? =
        completed(chapterId)?.let { files.section(chapterId, section) }?.takeIf(files::hasContent)?.readText()

    fun bookToc(chapterId: Int): List<TocEntry>? =
        completed(chapterId)?.let { files.readToc(chapterId) }?.let(::flattenToc)

    fun bookResource(chapterId: Int, rawQuery: String?): LocalResource? {
        if (completed(chapterId) == null) return null
        val name = fileParam(rawQuery) ?: return null
        val ref = files.readResources(chapterId)[name] ?: return null
        val file = files.resource(chapterId, ref.name)
        return if (files.hasContent(file)) LocalResource(file, ref.mime) else null
    }

    fun coverFile(chapterId: Int): File? = files.cover(chapterId).takeIf(files::hasContent)

    fun neighbors(chapterId: Int): Pair<Int?, Int?> = neighborsOf(all.value.values, chapterId)

    fun recordProgress(chapterId: Int, pagesRead: Int, pendingSync: Boolean) {
        update(chapterId) { if (isCompleted) copy(localPagesRead = pagesRead, pendingSync = pendingSync) else this }
    }

    fun sweepRead() {
        scope.launch {
            val s = settings.settings.first()
            val doomed = LinkedHashSet<Int>()
            if (s.autoDeleteRead) {
                doomed += all.value.values.filter {
                    it.isCompleted && it.pages > 0 && it.localPagesRead >= it.pages && !it.pendingSync
                }.map { it.chapterId }
            }
            if (s.smartDownloads && s.smartCleanup) doomed += smartCleanupCandidates(all.value.values)
            if (doomed.isNotEmpty()) remove(doomed)
        }
    }

    suspend fun syncPending() {
        val pending = all.value.values.filter { it.isCompleted && it.pendingSync && it.localPagesRead >= 0 }
        for (entry in pending) {
            val info = files.readInfo(entry.chapterId) ?: continue
            val ok = runCatching { readerRepo.saveProgress(info, entry.chapterId, entry.localPagesRead) }.isSuccess
            if (ok) update(entry.chapterId) { copy(pendingSync = false) }
        }
    }

    private fun allowed(): Boolean {
        val net = _network.value
        return net.online && (!wifiOnly.value || net.unmetered)
    }

    private fun pump() {
        synchronized(lock) {
            val server = sessions.current?.serverUrl ?: return
            if (!allowed()) return
            val room = maxParallel.value - jobs.size
            if (room > 0) {
                val unmetered = _network.value.unmetered
                all.value.values
                    .filter { it.state == DownloadState.Queued && it.server == server && it.chapterId !in jobs && (!it.auto || unmetered) }
                    .sortedBy { it.addedAt }
                    .take(room)
                    .forEach { start(it.chapterId) }
            }
            if (jobs.isNotEmpty()) ensureService()
        }
    }

    private fun start(chapterId: Int) {
        update(chapterId) { copy(state = DownloadState.Running, error = null) }
        jobs[chapterId] = scope.launch(Dispatchers.IO) {
            try {
                run(chapterId)
            } finally {
                synchronized(lock) { jobs.remove(chapterId) }
                pump()
            }
        }
    }

    private fun requeueRunning(onlyAuto: Boolean = false) {
        synchronized(lock) {
            jobs.forEach { (id, job) ->
                if (onlyAuto && all.value[id]?.auto != true) return@forEach
                update(id) { if (state == DownloadState.Running) copy(state = DownloadState.Queued) else this }
                job.cancel()
            }
        }
    }

    private fun ensureService() {
        if (serviceRunning) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } // may be refused while the app is in the background; the downloads still run as long as the process lives
    }

    internal fun onServiceChanged(running: Boolean) {
        serviceRunning = running
    }

    private fun update(chapterId: Int, block: DownloadEntry.() -> DownloadEntry) {
        all.update { map -> map[chapterId]?.let { map + (chapterId to it.block()) } ?: map }
    }

    private fun watchNetwork() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        _network.value = connectivity.activeNetwork.toStatus(connectivity)
        runCatching {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _network.value = capabilities.toStatus()
                }

                override fun onLost(network: Network) {
                    _network.value = NetworkStatus(online = false, unmetered = false)
                }
            })
        }
    }

    private fun Network?.toStatus(connectivity: ConnectivityManager): NetworkStatus =
        this?.let { connectivity.getNetworkCapabilities(it)?.toStatus() } ?: NetworkStatus(online = false, unmetered = false)

    private fun NetworkCapabilities.toStatus() = NetworkStatus(
        online = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )

    private suspend fun run(chapterId: Int) {
        try {
            work(chapterId)
            update(chapterId) {
                if (state == DownloadState.Running) {
                    copy(
                        state = DownloadState.Completed,
                        doneItems = maxOf(doneItems, totalItems),
                        bytes = files.size(chapterId),
                        error = null,
                    )
                } else {
                    this
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // a cancelled call surfaces as an IOException; that is a stop, not a failure
            currentCoroutineContext().ensureActive()
            val networkGone = e is IOException && e !is DownloadHttpException && !_network.value.online
            update(chapterId) {
                when {
                    state != DownloadState.Running -> this
                    // offline: wait in the queue, the network callback restarts it
                    networkGone -> copy(state = DownloadState.Queued)
                    else -> copy(state = DownloadState.Failed, error = errorKey(e))
                }
            }
        }
    }

    private suspend fun work(chapterId: Int) {
        val entry = all.value[chapterId] ?: return
        files.dir(chapterId).mkdirs()
        update(chapterId) { copy(bytes = files.size(chapterId)) }

        val info = files.readInfo(chapterId) ?: api.chapterInfo(chapterId).also { files.writeInfo(chapterId, it) }
        update(chapterId) { copy(pages = info.pages, format = info.seriesFormat, volumeId = info.volumeId, libraryId = info.libraryId) }

        // the cover is optional
        if (!files.hasContent(files.cover(chapterId))) {
            runCatching { urls.chapterCover(chapterId)?.let { fetchTo(it, files.cover(chapterId)) } }
        }

        when (ContentFormat.from(info.seriesFormat).takeIf { it != ContentFormat.Unknown } ?: ContentFormat.from(entry.format)) {
            ContentFormat.Pdf -> downloadPdf(chapterId)
            ContentFormat.Epub -> downloadBook(chapterId, info.pages.coerceAtLeast(1))
            else -> downloadPages(chapterId, info.pages)
        }
    }

    private suspend fun learnFromBook(chapterId: Int, sizes: List<Int>) {
        files.writeSizes(chapterId, sizes)
        val words = runCatching { readerRepo.chapterWordCount(chapterId) }.getOrDefault(0L)
        val factor = observedWordFactor(sizes, sizes.size, words) ?: return
        accuracy.setWords(chapterId, words.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        accuracy.learnWordFactor(chapterId, factor)
        accuracy.flush()
    }

    // text length of each section of a downloaded book; null if it isn't on the device
    suspend fun bookSizes(chapterId: Int, sections: Int, remember: Boolean): List<Int>? = withContext(Dispatchers.Default) {
        if (completed(chapterId) == null || sections <= 0) return@withContext null
        files.readSizes(chapterId)?.takeIf { it.size == sections }?.let { return@withContext it }
        val sizes = (0 until sections).map { section ->
            val file = files.section(chapterId, section)
            if (!files.hasContent(file)) return@withContext null
            runCatching { measure(file.readText()) }.getOrDefault(0)
        }
        if (remember) files.writeSizes(chapterId, sizes)
        sizes
    }

    private fun wantedPages(chapterId: Int, pages: Int): List<Int> {
        val ranges = all.value[chapterId]?.ranges.orEmpty()
        return if (ranges.isEmpty()) (0 until pages).toList() else pagesOf(ranges).filter { it in 0 until pages }
    }

    private suspend fun downloadPages(chapterId: Int, pages: Int) {
        update(chapterId) { copy(totalBytes = 0) }
        // the wanted set can grow while this runs
        while (true) {
            val wanted = wantedPages(chapterId, pages)
            val missing = wanted.filter { !files.hasContent(files.page(chapterId, it)) }
            update(chapterId) { copy(totalItems = wanted.size, doneItems = wanted.size - missing.size) }
            if (missing.isEmpty()) break
            for (page in missing) {
                currentCoroutineContext().ensureActive()
                if (page !in wantedPages(chapterId, pages)) continue
                val target = files.page(chapterId, page)
                val url = urls.readerPage(chapterId, page) ?: throw IOException("Not signed in")
                fetchTo(url, target)
                update(chapterId) { copy(bytes = bytes + target.length(), doneItems = doneItems + 1) }
            }
        }
    }

    private suspend fun downloadPdf(chapterId: Int) {
        val target = files.pdf(chapterId)
        update(chapterId) { copy(totalItems = 1, doneItems = 0) }
        if (!files.hasContent(target)) {
            val url = urls.pdf(chapterId) ?: throw IOException("Not signed in")
            fetchTo(url, target, resume = true) { written, total ->
                update(chapterId) { copy(bytes = written, totalBytes = total) }
            }
        }
        update(chapterId) { copy(doneItems = 1, totalBytes = 0) }
    }

    private suspend fun downloadBook(chapterId: Int, sections: Int) {
        var total = sections + 1
        var done = 0
        fun progress() = update(chapterId) { copy(totalItems = total, doneItems = done, totalBytes = 0) }
        progress()

        // section sizes are known before reading starts; skipped in incognito
        val learns = !incognito.value && all.value[chapterId]?.incognito != true
        val sizes = if (learns) IntArray(sections) else null

        val wanted = LinkedHashSet<String>()
        for (section in 0 until sections) {
            currentCoroutineContext().ensureActive()
            val file = files.section(chapterId, section)
            val html = if (files.hasContent(file)) {
                file.readText()
            } else {
                readerRepo.bookPage(chapterId, section).also { file.writeText(it) }
            }
            if (sizes != null) sizes[section] = withContext(Dispatchers.Default) { runCatching { measure(html) }.getOrDefault(0) }
            val found = extractBookResourceFiles(html)
            val before = wanted.size
            wanted += found
            total += wanted.size - before
            done++
            progress()
        }

        if (sizes != null && !incognito.value) learnFromBook(chapterId, sizes.toList())

        if (!files.hasContent(files.toc(chapterId))) {
            runCatching { files.writeToc(chapterId, readerRepo.bookChapters(chapterId)) }
        }
        done++
        progress()

        val resources = files.readResources(chapterId).toMutableMap()
        val queue = ArrayDeque(wanted)
        val seen = HashSet(wanted)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val name = queue.removeFirst()
            val known = resources[name]
            if (known != null && files.hasContent(files.resource(chapterId, known.name))) {
                done++
                progress()
                continue
            }
            val url = urls.bookResource(chapterId, name) ?: throw IOException("Not signed in")
            val stored = "r%08x".format(name.hashCode())
            val target = files.resource(chapterId, stored).also { it.parentFile?.mkdirs() }
            val mime = try {
                fetchTo(url, target)
            } catch (e: DownloadHttpException) {
                // a missing picture shouldn't sink the whole book
                if (e.code == 404) {
                    done++
                    progress()
                    continue
                }
                throw e
            }
            val type = mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: mimeForBookFile(name)
            resources[name] = ResourceRef(stored, type)
            files.writeResources(chapterId, resources)
            update(chapterId) { copy(bytes = bytes + target.length()) }

            // stylesheets point at fonts and images too
            if (type.contains("css")) {
                for (extra in extractBookResourceFiles(target.readText())) {
                    if (seen.add(extra)) {
                        queue.addLast(extra)
                        total++
                    }
                }
            }
            done++
            progress()
        }
    }

    private suspend fun fetchTo(
        url: String,
        target: File,
        resume: Boolean = false,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): String? {
        val part = File(target.path + ".part")
        part.parentFile?.mkdirs()
        var offset = if (resume && part.isFile) part.length() else 0L
        if (!resume) part.delete()

        val request = Request.Builder().url(url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
        val response = client.newCall(request).await()
        return response.use {
            // 416: the partial file already has every byte.
            if (it.code == 416 && part.isFile) {
                part.renameTo(target)
                return@use it.header("Content-Type")?.substringBefore(';')?.trim()
            }
            if (!it.isSuccessful) throw DownloadHttpException(it.code)

            val append = offset > 0 && it.code == 206
            if (!append) offset = 0
            val length = it.body.contentLength()
            val total = if (length > 0) length + offset else 0L

            withContext(Dispatchers.IO) {
                it.body.byteStream().use { input ->
                    FileOutputStream(part, append).use { out ->
                        val buffer = ByteArray(BUFFER)
                        var written = offset
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            currentCoroutineContext().ensureActive()
                            out.write(buffer, 0, read)
                            written += read
                            val now = System.nanoTime()
                            if (now - lastReport > REPORT_EVERY_NANOS) {
                                lastReport = now
                                onProgress(written, total)
                            }
                        }
                        onProgress(written, total)
                    }
                }
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            it.header("Content-Type")?.substringBefore(';')?.trim()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }

    private fun errorKey(e: Exception): String = when {
        e is DownloadHttpException && (e.code == 401 || e.code == 403) -> DownloadError.Auth
        e is DownloadHttpException && e.code == 404 -> DownloadError.NotFound
        e is DownloadHttpException && e.code >= 500 -> DownloadError.Server
        e is IOException && e.message?.contains("ENOSPC") == true -> DownloadError.Storage
        e is IOException && e.message?.contains("space", ignoreCase = true) == true -> DownloadError.Storage
        e is DownloadHttpException -> DownloadError.Server
        e is IOException -> DownloadError.Network
        else -> DownloadError.Other
    }

    private fun DownloadRequest.toEntry(
        server: String,
        addedAt: Long,
        auto: Boolean = false,
        incognito: Boolean = false,
    ) = DownloadEntry(
        chapterId = chapterId,
        seriesId = seriesId,
        server = server,
        seriesName = seriesName,
        title = title,
        format = format,
        volumeId = volumeId,
        libraryId = libraryId,
        pages = pages,
        volumeOrder = volumeOrder,
        order = order,
        state = DownloadState.Queued,
        addedAt = addedAt,
        auto = auto,
        ranges = listOfNotNull(range),
        incognito = incognito,
    )

    private companion object {
        const val SMART_MIN_FREE_BYTES = 500L * 1024 * 1024
        const val ORPHAN_SWEEP_DELAY_MS = 15_000L
        const val BUFFER = 64 * 1024
        const val REPORT_EVERY_NANOS = 150_000_000L
    }
}
