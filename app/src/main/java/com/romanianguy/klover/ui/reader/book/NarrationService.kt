package com.romanianguy.klover.ui.reader.book

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.romanianguy.klover.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Keeps the voice going with the screen off and shows it as media: a notification, the lock screen and quick settings
// player, and headset buttons. The voice itself is in ReadAloud; this only shows it and passes the controls on.
class NarrationService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var resumeOnFocus = false
    private var noisyRegistered = false
    private var silence: AudioTrack? = null

    private val narrator get() = NarrationHub.current

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.read_aloud_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        session = MediaSession(this, "Klover read aloud").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        narrator?.resumeReading()
                    }

                    override fun onPause() {
                        narrator?.pauseReading()
                    }

                    override fun onSkipToNext() {
                        narrator?.nextSentence()
                    }

                    override fun onSkipToPrevious() {
                        narrator?.previousSentence()
                    }

                    override fun onStop() {
                        narrator?.end()
                    }
                },
                main,
            )
            // deprecated, but without it the system does not hand headset and media keys to this session
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setSessionActivity(openApp())
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> narrator?.togglePause()
            ACTION_PREVIOUS -> narrator?.previousSentence()
            ACTION_NEXT -> narrator?.nextSentence()
            ACTION_STOP -> narrator?.end()
        }
        val current = narrator
        if (current == null || !current.state.value.active) {
            // started late, or the voice ended while it was starting
            runCatching {
                startForeground(NOTIFICATION_ID, build(NarrationState()), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        // must follow startForegroundService() at once; the system can refuse (it throws), and the voice then goes on
        // only while the app is open
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                build(current.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                current.state.collect { show(it) }
            }
        }
        return START_NOT_STICKY
    }

    private fun show(state: NarrationState) {
        if (!state.active) {
            stopSelf()
            return
        }
        val playing = !state.paused
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.subtitle)
                .apply {
                    // the media player behind the controls is drawn from this picture
                    state.artwork?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
                    }
                }
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(state))
        // paused because another player took over: keep the focus listener, to carry on when it is given back
        if (playing) holdAudio() else if (!resumeOnFocus) letGoOfAudio()
    }

    // focus and a wake lock while the voice is speaking: another player takes the voice's place, and the CPU stays up with
    // the screen off
    private fun holdAudio() {
        if (focus == null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener({ change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            resumeOnFocus = false
                            narrator?.pauseReading()
                        }

                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            if (narrator?.state?.value?.paused == false) {
                                resumeOnFocus = true
                                narrator?.pauseReading()
                            }
                        }

                        AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) {
                            resumeOnFocus = false
                            narrator?.resumeReading()
                        }
                    }
                }, main)
                .build()
            getSystemService(AudioManager::class.java).requestAudioFocus(request)
            focus = request
        }
        // The speech engine plays the sound from its own app, and the system hands headset and media keys only to an app
        // that is playing something itself: a silent loop makes this one the player.
        if (silence == null) silence = silentLoop()?.also { it.play() }
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "klover:read-aloud")
                .apply { acquire(WAKE_LOCK_MS) }
        }
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            noisyRegistered = true
        }
    }

    private fun letGoOfAudio() {
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        silence?.let {
            runCatching { it.stop() }
            it.release()
        }
        silence = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisy) }
            noisyRegistered = false
        }
    }

    private fun silentLoop(): AudioTrack? = runCatching {
        val frames = SILENCE_RATE
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SILENCE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 2)
            .build()
        track.write(ByteArray(frames * 2), 0, frames * 2)
        track.setLoopPoints(0, frames, -1)
        track
    }.getOrNull()

    // headphones pulled out: the voice would come out of the speaker
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            narrator?.pauseReading()
        }
    }

    // the system limits how long a media service may run; end the reading rather than crash
    override fun onTimeout(startId: Int, fgsType: Int) {
        narrator?.end()
        stopSelf(startId)
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        letGoOfAudio()
        session.isActive = false
        session.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun openApp() = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(code: Int, name: String, label: Int, icon: Int): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(this, icon),
        getString(label),
        PendingIntent.getService(
            this,
            code,
            Intent(this, NarrationService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    ).build()

    private fun build(state: NarrationState): Notification {
        val playing = !state.paused
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_read_aloud)
            .setContentTitle(state.title.ifBlank { getString(R.string.read_aloud) })
            .setContentText(state.subtitle)
            .setLargeIcon(state.artwork)
            .setContentIntent(openApp())
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(1, ACTION_PREVIOUS, R.string.read_aloud_previous, android.R.drawable.ic_media_previous))
            .addAction(
                if (playing) {
                    action(2, ACTION_PLAY_PAUSE, R.string.read_aloud_pause, android.R.drawable.ic_media_pause)
                } else {
                    action(2, ACTION_PLAY_PAUSE, R.string.read_aloud_resume, android.R.drawable.ic_media_play)
                },
            )
            .addAction(action(3, ACTION_NEXT, R.string.read_aloud_next, android.R.drawable.ic_media_next))
            .addAction(action(4, ACTION_STOP, R.string.read_aloud_stop, R.drawable.ic_notification_stop))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "read_aloud"
        private const val NOTIFICATION_ID = 42
        private const val SILENCE_RATE = 8_000
        private const val WAKE_LOCK_MS = 4 * 60 * 60 * 1000L
        private const val ACTION_PLAY_PAUSE = "com.romanianguy.klover.action.ALOUD_PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.romanianguy.klover.action.ALOUD_PREVIOUS"
        private const val ACTION_NEXT = "com.romanianguy.klover.action.ALOUD_NEXT"
        private const val ACTION_STOP = "com.romanianguy.klover.action.ALOUD_STOP"

        // called while the app is in front (the person pressed play), which is when a media service may be started
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java)) }
        }
    }
}
