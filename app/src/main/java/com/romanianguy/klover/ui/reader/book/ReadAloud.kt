package com.romanianguy.klover.ui.reader.book

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private val HIDDEN_BLOCKS = Regex(
    "<(style|script)[^>]*>.*?</\\1>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val BLOCK_ENDS = Regex("</(p|div|h[1-6]|li|tr|blockquote)\\s*>|<br\\s*/?>", RegexOption.IGNORE_CASE)
private val TAGS = Regex("<[^>]*>")
private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

fun htmlToSpeechText(html: String): String {
    var text = html.replace(HIDDEN_BLOCKS, " ").replace(BLOCK_ENDS, "\n").replace(TAGS, "")
    text = NUMERIC_ENTITY.replace(text) { m ->
        val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else " "
    }
    text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
    return text.lines().map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

data class SpeechChunk(val text: String, val start: Int)

// cuts at sentence ends, the engine has a length limit
fun chunkForSpeech(text: String, max: Int = 360): List<SpeechChunk> {
    if (text.isBlank()) return emptyList()
    // . ! ? then a space, or a line break
    val segments = ArrayList<IntRange>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val end = when {
            c == '\n' -> i + 1
            c in ".!?…" && (i + 1 >= text.length || text[i + 1].isWhitespace()) -> i + 1
            else -> -1
        }
        if (end > 0) {
            if (end > start) segments += start until end
            start = end
            i = end
        } else {
            i++
        }
    }
    if (start < text.length) segments += start until text.length

    val chunks = ArrayList<SpeechChunk>()
    var from = -1
    var to = -1
    fun flush() {
        if (from >= 0) {
            val piece = text.substring(from, to).trim()
            if (piece.isNotEmpty()) chunks += SpeechChunk(piece, from + text.substring(from, to).indexOf(piece))
        }
        from = -1
    }
    for (segment in segments) {
        var s = segment.first
        val e = segment.last + 1
        // very long sentences get cut at spaces
        while (e - s > max) {
            val cut = text.lastIndexOf(' ', s + max).takeIf { it > s } ?: (s + max)
            if (from >= 0 && cut - from > max) flush()
            if (from < 0) from = s
            to = cut
            flush()
            s = cut
        }
        if (from >= 0 && e - from > max) flush()
        if (from < 0) from = s
        to = e
    }
    flush()
    return chunks
}

private fun isSentenceEnd(text: String, i: Int): Boolean =
    text[i] == '\n' || (text[i] in ".!?…" && (i + 1 >= text.length || text[i + 1].isWhitespace()))

fun wordStartAt(text: String, offset: Int): Int {
    var i = offset.coerceIn(0, text.length)
    while (i > 0 && !text[i - 1].isWhitespace()) i--
    return i
}

fun sentenceAt(text: String, offset: Int): IntRange {
    if (text.isEmpty()) return 0 until 0
    val at = offset.coerceIn(0, text.length - 1)
    var start = at
    while (start > 0 && !isSentenceEnd(text, start - 1)) start--
    while (start < text.length - 1 && text[start].isWhitespace() && text[start] != '\n') start++
    var end = at
    while (end < text.length && !isSentenceEnd(text, end)) end++
    end = (end + 1).coerceAtMost(text.length)
    return start until end
}

fun nextSentenceStart(text: String, offset: Int): Int {
    var i = sentenceAt(text, offset).last + 1
    while (i < text.length && text[i].isWhitespace()) i++
    return i.coerceAtMost(text.length)
}

// like a music player: restart the sentence if we're well into it
fun previousSentenceStart(text: String, offset: Int, restartAfter: Int = 40): Int {
    val current = sentenceAt(text, offset).first
    if (offset - current > restartAfter || current <= 0) return current
    var before = current - 1
    while (before > 0 && text[before].isWhitespace()) before--
    return sentenceAt(text, before).first
}

fun chunkIndexAt(chunks: List<SpeechChunk>, offset: Int): Int {
    if (chunks.isEmpty()) return 0
    val index = chunks.indexOfLast { it.start <= offset }
    return if (index < 0) 0 else index
}

@Stable
// measured chars/sec; falls back to the speed setting until there's enough data (1x is about 15)
class SpeakingRate {
    private var lastPosition = -1
    private var lastAt = 0L
    private var average = 0f
    private var heard = 0

    fun reset() {
        lastPosition = -1
        average = 0f
        heard = 0
    }

    fun pause() {
        lastPosition = -1
    }

    fun onPosition(position: Int, nowMillis: Long) {
        if (lastPosition >= 0) {
            val chars = position - lastPosition
            val ms = nowMillis - lastAt
            // jumps and long silences say nothing about speed
            if (chars in 1..400 && ms in 150..4_000) {
                val rate = chars * 1000f / ms
                average = if (heard == 0) rate else average * 0.9f + rate * 0.1f
                heard++
            }
        }
        lastPosition = position
        lastAt = nowMillis
    }

    fun charsPerSecond(speed: Float): Float {
        val expected = BASE_CHARS_PER_SECOND * speed
        val trust = (heard / 12f).coerceIn(0f, 1f)
        return expected * (1 - trust) + (if (heard > 0) average else expected) * trust
    }

    companion object {
        const val BASE_CHARS_PER_SECOND = 15f
    }
}

// what happens when a section has been read to its end
enum class AfterSection { Idle, Stop, Advance }

fun afterSection(active: Boolean, sleepAtEnd: Boolean, autoNext: Boolean, section: Int, sections: Int): AfterSection = when {
    !active -> AfterSection.Idle
    sleepAtEnd -> AfterSection.Stop
    autoNext && section >= 0 && section < sections - 1 -> AfterSection.Advance
    else -> AfterSection.Stop
}

// what the media notification shows and the controls act on
data class NarrationState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val artwork: Bitmap? = null,
)

