package com.romanianguy.klover.ui.about

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.BuildConfig
import com.romanianguy.klover.R
import com.romanianguy.klover.data.stats.MIN_FOR_BAND
import com.romanianguy.klover.data.stats.PaceKind
import com.romanianguy.klover.data.stats.ReadingAccuracy
import com.romanianguy.klover.data.stats.ReadingPace
import com.romanianguy.klover.data.stats.Speed
import com.romanianguy.klover.data.stats.Steadiness
import com.romanianguy.klover.data.stats.overallProfile
import com.romanianguy.klover.data.stats.profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutTab(viewModel: AboutViewModel = koinViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val unknown = stringResource(R.string.about_server_unknown)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val metrics = resources.displayMetrics
    val screen = "${(metrics.widthPixels / metrics.density).toInt()} x ${(metrics.heightPixels / metrics.density).toInt()} dp"
    val serverVersion = when {
        ui.serverVersion == null -> "..."
        ui.serverVersion!!.isBlank() -> unknown
        else -> ui.serverVersion!!
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmallEmphasized)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        InfoCard(R.string.about_section_server) {
            ui.session?.let {
                InfoRow(R.string.about_server_address, it.serverUrl)
                if (it.username.isNotBlank()) InfoRow(R.string.about_server_user, it.username)
            }
            InfoRow(R.string.about_server_version, serverVersion)
        }
        InfoCard(R.string.about_section_device) {
            InfoRow(R.string.about_device_model, "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoRow(R.string.about_device_android, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow(R.string.about_device_screen, screen)
        }
        InfoCard(R.string.about_section_data) {
            InfoRow(
                R.string.about_downloads,
                pluralStringResource(
                    R.plurals.about_downloads_value,
                    ui.downloadChapters,
                    Formatter.formatShortFileSize(context, ui.downloadBytes),
                    ui.downloadChapters,
                ),
            )
        }
        EstimateCard()
        InfoCard(R.string.about_section_credits) {
            listOf(
                R.string.about_credit_compose, R.string.about_credit_nav, R.string.about_credit_coil, R.string.about_credit_okhttp,
                R.string.about_credit_koin, R.string.about_credit_kotlinx, R.string.about_credit_color,
            ).forEach { InfoRow(it, stringResource(R.string.about_licence), stacked = true) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://www.kavitareader.com".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.about_open_kavita))
            }
            OutlinedButton(onClick = {
                val text = buildString {
                    appendLine("Klover ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL}, $screen")
                    appendLine("Kavita $serverVersion")
                    ui.session?.let { appendLine("Server ${it.serverUrl}") } // never the token or the API key
                }
                scope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("Klover", text).toClipEntry())
                    copied = true
                }
            }) {
                Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (copied) R.string.about_copied else R.string.about_copy_info))
            }
        }
        Text(
            stringResource(R.string.about_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun EstimateCard() {
    val accuracy by koinInject<ReadingAccuracy>().data.collectAsStateWithLifecycle()
    val paces by koinInject<ReadingPace>().samples.collectAsStateWithLifecycle()
    val overall = overallProfile(accuracy.outcomes)
    val checked = accuracy.outcomes.values.sumOf { it.n }
    val kinds = PaceKind.entries.filter { (accuracy.outcomes[it.key]?.n ?: 0) > 0 }
    InfoCard(R.string.about_section_estimate) {
        InfoRow(
            R.string.about_estimate_accuracy,
            overall?.let { (percent, steadiness) ->
                stringResource(R.string.about_estimate_value, percent, stringResource(steadiness.label()))
            }
                ?: stringResource(R.string.about_estimate_learning, checked, MIN_FOR_BAND),
        )
        for (kind in kinds) {
            val profile = profile(kind, accuracy.outcomes[kind.key], paces[kind.key])
            InfoRow(
                kind.label(),
                profile?.let {
                    // "Steady", or "Steady · Fast" when the reader is also faster or slower than usual for this kind
                    // of content.
                    val speed = when (it.speed) {
                        Speed.Fast -> stringResource(R.string.tag_fast)
                        Speed.Slow -> stringResource(R.string.tag_slow)
                        Speed.Average -> null
                    }
                    val steady = stringResource(it.steadiness.label())
                    stringResource(
                        R.string.about_estimate_value,
                        it.accuracyPercent,
                        if (speed == null) steady else stringResource(R.string.tag_join, steady, speed),
                    )
                } ?: stringResource(R.string.about_estimate_learning, accuracy.outcomes[kind.key]?.n ?: 0, MIN_FOR_BAND),
            )
        }
        Text(
            stringResource(R.string.about_estimate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Steadiness.label(): Int = when (this) {
    Steadiness.Steady -> R.string.tag_steady
    Steadiness.Regular -> R.string.tag_regular
    Steadiness.Varied -> R.string.tag_varied
    Steadiness.Erratic -> R.string.tag_erratic
}

private fun PaceKind.label(): Int = when (this) {
    PaceKind.Manga -> R.string.stats_kind_manga
    PaceKind.Pages -> R.string.stats_kind_comics
    PaceKind.Strips -> R.string.stats_kind_strips
    PaceKind.Pdf -> R.string.stats_kind_pdf
    PaceKind.Text -> R.string.stats_kind_books
    PaceKind.Novel -> R.string.stats_kind_novels
}

@Composable
private fun InfoCard(title: Int, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String, stacked: Boolean = false) {
    if (stacked) {
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 96.dp, max = 140.dp),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
