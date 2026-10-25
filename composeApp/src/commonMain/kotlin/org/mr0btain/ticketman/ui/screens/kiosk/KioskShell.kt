package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.AppWindow
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.TmSnackbarHost
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.glassSource
import org.mr0btain.ticketman.ui.kit.navigation.TmTopBar
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.CounterScreen
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.shellInsets
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.lock

/**
 * What a kiosk shows instead of the normal app: the counter with no navigation, and the PIN screen when locked.
 * Any touch or key press counts as activity for the idle lock
 */
@Composable
fun KioskShell(viewModel: MainViewModel) {
    val kiosk = viewModel.kiosk
    val locked by kiosk.locked.collectAsState()
    val staff by kiosk.staff.collectAsState()
    val station by viewModel.session.station.collectAsState()
    val currentUser by viewModel.session.currentUser.collectAsState()
    val pinned by AppWindow.pinned.collectAsState()
    val spacing = TmTheme.spacing

    TmBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Watch every pointer event without taking it from what's underneath
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        kiosk.recordActivity()
                    }
                }
            }
            .onPreviewKeyEvent {
                kiosk.recordActivity()
                false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(shellInsets)) {
            // Android asks to confirm pinning the first time, and someone can unpin it later with Back and Overview
            if (pinned == false) {
                NotPinnedNotice(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(start = spacing.sm, top = spacing.sm, end = spacing.sm)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (locked) {
                    LockScreen(
                        stationName = station?.name.orEmpty(),
                        staff = staff,
                        onRefresh = { kiosk.refreshStaff() },
                        onUnlock = { member, pin, onResult -> kiosk.unlock(member, pin, onResult) },
                        onExit = { member, pin, onResult -> kiosk.exit(member, pin, onResult) },
                        onPunch = { member, pin, onResult -> kiosk.punch(member, pin, onResult) }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        TmTopBar(
                            title = station?.name.orEmpty().ifBlank { "Counter" },
                            subtitle = currentUser?.let { user -> "Signed in as ${user.name.ifBlank { user.username }}" },
                            actions = {
                                TmButton(
                                    text = "Lock",
                                    onClick = { kiosk.lock() },
                                    variant = TmButtonVariant.SECONDARY,
                                    leadingIcon = Res.drawable.lock
                                )
                            }
                        )

                        CounterScreen(
                            modifier = Modifier
                                .weight(1f)
                                .glassSource(),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        TmSnackbarHost(state = viewModel.snackBarHost, modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(shellInsets))
    }
}

/** Says the app can still be left, with a button to ask Android to pin it again */
@Composable
private fun NotPinnedNotice(modifier: Modifier = Modifier) {
    val spacing = TmTheme.spacing

    Column(
        modifier = modifier.widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        horizontalAlignment = Alignment.End
    ) {
        TmNotice(
            kind = TmNoticeKind.WARNING,
            title = "Screen not pinned",
            text = "Anyone can swipe out of the app. Pin the screen and accept Android's prompt to keep it here. " +
                    "Leaving kiosk mode unpins it."
        )
        TmButton(text = "Pin screen", onClick = { AppWindow.requestPin() }, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.lock)
    }
}
