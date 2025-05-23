package org.mr0btain.ticketman

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.mr0btain.ticketman.components.injection.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        initKoin()
        App()
    }
}