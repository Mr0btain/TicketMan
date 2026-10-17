package org.mr0btain.ticketman

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.FileKit
import org.mr0btain.ticketman.components.injection.initKoin
import org.mr0btain.ticketman.ui.AppWindow

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1460.dp, 800.dp))

    // Kiosk mode fills the screen
    val fullscreen by AppWindow.fullscreen.collectAsState()
    LaunchedEffect(fullscreen) {
        windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    Window(
        state = windowState,
        onCloseRequest = ::exitApplication,
        title = "TicketMan",
    ) {
        initKoin()
        FileKit.init("TicketMan")
        App()
    }
}