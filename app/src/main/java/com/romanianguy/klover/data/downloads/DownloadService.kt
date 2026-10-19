package com.romanianguy.klover.data.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.romanianguy.klover.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

// keeps the process alive with a progress notification while downloads run; DownloadManager does the work
class DownloadService : Service() {
    private val manager: DownloadManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager.onServiceChanged(true)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.downloads_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE_ALL) manager.pauseAll()

        // Must be called promptly after startForegroundService(), before anything else can go wrong. The system caps
        // how
        // long data sync services may run in the foreground each day; once that is used up it refuses a new one by
        // throwing,
        // and an uncaught throw here would take the whole app down. The downloads then simply carry on without the
        // service's protection (and queue again if the process is stopped).
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(manager.running.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (watcher == null) {
            watcher = scope.launch {
                manager.running.collectLatest { running ->
                    if (running.isEmpty()) {
                        // A short grace period: the next queued chapter starts a moment after the last one ends.
                        delay(STOP_DELAY_MS)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(running))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    // the daily data-sync limit was hit: must stop now (running on is a crash), downloads go on only while the
    // process lives
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        manager.onServiceChanged(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(running: List<DownloadEntry>): Notification {
        val percent = if (running.isEmpty()) 0 else (running.map { it.progress }.average() * 100).toInt()
        val title = when (running.size) {
            0 -> getString(R.string.downloads_title)
            1 -> running.first().seriesName
            else -> resources.getQuantityString(R.plurals.downloads_notification_title, running.size, running.size)
        }
        val text = if (running.size == 1) running.first().title else getString(
            R.string.downloads_notification_progress,
            percent,
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseAll = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, running.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.downloads_pause_all), pauseAll)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION_ID = 41
        const val ACTION_PAUSE_ALL = "com.romanianguy.klover.action.PAUSE_ALL_DOWNLOADS"
        const val STOP_DELAY_MS = 1500L
    }
}
