package com.romanianguy.klover.ui.reader.book

import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.BookFont
import com.romanianguy.klover.data.prefs.BookMargins
import com.romanianguy.klover.data.prefs.PageTurn
import com.romanianguy.klover.data.prefs.PrefKeys
import com.romanianguy.klover.data.repo.TocEntry
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.stats.LeftSmoother
import com.romanianguy.klover.data.stats.bookCharsLeft
import com.romanianguy.klover.ui.common.BookSpine
import com.romanianguy.klover.ui.common.ConnectedChoiceGroup
import com.romanianguy.klover.ui.common.ErrorState
import com.romanianguy.klover.ui.common.Loadable
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.PageSheet
import com.romanianguy.klover.ui.common.foldSettle
import com.romanianguy.klover.ui.common.loadable
import com.romanianguy.klover.ui.common.rememberBookInset
import com.romanianguy.klover.ui.common.rememberNotificationAsker
import com.romanianguy.klover.ui.reader.BottomControls
import com.romanianguy.klover.ui.reader.EndOfChapter
import com.romanianguy.klover.ui.reader.ImmersiveMode
import com.romanianguy.klover.ui.reader.LeftScope
import com.romanianguy.klover.ui.reader.PageModeSelector
import com.romanianguy.klover.ui.reader.ReadAloudLeft
import com.romanianguy.klover.ui.reader.ReaderData
import com.romanianguy.klover.ui.reader.ReaderForecast
import com.romanianguy.klover.ui.reader.ReaderInsightsLine
import com.romanianguy.klover.ui.reader.ReaderTitle
import com.romanianguy.klover.ui.reader.ReaderToolsButton
import com.romanianguy.klover.ui.reader.ReaderToolsSheet
import com.romanianguy.klover.ui.reader.ReaderViewModel
import com.romanianguy.klover.ui.reader.TabletopPanel
import com.romanianguy.klover.ui.reader.rememberReaderLayout
import com.romanianguy.klover.ui.reader.rememberTabletopMetrics
import com.romanianguy.klover.ui.reader.volumeKeyPaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.roundToInt

