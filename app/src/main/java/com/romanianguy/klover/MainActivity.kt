package com.romanianguy.klover

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.romanianguy.klover.data.history.ContinueShortcut
import com.romanianguy.klover.ui.KloverRoot
import com.romanianguy.klover.ui.LaunchRequests

class MainActivity : ComponentActivity() {
    @Volatile
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch screen eases away over the first screen instead of being cut to it.
        splashScreen.setOnExitAnimationListener { splash ->
            splash.animate().alpha(0f).setDuration(SPLASH_FADE_MS).withEndAction { splash.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrame()
        handle(intent)
        setContent { KloverRoot(onReady = { ready = true }) }
    }

    // holds the launch screen until the first frame can be drawn as it will stay: one change instead of three in the
    // first second.
    // a short limit keeps it from ever waiting for good
    private fun holdFirstFrame() {
        val content = findViewById<View>(android.R.id.content)
        val started = SystemClock.elapsedRealtime()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (ready || SystemClock.elapsedRealtime() - started > FIRST_FRAME_LIMIT_MS) {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
                return false
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != ContinueShortcut.ACTION) return
        val chapter = intent.getIntExtra(ContinueShortcut.EXTRA_CHAPTER, 0)
        val account = intent.getStringExtra(ContinueShortcut.EXTRA_ACCOUNT)
        if (chapter > 0 && account != null) LaunchRequests.pending.value = LaunchRequests.Continue(chapter, account)
        // Handled once: turning the phone or coming back must not open it again.
        intent.action = null
    }

    private companion object {
        const val FIRST_FRAME_LIMIT_MS = 1_500L
        const val SPLASH_FADE_MS = 260L
    }
}
