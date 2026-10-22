package org.mr0btain.ticketman

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.mr0btain.ticketman.components.print.AndroidPrintHost
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.AppWindow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPrintHost.attach(this)

        setContent {
            // Kiosk mode hides the status and navigation bars, a swipe brings them back for a moment
            val fullscreen by AppWindow.fullscreen.collectAsState()
            LaunchedEffect(fullscreen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)

                if (fullscreen) {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }

                // Lock task mode can only start from the activity in front, so wait until it is
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                setLockTask(fullscreen)
            }

            LaunchedEffect(Unit) {
                AppWindow.pinRequests.collect { if (AppWindow.fullscreen.value) setLockTask(true) }
            }

            // Nothing tells the activity when someone confirms or undoes pinning, so look while it matters
            LaunchedEffect(fullscreen) {
                while (fullscreen) {
                    reportPinned()
                    delay(PIN_CHECK_MILLIS)
                }
            }

            App()
        }
    }

    /**
     * Pins the app for kiosk mode so it can't be swiped away, or unpins it. Unless an MDM made this app the device
     * owner, Android first asks whoever is holding it to confirm, and they can unpin it again with Back and Overview
     */
    private fun setLockTask(on: Boolean) {
        val pinned = isPinned()

        try {
            if (on && !pinned) startLockTask()
            if (!on && pinned) stopLockTask()
        } catch (e: RuntimeException) {
            AppLogger.e(LOCK_TASK_TAG, "Couldn't ${if (on) "start" else "stop"} lock task mode: ${e.message}")
        }

        reportPinned()
    }

    private fun isPinned(): Boolean =
        getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    private fun reportPinned() = AppWindow.setPinned(isPinned())

    private companion object {
        const val LOCK_TASK_TAG = "MainActivity:LockTask"
        const val PIN_CHECK_MILLIS = 2_000L
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
