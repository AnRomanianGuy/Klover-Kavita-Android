package com.romanianguy.klover.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.downloads.DownloadBadge
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.downloads.SeriesDownloadSummary
import com.romanianguy.klover.data.downloads.summarizeSeriesDownloads
import com.romanianguy.klover.data.model.SeriesDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Immutable
class DownloadControl(
    private val read: () -> DownloadBadge,
    val onToggle: () -> Unit,
) {
    constructor(badge: DownloadBadge, onToggle: () -> Unit) : this({ badge }, onToggle)

    val badge: DownloadBadge get() = read()
}

@Immutable
class SeriesDownloadInfo(val id: Int, val name: String, val libraryId: Int, val format: Int, val pages: Int)

fun SeriesDto.toDownloadInfo() = SeriesDownloadInfo(
    id,
    name?.takeIf { it.isNotBlank() } ?: localizedName.orEmpty(),
    libraryId,
    format,
    pages,
)

@Stable
class SeriesDownloads internal constructor(
    private val summaries: State<Map<Int, SeriesDownloadSummary>>,
    private val toggle: (SeriesDownloadInfo, DownloadBadge) -> Unit,
) {
    private fun badgeOf(info: SeriesDownloadInfo): DownloadBadge =
        summaries.value[info.id]?.toBadge(info.pages) ?: DownloadBadge.None

    operator fun invoke(info: SeriesDownloadInfo): DownloadControl =
        DownloadControl(read = { badgeOf(info) }, onToggle = { toggle(info, badgeOf(info)) })

    @Composable
    fun badge(info: SeriesDownloadInfo): DownloadBadge {
        val own by remember(info.id) { derivedStateOf { summaries.value[info.id] } }
        return own?.toBadge(info.pages) ?: DownloadBadge.None
    }
}

@Composable
fun rememberSeriesDownloadControls(): SeriesDownloads {
    val manager = koinInject<DownloadManager>()
    val scope = koinInject<CoroutineScope>()
    val entries = manager.entries.collectAsStateWithLifecycle()
    val askNotifications = rememberNotificationAsker()
    val volumeLabel = stringResource(R.string.series_volume)
    val chapterLabel = stringResource(R.string.series_chapter)
    val summaries = remember(entries) {
        derivedStateOf {
            entries.value.groupBy { it.seriesId }.mapNotNull { (id, list) -> summarizeSeriesDownloads(list)?.let { id to it } }.toMap()
        }
    }
    return remember(manager, scope, askNotifications, volumeLabel, chapterLabel, summaries) {
        SeriesDownloads(summaries) { info, badge ->
            when (badge) {
                DownloadBadge.Done, is DownloadBadge.Active -> manager.removeSeries(info.id)
                else -> {
                    askNotifications()
                    scope.launch {
                        runCatching {
                            val series = SeriesDto(
                                id = info.id,
                                name = info.name,
                                libraryId = info.libraryId,
                                format = info.format,
                                pages = info.pages,
                            )
                            manager.enqueueSeries(series, volumeLabel, chapterLabel)
                        }
                    }
                }
            }
        }
    }
}

// fades out as the cover is lifted and back in once it lands, instead of blinking with it
@Composable
fun BoxScope.BadgeLayer(lifted: Boolean, content: @Composable BoxScope.() -> Unit) {
    val layerAlpha by animateFloatAsState(
        targetValue = if (lifted) 0f else 1f,
        animationSpec = tween(durationMillis = if (lifted) 90 else 300, delayMillis = if (lifted) 0 else 80),
        label = "badge-layer",
    )
    Box(Modifier.matchParentSize().graphicsLayer { alpha = layerAlpha }, content = content)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.DownloadBadgeIcon(badge: DownloadBadge) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = badge,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp),
        contentKey = { it::class },
        transitionSpec = {
            // Eases in (no overshoot, no big jump): a badge should settle in, not pop.
            (scaleIn(effects, initialScale = 0.75f) + fadeIn(effects)) togetherWith
                (scaleOut(effects, targetScale = 0.75f) + fadeOut(effects))
        },
        label = "download-badge",
    ) { shown ->
        if (shown == DownloadBadge.None) {
            Box(Modifier.size(0.dp))
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (shown) {
                        DownloadBadge.Done -> Icon(
                            Icons.Filled.DownloadDone,
                            contentDescription = stringResource(R.string.downloads_badge_done),
                            modifier = Modifier.size(20.dp),
                        )

                        DownloadBadge.Partial -> Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        is DownloadBadge.Active -> when {
                            shown.failed -> Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            shown.paused -> Icon(
                                Icons.Outlined.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            else -> {
                                // Progress arrives in steps (a page, a chapter); gliding between them looks
                                // continuous.
                                val shownProgress by animateFloatAsState(
                                    targetValue = shown.progress.coerceIn(0.06f, 1f),
                                    animationSpec = tween(durationMillis = 450),
                                    label = "download-progress",
                                )
                                CircularProgressIndicator(
                                    progress = { shownProgress },
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 3.dp,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    gapSize = 0.dp,
                                )
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = stringResource(R.string.downloads_badge_active),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        DownloadBadge.None -> Unit
                    }
                }
            }
        }
    }
}

// asks for the notification permission once; downloads work without it
@Composable
fun rememberNotificationAsker(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
