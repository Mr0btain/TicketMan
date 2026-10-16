package org.mr0btain.ticketman

import androidx.compose.ui.window.ComposeUIViewController
import org.mr0btain.ticketman.components.injection.initKoin

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}