// What the reader tells the voice about the book, so it can go on to the next section by itself with the screen off.
class NarrationHost(
    val sections: Int,
    val autoNext: Boolean,
    val title: String,
    val subtitle: (section: Int) -> String,
    // the spoken text of a section, as the page has it
    val loadSpeech: suspend (section: Int) -> String,
    // the voice moved on to a section
    val onAdvanced: (section: Int) -> Unit,
)

// The voice. It keeps going when the reader is not drawn (screen off, another app in front): it moves on from section
// to section itself, and a foreground service shows the controls. It lives and dies with the open reader.
class ReadAloud(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var speaking by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var position by mutableIntStateOf(0)
        private set

    var wordEnd by mutableIntStateOf(0)
        private set

    var failed by mutableStateOf(false)
        private set

    // reading is on (it may be paused)
    var active by mutableStateOf(false)
        private set

    var paused by mutableStateOf(false)
        private set

    // 0 off, -1 at the end of the section, else minutes
    var sleepMinutes by mutableIntStateOf(0)
        private set

    // the section whose text the voice has, -1 when it has none
    var section by mutableIntStateOf(-1)
        private set

    var host: NarrationHost? = null

    // the cover shown in the media control
    var artwork: Bitmap? = null
        set(value) {
            if (field === value) return
            field = value
            publish()
        }

    private val _state = MutableStateFlow(NarrationState())
    val state: StateFlow<NarrationState> = _state

    private var speed = 1f
    private var tts: TextToSpeech? = null
    private var ready = false
    private var waiting: (() -> Unit)? = null

    private var text = ""
    private var chunks: List<SpeechChunk> = emptyList()
    private var generation = 0
    private var offset = 0
    private var advancing: Job? = null
    private var sleeping: Job? = null

    fun setSpeed(value: Float) {
        if (speed == value) return
        speed = value
        tts?.setSpeechRate(value)
        // restart the current sentence so the new speed is heard right away
        if (speaking) playAt(section, text, offset)
    }

    // starts reading [fullText], the text of [forSection], from a word
    fun begin(forSection: Int, fullText: String, fromOffset: Int) {
        active = true
        paused = false
        publish()
        NarrationHub.current = this
        NarrationService.start(appContext)
        playAt(forSection, fullText, fromOffset)
    }

    fun begin(forSection: Int, fullText: String, fraction: Float) =
        begin(forSection, fullText, (fullText.length * fraction.coerceIn(0f, 1f)).toInt())

    // reading is on but the text is not here yet (the page is still being laid out): it starts when it arrives
    fun beginWithoutText() {
        active = true
        paused = false
        publish()
        NarrationHub.current = this
        NarrationService.start(appContext)
    }

    fun end() {
        active = false
        paused = false
        sleepMinutes = 0
        sleeping?.cancel()
        advancing?.cancel()
        stop()
        publish()
        NarrationHub.release(this)
    }

    fun pauseReading() {
        if (!active || paused) return
        paused = true
        pause()
        publish()
    }

    fun resumeReading() {
        if (!active || !paused) return
        paused = false
        publish()
        if (section >= 0) playAt(section, text, offset)
    }

    fun togglePause() = if (paused) resumeReading() else pauseReading()

    fun nextSentence() {
        val to = nextSentenceStart(text, offset)
        when {
            to >= text.length -> finishSection()
            paused -> seekTo(text, to.toFloat() / text.length)
            else -> playAt(section, text, to)
        }
    }

    fun previousSentence() {
        val to = previousSentenceStart(text, offset)
        if (paused) seekTo(text, to.toFloat() / text.length.coerceAtLeast(1)) else playAt(section, text, to)
    }

    fun setSleep(minutes: Int) {
        sleepMinutes = minutes
        sleeping?.cancel()
        if (minutes > 0) {
            sleeping = scope.launch {
                delay(minutes * 60_000L)
                sleepMinutes = 0
                end()
            }
        }
    }

    fun play(forSection: Int, fullText: String, fraction: Float) = playAt(
        forSection,
        fullText,
        (fullText.length * fraction.coerceIn(0f, 1f)).toInt(),
    )

    fun playAt(forSection: Int, fullText: String, fromOffset: Int) {
        advancing?.cancel()
        section = forSection
        text = fullText
        offset = fromOffset.coerceIn(0, fullText.length)
        // chunk from the chosen word, not from the start of its chunk
        chunks = chunkForSpeech(fullText.substring(offset)).map { it.copy(start = it.start + offset) }
        position = offset
        wordEnd = offset
        if (fullText.isNotEmpty()) progress = (offset.toFloat() / fullText.length).coerceIn(0f, 1f)
        publish()
        if (chunks.isEmpty()) {
            // nothing to say (picture page), count it as read
            speaking = false
            main.post { finishSection() }
            return
        }
        whenReady { startSpeaking() }
    }

    fun seekTo(fullText: String, fraction: Float) {
        val target = (fullText.length * fraction.coerceIn(0f, 1f)).toInt()
        if (speaking) {
            playAt(section, fullText, target)
        } else {
            text = fullText
            offset = target
            position = target
            wordEnd = target
            progress = fraction.coerceIn(0f, 1f)
        }
    }

    // a new section is being opened by hand: the voice has no text for it yet
    fun reset(fraction: Float = 0f) {
        advancing?.cancel()
        stop()
        section = -1
        progress = fraction
        offset = 0
        position = 0
        wordEnd = 0
    }

    private fun pause() {
        generation++
        // paused before the engine finished starting, drop the queued start
        waiting = null
        speaking = false
        tts?.stop()
    }

    private fun stop() {
        generation++
        waiting = null
        speaking = false
        progress = 0f
        position = 0
        wordEnd = 0
        tts?.stop()
    }

    fun shutdown() {
        advancing?.cancel()
        sleeping?.cancel()
        scope.cancel()
        active = false
        paused = false
        generation++
        waiting = null
        speaking = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        publish()
        NarrationHub.release(this)
    }

    private fun publish() {
        val info = host
        _state.value = NarrationState(
            active = active,
            paused = paused,
            title = info?.title.orEmpty(),
            subtitle = info?.subtitle?.invoke(section.coerceAtLeast(0)).orEmpty(),
            artwork = artwork,
        )
    }

    private fun finishSection() {
        val info = host
        when (afterSection(active, sleepMinutes == -1, info?.autoNext == true, section, info?.sections ?: 0)) {
            AfterSection.Idle -> Unit
            AfterSection.Stop -> {
                speaking = false
                progress = 1f
                sleepMinutes = 0
                end()
            }
            AfterSection.Advance -> advance(section + 1)
        }
    }

    // the next section's text is fetched here, not by the page, so it goes on with the screen off
    private fun advance(next: Int) {
        val info = host ?: return end()
        advancing?.cancel()
        advancing = scope.launch {
            val spoken = runCatching { info.loadSpeech(next) }.getOrNull()
            if (spoken == null) {
                end()
                return@launch
            }
            if (!active || paused) return@launch
            info.onAdvanced(next)
            playAt(next, spoken, 0)
        }
    }

    private fun whenReady(block: () -> Unit) {
        if (ready) {
            block()
            return
        }
        waiting = block
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                main.post {
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.let { engine ->
                            engine.language = Locale.getDefault()
                                .takeIf { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                                ?: Locale.US
                            engine.setSpeechRate(speed)
                            engine.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                            )
                            engine.setOnUtteranceProgressListener(listener)
                        }
                        ready = true
                        waiting?.invoke()
                        waiting = null
                    } else {
                        failed = true
                        speaking = false
                    }
                }
            }
        }
    }

    private fun startSpeaking() {
        val engine = tts ?: return
        generation++
        val mine = generation
        engine.stop()
        val first = chunkIndexAt(chunks, offset)
        speaking = true
        for (index in first until chunks.size) {
            engine.speak(chunks[index].text, TextToSpeech.QUEUE_ADD, null, "g${mine}c$index")
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (gen, index) = parse(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                // engines without word positions still move the sentence along
                val chunk = chunks.getOrNull(index) ?: return@post
                offset = chunk.start
                position = chunk.start
                wordEnd = chunk.start
                if (text.isNotEmpty()) progress = (offset.toFloat() / text.length).coerceIn(0f, 1f)
            }
        }

        override fun onDone(utteranceId: String?) {
            val (gen, index) = parse(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                if (index >= chunks.lastIndex) {
                    speaking = false
                    progress = 1f
                    finishSection()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (gen, _) = parse(utteranceId) ?: return
            main.post {
                // cut off by a seek or skip, belongs to an older generation
                if (gen == generation) speaking = false
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (gen, index) = parse(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                val chunk = chunks.getOrNull(index) ?: return@post
                offset = chunk.start + start
                position = offset
                wordEnd = (chunk.start + end).coerceAtMost(text.length)
                if (text.isNotEmpty()) progress = (offset.toFloat() / text.length).coerceIn(0f, 1f)
            }
        }
    }

    private fun parse(id: String?): Pair<Int, Int>? {
        val match = ID.matchEntire(id ?: return null) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private companion object {
        val ID = Regex("g(\\d+)c(\\d+)")
    }
}

// the voice that is reading now, for the service that shows its controls
object NarrationHub {
    @Volatile
    var current: ReadAloud? = null

    fun release(narrator: ReadAloud) {
        if (current === narrator) current = null
    }
}

@Composable
fun rememberReadAloud(): ReadAloud {
    val context = LocalContext.current
    val engine = remember { ReadAloud(context) }
    DisposableEffect(engine) { onDispose { engine.shutdown() } }
    return engine
}
