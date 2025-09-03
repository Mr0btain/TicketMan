package org.mr0btain.ticketman

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.FileKit
import org.mr0btain.ticketman.components.injection.initKoin

fun main() = application {
    Window(
        state = rememberWindowState(size = DpSize(1460.dp, 800.dp)),
        onCloseRequest = ::exitApplication,
        title = "TicketMan",
    ) {
        initKoin()
        FileKit.init("TicketMan")
        App()
    }
}