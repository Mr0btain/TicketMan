package org.mr0btain.ticketman

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.mr0btain.ticketman.components.injection.initKoin

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KotlinProject",
    ) {
        initKoin()
        App()
    }
}