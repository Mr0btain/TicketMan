package org.mr0btain.ticketman

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.awaitCacheReady
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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

    // Kiosk mode asks for fullscreen. Browsers may refuse until the page has been clicked, which is fine
    MainScope().launch {
        AppWindow.fullscreen.collect { fullscreen ->
            try {
                if (fullscreen && document.fullscreenElement == null) document.documentElement?.requestFullscreen()
                if (!fullscreen && document.fullscreenElement != null) document.exitFullscreen()
            } catch (ex: Throwable) {
                println("Fullscreen not allowed: $ex")
            }
        }
    }
}
