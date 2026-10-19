package com.romanianguy.klover.ui.garden

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.garden.Badge
import com.romanianguy.klover.data.garden.BadgeWatcher
import com.romanianguy.klover.data.garden.FIVE_LEAF_GUARANTEE
import com.romanianguy.klover.data.garden.FOUR_LEAF_GUARANTEE
import com.romanianguy.klover.data.garden.Garden
import com.romanianguy.klover.data.garden.Recap
import com.romanianguy.klover.data.garden.badgeFacts
import com.romanianguy.klover.data.garden.cloverLeaves
import com.romanianguy.klover.data.garden.counted
import com.romanianguy.klover.data.garden.gardenOf
import com.romanianguy.klover.data.garden.goalMinutesLeft
import com.romanianguy.klover.data.garden.goalProgress
import com.romanianguy.klover.data.garden.pityOf
import com.romanianguy.klover.data.garden.recapOf
import com.romanianguy.klover.data.history.HistoryEntry
import com.romanianguy.klover.data.history.ReadingHistory
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.stats.ReadingStats
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.reveal
import com.romanianguy.klover.ui.navigation.LocalFloatingBarInset
import com.romanianguy.klover.ui.stats.rememberStatsSummary
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun GardenScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val history by koinInject<ReadingHistory>().entries.collectAsStateWithLifecycle()
    val stats = koinInject<ReadingStats>()
    val days by stats.days.collectAsStateWithLifecycle()
    val summary = rememberStatsSummary()
    val settings = LocalAppSettings.current
    val sorted = remember(history) { history.counted().sortedBy { it.at } }
    val leaves = remember(history) { cloverLeaves(history) }
    val garden = remember(leaves) { gardenOf(leaves) }
    val pity = remember(leaves) { pityOf(leaves) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val bestDay = remember(days) { days.values.maxOfOrNull { it.seconds } ?: 0L }
    val facts = remember(history, summary, bestDay) {
        badgeFacts(
            history,
            summary,
            bestDay,
            goalMetWeeks = if (settings.weeklyGoalMinutes > 0 && summary.weekSeconds >= settings.weeklyGoalMinutes * 60L) 1 else 0,
        )
    }
    val recap = remember(history, summary) { recapOf(history, summary, System.currentTimeMillis()) }
    // A badge once earned stays earned (a weekly goal in the weeks after, too); what the facts say now counts at
    // once.
    val kept by koinInject<BadgeWatcher>().earned.collectAsStateWithLifecycle(emptySet())

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.garden_title)) },
                subtitle = { Text(pluralStringResource(R.plurals.garden_count, garden.clovers, garden.clovers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopStart) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp + LocalFloatingBarInset.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalCard(summary.weekSeconds, settings.weeklyGoalMinutes, Modifier.reveal(0))
                RecapCard(recap, Modifier.reveal(1))
                GardenBed(
                    garden = garden,
                    leaves = leaves,
                    entries = sorted,
                    picked = picked,
                    onPick = { picked = if (picked == it) null else it },
                    modifier = Modifier.reveal(2),
                )
                PityCard(garden, pity.sinceFour, pity.sinceFive, Modifier.reveal(3))
                Text(
                    stringResource(R.string.garden_how),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp).reveal(4),
                )
                Column(Modifier.reveal(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.badges_title), style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Badge.entries.forEachIndexed { order, badge ->
                            BadgeTile(
                                badge,
                                earned = badge in kept || badge.earned(facts),
                                order = order,
                                modifier = Modifier.widthIn(min = 96.dp, max = 140.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalCard(weekSeconds: Long, goalMinutes: Int, modifier: Modifier = Modifier) {
    val progress = goalProgress(weekSeconds, goalMinutes)
    val animated by animateFloatAsState(progress, tween(700), label = "goal")
    val reached = goalMinutes > 0 && progress >= 1f
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (reached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                if (goalMinutes > 0) {
                    CircularWavyProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.size(88.dp),
                        color = if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f),
                    )
                    Text(
                        "${(animated * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.goal_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        goalMinutes <= 0 -> stringResource(R.string.goal_none)
                        reached -> stringResource(R.string.goal_reached, goalMinutes)
                        else -> stringResource(
                            R.string.goal_left,
                            goalMinutesLeft(weekSeconds, goalMinutes),
                            goalMinutes,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecapCard(recap: Recap, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.recap_title), style = MaterialTheme.typography.titleMedium)
            if (recap.minutes == 0 && recap.chapters == 0) {
                Text(
                    stringResource(R.string.recap_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(
                        R.string.recap_line, recap.minutes,
                        pluralStringResource(R.plurals.recap_chapters, recap.chapters, recap.chapters),
                        pluralStringResource(R.plurals.recap_days, recap.activeDays, recap.activeDays),
                    ),
                    style = MaterialTheme.typography.bodyLarge)
                recap.topSeries?.let { Text(stringResource(R.string.recap_top, it), style = MaterialTheme.typography.bodyMedium) }
                if (recap.bestDayMinutes > 0) {
                    Text(
                        stringResource(R.string.recap_best_day, recap.bestDayMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun leafColor(leaves: Int, index: Int, scheme: ColorScheme): Color = when (leaves) {
    5 -> Color(0xFFE0A21B)
    4 -> Color(0xFF1FA08F)
    else -> if ((index / 7) % 3 == 0) scheme.tertiary else scheme.primary
}

// newest last, only the latest SHOWN are drawn
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GardenBed(
    garden: Garden,
    leaves: List<Int>,
    entries: List<HistoryEntry>,
    picked: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendChip(3, garden.threeLeaf)
            LegendChip(4, garden.fourLeaf)
            LegendChip(5, garden.fiveLeaf)
        }
        val bed = scheme.surfaceContainerLow
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = bed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.primaryContainer.copy(alpha = 0.18f),
                            Color.Transparent,
                            scheme.tertiaryContainer.copy(alpha = 0.22f),
                        ),
                    ),
                ),
            ) {
                if (garden.clovers == 0) {
                    Text(
                        stringResource(R.string.garden_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp),
                    )
                } else {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val shown = minOf(garden.clovers, SHOWN)
                        val first = garden.clovers - shown
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (i in 0 until shown) {
                                val index = first + i
                                Clover(
                                    leaves = leaves[index],
                                    index = index,
                                    order = i,
                                    bed = bed,
                                    selected = picked == index,
                                    onClick = { onPick(index) },
                                    modifier = Modifier.size(46.dp),
                                )
                            }
                        }
                        val chosen = picked?.let { entries.getOrNull(it) }
                        androidx.compose.animation.AnimatedVisibility(visible = chosen != null) {
                            chosen?.let { entry ->
                                val date = remember(entry.at) {
                                    Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault()).toLocalDate()
                                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerHighest) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(
                                            listOf(entry.seriesName, entry.title).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            date + " · " + pluralStringResource(
                                                R.plurals.garden_leaves,
                                                leaves[picked ?: 0],
                                                leaves[picked ?: 0],
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SHOWN = 120

@Composable
private fun LegendChip(leaves: Int, count: Int) {
    val scheme = MaterialTheme.colorScheme
    val tint = leafColor(leaves, 0, scheme)
    Surface(shape = CircleShape, color = scheme.surfaceContainerHigh) {
        Row(
            Modifier.padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bed = scheme.surfaceContainerHigh
            Canvas(Modifier.size(28.dp)) { drawCloverLeaves(leaves, tint, bed) }
            Text(
                pluralStringResource(R.plurals.garden_kind, leaves, count, leaves),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PityCard(garden: Garden, sinceFour: Int, sinceFive: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.garden_luck_title), style = MaterialTheme.typography.titleMedium)
            PityRow(4, garden.untilFourGuaranteed, sinceFour / FOUR_LEAF_GUARANTEE.toFloat())
            PityRow(5, garden.untilFiveGuaranteed, sinceFive / FIVE_LEAF_GUARANTEE.toFloat())
            Text(
                stringResource(R.string.garden_tiers, FOUR_LEAF_GUARANTEE, FIVE_LEAF_GUARANTEE),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PityRow(leaves: Int, chaptersLeft: Int, fill: Float) {
    val scheme = MaterialTheme.colorScheme
    val tint = leafColor(leaves, 0, scheme)
    val animated by animateFloatAsState(fill.coerceIn(0f, 1f), tween(700), label = "pity")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val bed = scheme.surfaceContainerHigh
        Canvas(Modifier.size(34.dp)) { drawCloverLeaves(leaves, tint, bed) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (chaptersLeft <= 1) stringResource(R.string.garden_pity_next, leaves)
                else pluralStringResource(R.plurals.garden_pity, chaptersLeft, leaves, chaptersLeft),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearWavyProgressIndicator(
                progress = { animated },
                color = tint,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Clover(
    leaves: Int,
    index: Int,
    order: Int,
    bed: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalAppSettings.current.motionStyle
    val grow = remember { Animatable(if (style == MotionStyle.Reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (style != MotionStyle.Reduced) {
            delay((order % 30) * 25L)
            grow.animateTo(
                1f,
                spring(
                    dampingRatio = if (style == MotionStyle.Bouncy) 0.45f else 0.85f,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }
    val scheme = MaterialTheme.colorScheme
    val tint = leafColor(leaves, index, scheme)
    val alive = leaves >= 4 && style != MotionStyle.Reduced
    // only the rare ones animate, even an idle animation wakes the frame clock every frame
    val breathing = if (alive) {
        rememberInfiniteTransition(label = "clover-alive").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (leaves >= 5) 1800 else 2600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "clover-breathe",
        )
    } else {
        null
    }
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "clover-ring")
    Canvas(
        modifier
            .clip(CircleShape)
            .clickable(onClickLabel = null, role = Role.Button, onClick = onClick)
            .graphicsLayer {
                val breathe = breathing?.value ?: 0f
                val sway = if (alive) (breathe - 0.5f) * (if (leaves >= 5) 10f else 6f) else 0f
                val swell = if (alive && leaves >= 5) 1f + 0.06f * breathe else 1f
                scaleX = grow.value * swell
                scaleY = grow.value * swell
                rotationZ = (1f - grow.value) * -40f + sway
            },
    ) {
        if (leaves >= 5) {
            val glow = breathing?.value ?: 0f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.30f + 0.20f * glow), Color.Transparent),
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
            )
        }
        if (ring > 0f) {
            drawCircle(
                scheme.primary.copy(alpha = 0.55f * ring),
                radius = size.minDimension / 2f - 1.5.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCloverLeaves(leaves, tint, bed)
    }
}

// a clover from above: hearts around a middle, each outlined in the page colour so they read as separate leaves
private fun DrawScope.drawCloverLeaves(count: Int, color: Color, outline: Color) {
    val s = size.minDimension
    val centre = Offset(size.width / 2f, size.height * 0.46f)
    val leaf = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.55f, -0.18f, -0.78f, -0.72f, -0.40f, -0.92f)
        cubicTo(-0.20f, -1.02f, 0.00f, -0.90f, 0.00f, -0.74f)
        cubicTo(0.00f, -0.90f, 0.20f, -1.02f, 0.40f, -0.92f)
        cubicTo(0.78f, -0.72f, 0.55f, -0.18f, 0f, 0f)
        close()
    }
    // leaf angles, degrees clockwise from up
    val angles = when (count) {
        3 -> listOf(0f, 120f, 240f)
        4 -> listOf(45f, 135f, 225f, 315f)
        else -> List(5) { it * 72f }
    }
    val unit = s * when (count) { 3 -> 0.40f; 4 -> 0.37f; else -> 0.33f }
    val stem = Path().apply {
        moveTo(centre.x, centre.y)
        quadraticTo(centre.x - s * 0.02f, centre.y + s * 0.30f, centre.x + s * 0.12f, size.height * 0.96f)
    }
    drawPath(stem, color, style = Stroke(width = s * 0.06f, cap = StrokeCap.Round))
    val line = Stroke(width = s * 0.045f, join = StrokeJoin.Round)
    for (angle in angles) {
        withTransform({
            translate(centre.x, centre.y)
            rotate(angle, Offset.Zero)
            translate(0f, -s * 0.03f)
            scale(unit, unit, Offset.Zero)
        }) {
            drawPath(leaf, color)
            drawPath(leaf, outline, style = Stroke(width = 0.07f, join = StrokeJoin.Round))
            drawLine(
                outline.copy(alpha = 0.55f),
                Offset(0f, -0.10f),
                Offset(0f, -0.62f),
                strokeWidth = 0.05f,
                cap = StrokeCap.Round,
            )
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val unusedLine = line
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BadgeTile(badge: Badge, earned: Boolean, order: Int, modifier: Modifier = Modifier) {
    val style = LocalAppSettings.current.motionStyle
    val pop = remember { Animatable(if (style == MotionStyle.Reduced || !earned) 1f else 0f) }
    LaunchedEffect(earned) {
        if (earned && style != MotionStyle.Reduced) {
            delay(order * 60L)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (earned) scheme.tertiaryContainer else scheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .let { if (earned) it.then(Modifier) else it },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(if (earned) scheme.tertiary else scheme.outlineVariant.copy(alpha = 0.5f))
                }
                Icon(
                    if (earned) badgeIcon(badge) else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (earned) scheme.onTertiary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                stringResource(badgeName(badge)),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (earned) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
            )
            Text(
                stringResource(badgeHint(badge)),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (earned) scheme.onTertiaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
            )
        }
    }
}

internal fun badgeName(badge: Badge): Int = when (badge) {
    Badge.FirstChapter -> R.string.badge_first
    Badge.TenChapters -> R.string.badge_ten
    Badge.FiftyChapters -> R.string.badge_fifty
    Badge.HundredChapters -> R.string.badge_hundred
    Badge.Streak3 -> R.string.badge_streak3
    Badge.Streak7 -> R.string.badge_streak7
    Badge.Streak30 -> R.string.badge_streak30
    Badge.LongDay -> R.string.badge_long_day
    Badge.TenHours -> R.string.badge_ten_hours
    Badge.NightOwl -> R.string.badge_night_owl
    Badge.EarlyBird -> R.string.badge_early_bird
    Badge.GoalMet -> R.string.badge_goal
}

internal fun badgeHint(badge: Badge): Int = when (badge) {
    Badge.FirstChapter -> R.string.badge_first_hint
    Badge.TenChapters -> R.string.badge_ten_hint
    Badge.FiftyChapters -> R.string.badge_fifty_hint
    Badge.HundredChapters -> R.string.badge_hundred_hint
    Badge.Streak3 -> R.string.badge_streak3_hint
    Badge.Streak7 -> R.string.badge_streak7_hint
    Badge.Streak30 -> R.string.badge_streak30_hint
    Badge.LongDay -> R.string.badge_long_day_hint
    Badge.TenHours -> R.string.badge_ten_hours_hint
    Badge.NightOwl -> R.string.badge_night_owl_hint
    Badge.EarlyBird -> R.string.badge_early_bird_hint
    Badge.GoalMet -> R.string.badge_goal_hint
}