private const val BASE_FONT_DP = 19

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun BookReaderContent(
    ready: ReaderData,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val layout = rememberReaderLayout(prefs.pageMode, prefs.tabletopMode)
    val tabletop = rememberTabletopMetrics(layout.tabletop)
    val serverUrl = koinInject<SessionManager>().current?.serverUrl.orEmpty()
    val sections = ready.info.pages.coerceAtLeast(1)

    var section by rememberSaveable(ready.chapterId) { mutableIntStateOf(ready.startPage.coerceIn(0, sections - 1)) }
    var screen by remember { mutableIntStateOf(0) }
    var screens by remember { mutableIntStateOf(1) }
    // where the next document opens, as a fraction of its screens (1 = last, going back)
    val resume = remember { ResumePoint() }

    var html by remember { mutableStateOf<Loadable<String>>(Loadable.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(section, attempt) {
        html = Loadable.Loading
        html = loadable { viewModel.bookSection(section) }
    }
    // same 600 ms guard as the paged reader
    val pendingSection by rememberUpdatedState(section)
    DisposableEffect(viewModel) { onDispose { viewModel.saveProgress(pendingSection) } }
    LaunchedEffect(section) {
        delay(600)
        viewModel.saveProgress(section)
    }

    val controller = remember { BookPageController() }
    val readAloud = rememberReadAloud()
    // the voice runs on its own, with the screen off too, so reading is its state, not the page's
    val wantsPlay by readAloud::active
    val paused by readAloud::paused
    val sleepMinutes by readAloud::sleepMinutes // 0 off, -1 end of section
    val askNotifications = rememberNotificationAsker()
    // section text as the page has it, so a spoken position maps to the screen
    var sectionText by remember { mutableStateOf("") }
    var textReady by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.ttsSpeed) { readAloud.setSpeed(prefs.ttsSpeed) }
    fun screenFraction(index: Int) = if (screens > 1) index / screens.toFloat() else 0f
    fun startReading(fromOffset: Int? = null) {
        askNotifications()
        when {
            !textReady -> readAloud.beginWithoutText()
            fromOffset != null -> readAloud.begin(section, sectionText, fromOffset)
            else -> readAloud.begin(section, sectionText, screenFraction(screen))
        }
    }
    fun stopReading() = readAloud.end()
    LaunchedEffect(section) {
        sectionText = ""
        textReady = false
        // the voice moved on to this section by itself and is already reading it; anything else starts the section over
        if (!(readAloud.active && readAloud.section == section)) {
            readAloud.reset(resume.fraction.takeIf { it < 0.95f } ?: 0f)
        }
    }
    // new text arrived while reading: carry on (picture-only pages count as read)
    LaunchedEffect(sectionText, textReady) {
        if (textReady && wantsPlay && !paused && readAloud.section != section) {
            readAloud.play(section, sectionText, readAloud.progress)
        }
    }
    LaunchedEffect(readAloud.position, readAloud.wordEnd, wantsPlay, textReady) {
        if (wantsPlay && textReady && sectionText.isNotEmpty()) {
            val sentence = sentenceAt(sectionText, readAloud.position)
            controller.highlight(sentence.first, sentence.last + 1, readAloud.position, readAloud.wordEnd)
        } else if (textReady) {
            controller.highlight(-1, -1, -1, -1)
        }
    }
    LaunchedEffect(readAloud.failed) {
        if (readAloud.failed) {
            stopReading()
            Toast.makeText(context, R.string.read_aloud_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    val palette = prefs.bookTheme.palette()
    val bars = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val hingeDp = layout.hinge?.let { with(density) { it.width.toDp().value.toInt() } } ?: 0
    val margin = prefs.bookMargins.sidePadDp
    val style = remember(
        layout,
        prefs.bookFontScale,
        prefs.bookFont,
        prefs.bookLineSpacing,
        margin,
        prefs.bookJustify,
        prefs.tapZoneSize,
        prefs.bookParagraphs,
        prefs.bookIndent,
        prefs.bookHyphenate,
        prefs.bookStyles,
        palette,
        bars,
    ) {
        BookStyle(
            columns = if (layout.dual) 2 else 1,
            // keep text clear of the hinge
            sidePadDp = max(margin, hingeDp / 2 + margin / 2),
            topPadDp = bars.calculateTopPadding().value.toInt() + 20,
            bottomPadDp = bars.calculateBottomPadding().value.toInt() + 28,
            fontSizeDp = (BASE_FONT_DP * prefs.bookFontScale).roundToInt(),
            background = palette.background,
            text = palette.text,
            link = palette.link,
            selection = palette.selection,
            fontFamily = prefs.bookFont.composeFamily(),
            lineHeight = prefs.bookLineSpacing,
            justify = prefs.bookJustify,
            tapEdge = prefs.tapZoneSize.edge,
            paragraphGap = prefs.bookParagraphs.em,
            indent = prefs.bookIndent,
            hyphenate = prefs.bookHyphenate,
            bookStyles = prefs.bookStyles,
        )
    }
    // parsing is slow on long sections, so off the main thread
    val model by produceState<BookModel?>(null, html) {
        value = null
        value = (html as? Loadable.Ready)?.let { withContext(Dispatchers.Default) { parseBookSection(it.value) } }
    }
    val client = koinInject<OkHttpClient>()
    val images = remember(serverUrl, viewModel, client) {
        BookImages(serverUrl, client) { query -> viewModel.localBookResource(query) }
    }

    var overlayVisible by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    if (showTools) ReaderToolsSheet(viewModel, null) { showTools = false }
    var showToc by rememberSaveable { mutableStateOf(false) }

    // the player shrinks to a thin bar when idle; any touch restarts the wait
    var minimized by remember { mutableStateOf(false) }
    var playerTouches by remember { mutableIntStateOf(0) }
    LaunchedEffect(wantsPlay, overlayVisible, playerTouches, minimized) {
        when {
            !wantsPlay || overlayVisible -> minimized = false
            !minimized -> {
                delay(PLAYER_IDLE_MS)
                minimized = true
            }
        }
    }
    ImmersiveMode(
        visible = overlayVisible,
        immersive = prefs.immersive,
        keepScreenOn = prefs.keepScreenOn,
        brightness = prefs.readerBrightness,
        orientation = prefs.readerOrientation,
    )

    fun openSection(target: Int, fraction: Float) {
        resume.fraction = fraction
        section = target.coerceIn(0, sections - 1)
    }

    fun turn(delta: Int) {
        val target = screen + delta
        when {
            target in 0 until screens -> {
                controller.goTo(target)
                if (delta > 0) viewModel.notePages(1)
                // turning by hand restarts the voice at the top of the new screen
                if (wantsPlay && !paused && sectionText.isNotEmpty()) readAloud.play(section, sectionText, screenFraction(target))
            }

            delta > 0 && section < sections - 1 -> {
                viewModel.notePages(1)
                openSection(section + 1, 0f)
            }

            delta < 0 && section > 0 -> openSection(section - 1, 1f)
        }
    }

    // what the voice needs to go on by itself, and the name in the media notification (not the book's in incognito)
    val resources = LocalResources.current
    val aloudTitle = if (prefs.incognito) stringResource(R.string.read_aloud) else ready.info.seriesName.orEmpty()
    val aloudChapter = if (prefs.incognito) "" else ready.info.title?.takeIf { it.isNotBlank() } ?: ready.info.chapterTitle.orEmpty()
    SideEffect {
        readAloud.host = NarrationHost(
            sections = sections,
            autoNext = prefs.ttsAutoNext,
            title = aloudTitle,
            subtitle = { index ->
                val label = resources.getString(R.string.book_section_label, index + 1, sections)
                if (aloudChapter.isBlank()) label else "$aloudChapter  ·  $label"
            },
            loadSpeech = { index -> withContext(Dispatchers.Default) { parseBookSection(viewModel.bookSection(index)).speech } },
            onAdvanced = { index -> viewModel.saveProgress(index) },
        )
    }
    // the voice went on to another section while the page was not looking (screen off): the page catches up
    LaunchedEffect(readAloud.section) {
        val voice = readAloud.section
        if (readAloud.active && voice >= 0 && voice != section) openSection(voice, readAloud.progress)
    }
    val readAloudBar: @Composable (Modifier) -> Unit = { barModifier ->
        ReadAloudBar(
            paused = paused,
            speed = prefs.ttsSpeed,
            sleepMinutes = sleepMinutes,
            onSeek = { fraction ->
                readAloud.seekTo(sectionText, fraction)
            },
            onToggle = readAloud::togglePause,
            onPreviousSentence = readAloud::previousSentence,
            onNextSentence = readAloud::nextSentence,
            onSpeed = { viewModel.setPref(PrefKeys.TTS_SPEED, it) },
            onSleep = readAloud::setSleep,
            onClose = { stopReading() },
            progress = { readAloud.progress },
            modifier = barModifier,
        )
    }

    // rest of this section plus the later ones; unseen sections use the server's word count, offline the seen ones
    val words by viewModel.bookWords.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadBookWords() }
    val seen = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
    LaunchedEffect(model) { model?.let { seen[section] = it.speech.length } }
    // a downloaded book is known in full
    LaunchedEffect(sections) {
        viewModel.downloadedBookSizes(sections)?.forEachIndexed { index, size -> if (index !in seen) seen[index] = size }
    }
    LaunchedEffect(seen.size) { viewModel.noteBookSections(seen.values.toList(), sections) }
    val textKind = viewModel.paceKindFor(ready.format)
    val bookLeft = bookCharsLeft(controller.textLeft, section, sections, seen, words)
    val bookProgress = (section + (screen + 1f) / screens.coerceAtLeast(1)) / sections
    ReaderForecast(viewModel, textKind, bookLeft ?: controller.textLeft.toFloat(), bookProgress)

    // reading aloud: time until the voice is done
    val speaking = remember { SpeakingRate() }
    val aloudSmoother = remember { LeftSmoother() }
    val aloudClock = remember { LongArray(1) }
    LaunchedEffect(readAloud.position, wantsPlay, paused) {
        if (wantsPlay && !paused) speaking.onPosition(readAloud.position, SystemClock.elapsedRealtime()) else speaking.pause()
    }
    LaunchedEffect(prefs.ttsSpeed) {
        speaking.reset()
        aloudSmoother.reset()
    }
    LaunchedEffect(wantsPlay, section) { aloudSmoother.reset() }
    val insights: @Composable () -> Unit = {
        val aloud = if (wantsPlay && textReady && sectionText.isNotEmpty()) {
            val inSection = (sectionText.length - readAloud.position).coerceAtLeast(0)
            val toBookEnd = prefs.ttsAutoNext && bookCharsLeft(inSection, section, sections, seen, words) != null
            val chars = if (toBookEnd) bookCharsLeft(
                inSection,
                section,
                sections,
                seen,
                words,
            )!! else inSection.toFloat()
            // the speed wavers, so re-estimate only every few seconds
            val target = chars / speaking.charsPerSecond(prefs.ttsSpeed)
            val now = SystemClock.elapsedRealtime()
            val seconds = if (now - aloudClock[0] >= ALOUD_REFRESH_MS) {
                aloudClock[0] = now
                aloudSmoother.next(chars.coerceAtLeast(1f), target)
            } else {
                aloudSmoother.scaled(chars.coerceAtLeast(1f), target)
            }
            ReadAloudLeft(seconds.toLong(), if (toBookEnd) LeftScope.Book else LeftScope.Section)
        } else {
            null
        }
        ReaderInsightsLine(
            viewModel,
            textKind,
            bookLeft ?: controller.textLeft.toFloat(),
            if (bookLeft != null) LeftScope.Book else LeftScope.Section,
            progress = bookProgress,
            readAloud = aloud,
        )
    }

    val listener = remember {
        object : BookPageListener {
            override fun onReady(index: Int, total: Int) {
                track(index, total)
                sectionText = controller.speechText()
                textReady = true
            }
            override fun onPage(index: Int, total: Int) = track(index, total)
            // when and how much text was left, to learn the reading speed
            private var markSection = -1
            private var markIndex = -1
            private var markLeft = 0
            private var markAt = 0L

            private fun track(index: Int, total: Int) {
                screen = index
                screens = total
                resume.fraction = if (total > 1) index / (total - 1f) else 0f
                val now = SystemClock.elapsedRealtime()
                val left = controller.textLeft
                if (!wantsPlay && markSection == section && index == markIndex + 1 && markLeft > left && markAt > 0) {
                    viewModel.notePace(
                        viewModel.paceKindFor(ready.format),
                        (now - markAt) / 1000f,
                        (markLeft - left).toFloat(),
                    )
                }
                markSection = section
                markIndex = index
                markLeft = left
                markAt = now
            }

            override fun onSpeechScreen(index: Int) {
                if (prefs.ttsFollow && wantsPlay && !paused) controller.goTo(index)
            }

            override fun onWordLong(offset: Int) {
                if (sectionText.isEmpty()) return
                startReading(wordStartAt(sectionText, offset))
            }

            override fun onSwipe(direction: Int) = turn(direction)
            override fun onTap(zone: Int) {
                // zone: -1 left edge, 1 right edge, 0 middle. Tap zones off makes every tap a middle tap.
                val effective = if (!prefs.tapZones) 0 else if (prefs.invertTapZones) -zone else zone
                if (effective == 0) overlayVisible = !overlayVisible else turn(effective)
            }

            override fun onLink(section: Int) = openSection(section, 0f)
            override fun onExternalLink(url: String) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    val atEnd = section >= sections - 1 && screen >= screens - 1
    SideEffect { viewModel.controlsShown = overlayVisible }

    val urls = koinInject<ServerUrls>()
    Box(
        Modifier
            .fillMaxSize()
            .volumeKeyPaging(
                enabled = prefs.volumeKeys,
                refocusOn = screen to section,
                onPrevious = { turn(-1) },
                onNext = { turn(+1) },
            ),
    ) {
        // the cover for the media control, only in the open book's name (not in incognito)
        NarrationCover(
            enabled = wantsPlay && !prefs.incognito,
            url = ready.info.seriesId.takeIf { it > 0 }?.let { urls.seriesCover(it) },
            title = ready.info.seriesName.orEmpty(),
            onArt = { readAloud.artwork = it },
        )
        when (val state = html) {
            Loadable.Loading -> LoadingState()
            is Loadable.Failed -> ErrorState(stringResource(R.string.error_generic), onRetry = { attempt++ })
            is Loadable.Ready -> model?.let { parsed ->
                BookView(
                    model = parsed,
                    style = style,
                    start = BookStart(resume.fraction),
                    controller = controller,
                    listener = listener,
                    images = images,
                    modifier = (if (tabletop != null) Modifier.fillMaxWidth().height(tabletop.pageHeight) else Modifier.fillMaxSize())
                        .foldSettle(layout.hinge != null && layout.dual),
                    // held like a book, the two columns turn as pages
                    fold = if (layout.dual && tabletop == null && (layout.hinge != null || prefs.pageTurn == PageTurn.Flip)) {
                        layout.hinge?.center?.x ?: (windowWidthPx / 2f)
                    } else {
                        null
                    },
                )
            }
        }

        val bookHinge = if (layout.dual && tabletop == null) layout.hinge else null
        if (bookHinge != null) BookSpine(bookHinge)

        if (tabletop != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tabletop.panelHeight)) {
                TabletopPanel(
                    title = { ReaderTitle(ready.info) },
                    label = stringResource(R.string.book_section_label, section + 1, sections) +
                        if (screens > 1) "  ·  ${screen + 1} / $screens" else "",
                    index = section,
                    lastIndex = sections - 1,
                    rtl = false,
                    onSeek = { openSection(it, 0f) },
                    onPrevious = { turn(-1) },
                    onNext = { turn(+1) },
                    previousChapterId = ready.previousChapterId,
                    nextChapterId = ready.nextChapterId,
                    onOpenChapter = onOpenChapter,
                    onBack = onBack,
                    onSettings = { showSettings = true },
                    extra = { if (wantsPlay) readAloudBar(Modifier) },
                    insights = insights,
                    actions = {
                        ReaderToolsButton(viewModel) { showTools = true }
                        IconButton(onClick = { if (wantsPlay) stopReading() else startReading() }) {
                            Icon(Icons.Filled.Headphones, contentDescription = stringResource(R.string.read_aloud))
                        }
                        IconButton(onClick = { showToc = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = stringResource(R.string.book_contents),
                            )
                        }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = overlayVisible && tabletop == null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                title = { ReaderTitle(ready.info) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    ReaderToolsButton(viewModel) { showTools = true }
                    IconButton(onClick = { if (wantsPlay) stopReading() else startReading() }) {
                        Icon(Icons.Filled.Headphones, contentDescription = stringResource(R.string.read_aloud))
                    }
                    IconButton(onClick = { showToc = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = stringResource(R.string.book_contents),
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.reader_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                ),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val motion = MaterialTheme.motionScheme
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val dockBottom by animateDpAsState(
                if (overlayVisible) 8.dp else 16.dp + navBottom,
                motion.defaultSpatialSpec(),
                label = "dock-bottom",
            )
            AnimatedVisibility(
                visible = wantsPlay && tabletop == null,
                enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 2 },
                exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(dampingRatio = 0.9f, stiffness = 320f)) { it / 2 },
            ) {
                // as wide as the right page, not across the fold
                val inset = rememberBookInset(bookHinge)
                Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(inset))
                ReadAloudDock(
                    minimized = minimized && !overlayVisible,
                    paused = paused,
                    progress = { readAloud.progress },
                    onExpand = { minimized = false },
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = dockBottom)
                        // watches touches without consuming them
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    playerTouches++
                                }
                            }
                        },
                ) {
                    readAloudBar(Modifier)
                }
                }
            }
            AnimatedVisibility(
                visible = atEnd && tabletop == null,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(
                    motion.defaultSpatialSpec(),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(
                    motion.defaultSpatialSpec(),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                val pillGap by animateDpAsState(
                    if (overlayVisible) 8.dp else 24.dp,
                    motion.defaultSpatialSpec(),
                    label = "pill-gap",
                )
                val endPill: @Composable () -> Unit = {
                    EndOfChapter(
                        nextChapterId = ready.nextChapterId,
                        onOpenNext = onOpenChapter,
                        modifier = Modifier
                            .padding(bottom = pillGap)
                            .padding(WindowInsets.navigationBars.asPaddingValues()),
                    )
                }
                // book posture: centred on the last page, not across the fold
                val pillInset = rememberBookInset(bookHinge)
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(pillInset))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { endPill() }
                }
            }
            AnimatedVisibility(
                visible = overlayVisible && tabletop == null,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(
                    motion.defaultSpatialSpec(),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(
                    motion.defaultSpatialSpec(),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                BottomControls(
                    label = stringResource(R.string.book_section_label, section + 1, sections) +
                        if (screens > 1) "  ·  ${screen + 1} / $screens" else "",
                    currentIndex = section,
                    lastIndex = sections - 1,
                    rtl = false,
                    previousChapterId = ready.previousChapterId,
                    nextChapterId = ready.nextChapterId,
                    onSeek = { openSection(it, 0f) },
                    onOpenChapter = onOpenChapter,
                    insights = insights,
                    hinge = bookHinge,
                )
            }
        }
    }

    if (showSettings) {
        PageSheet(onDismissRequest = { showSettings = false }) {
            BookSettingsSheet(
                prefs = prefs,
                dualActive = layout.dual,
                viewModel = viewModel,
            )
        }
    }
    if (showToc) {
        PageSheet(onDismissRequest = { showToc = false }) {
            TableOfContents(
                load = viewModel::bookToc,
                currentSection = section,
                onSelect = {
                    showToc = false
                    openSection(it, 0f)
                    overlayVisible = false
                },
            )
        }
    }
}

private const val SIDE_MARGIN_DP = 28

private const val ALOUD_REFRESH_MS = 5_000L

private const val PLAYER_IDLE_MS = 4_000L

private class ResumePoint {
    var fraction: Float = 0f
}

@Composable
private fun BookSettingsSheet(
    prefs: AppSettings,
    dualActive: Boolean,
    viewModel: ReaderViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // sliders commit on release so the page is laid out once
        Text(stringResource(R.string.book_text_size), style = MaterialTheme.typography.titleSmall)
        var draft by remember(prefs.bookFontScale) { mutableFloatStateOf(prefs.bookFontScale) }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { viewModel.setBookFontScale(draft) },
            valueRange = 0.7f..2.2f,
        )
        Text(
            text = "${(draft * 100).roundToInt()} %",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(stringResource(R.string.book_line_spacing), style = MaterialTheme.typography.titleSmall)
        var spacing by remember(prefs.bookLineSpacing) { mutableFloatStateOf(prefs.bookLineSpacing) }
        Slider(
            value = spacing,
            onValueChange = { spacing = it },
            onValueChangeFinished = { viewModel.setPref(PrefKeys.BOOK_LINE_SPACING, spacing) },
            valueRange = 1.2f..2.2f,
        )

        Text(stringResource(R.string.book_font), style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup(BookFont.entries, prefs.bookFont, { viewModel.setPrefEnum(PrefKeys.BOOK_FONT, it) }) {
            Text(
                stringResource(
                    when (it) {
                        BookFont.Serif -> R.string.book_font_serif
                        BookFont.Sans -> R.string.book_font_sans
                        BookFont.Mono -> R.string.book_font_mono
                    },
                ),
                maxLines = 1,
            )
        }

        Text(stringResource(R.string.book_margins), style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup(
            BookMargins.entries,
            prefs.bookMargins,
            { viewModel.setPrefEnum(PrefKeys.BOOK_MARGINS, it) },
        ) {
            Text(
                stringResource(
                    when (it) {
                        BookMargins.Narrow -> R.string.book_margins_narrow
                        BookMargins.Normal -> R.string.book_margins_normal
                        BookMargins.Wide -> R.string.book_margins_wide
                    },
                ),
                maxLines = 1,
            )
        }

        Text(stringResource(R.string.book_theme), style = MaterialTheme.typography.titleSmall)
        BookThemePicker(prefs.bookTheme, onSelect = { viewModel.setPrefEnum(PrefKeys.BOOK_THEME, it) })

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.book_justify), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = prefs.bookJustify, onCheckedChange = { viewModel.setPref(PrefKeys.BOOK_JUSTIFY, it) })
        }

        PageModeSelector(prefs.pageMode, dualActive, viewModel::setPageMode)
    }
}

@Composable
private fun TableOfContents(
    load: suspend () -> List<TocEntry>,
    currentSection: Int,
    onSelect: (Int) -> Unit,
) {
    var entries by remember { mutableStateOf<Loadable<List<TocEntry>>>(Loadable.Loading) }
    LaunchedEffect(Unit) { entries = loadable { load() } }

    when (val state = entries) {
        Loadable.Loading -> LoadingState(Modifier.padding(48.dp))
        is Loadable.Failed -> Text(
            text = stringResource(R.string.book_no_contents),
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is Loadable.Ready -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(state.value) { _, entry ->
                val current = entry.page == currentSection
                ListItem(
                    modifier = Modifier
                        .clickable { onSelect(entry.page) }
                        .padding(start = (entry.depth * 16).dp),
                    headlineContent = { Text(entry.title.ifBlank { stringResource(R.string.book_untitled_section) }) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                    ),
                )
            }
        }
    }
}
