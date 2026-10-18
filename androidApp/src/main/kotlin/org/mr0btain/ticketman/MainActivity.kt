package org.mr0btain.ticketman

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
import org.mr0btain.ticketman.components.print.AndroidPrintHost
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
            }

            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}