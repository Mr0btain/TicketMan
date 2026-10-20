package org.mr0btain.ticketman.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.data.local.UpdateInterval
import kotlin.time.Clock

/**
 * Runs [onCheck] every [interval] while this is on screen, [enabled] and the window has focus, so a screen picks up
 * what other devices changed. A window that comes back after being away longer than [interval] checks straight away.
 * Screens pass `enabled = false` while a dialog is open, so nothing reloads under someone's edit
 */
@Composable
fun CheckForUpdates(
    interval: UpdateInterval,
    enabled: Boolean = true,
    onCheck: suspend () -> Unit
) {
    val focused = LocalWindowInfo.current.isWindowFocused
    val check by rememberUpdatedState(onCheck)
    // Kept across focus changes, so coming back after a while is a check rather than a fresh wait
    var lastCheck by remember { mutableLongStateOf(now()) }

    LaunchedEffect(interval, enabled, focused) {
        if (interval == UpdateInterval.OFF || !enabled || !focused) return@LaunchedEffect

        while (true) {
            val wait = lastCheck + interval.millis - now()
            if (wait > 0) delay(wait)

            check()
            lastCheck = now()
        }
    }
}

private fun now() = Clock.System.now().toEpochMilliseconds()
