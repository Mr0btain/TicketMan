package org.mr0btain.ticketman

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.awaitCacheReady
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event
import org.mr0btain.ticketman.components.injection.initKoin
import org.mr0btain.ticketman.ui.AppWindow

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = initKoin().koin

    MainScope().launch {
        // KSafe loads from storage asynchronously on web, reads before this finishes return the default value
        koin.get<KSafe>().awaitCacheReady()

        ComposeViewport(document.body!!) {
            App()
        }
    }

    // Kiosk mode asks for fullscreen. Browsers refuse unless it's asked from a click or key press, so a kiosk
    // started on page load (or left by pressing Escape) asks again on the next one
    MainScope().launch {
        AppWindow.fullscreen.collect { fullscreen ->
            if (fullscreen) enterFullscreen()
            else if (document.fullscreenElement != null) document.exitFullscreen()
        }
    }

    val retry: (Event) -> Unit = { if (AppWindow.fullscreen.value) enterFullscreen() }
    document.addEventListener("pointerdown", retry)
    document.addEventListener("keydown", retry)
}

// A refusal comes back as a rejected promise, not a throw, so it's caught there or the page logs an uncaught error
private fun enterFullscreen() {
    if (document.fullscreenElement != null) return
    document.documentElement?.requestFullscreen()?.catch { null }
}
