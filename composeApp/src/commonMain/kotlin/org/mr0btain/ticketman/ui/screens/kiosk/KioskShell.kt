package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmSnackbarHost
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.glassSource
import org.mr0btain.ticketman.ui.kit.navigation.TmTopBar
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.CounterScreen
import org.mr0btain.ticketman.ui.MainViewModel
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
        if (locked) {
            LockScreen(
                stationName = station?.name.orEmpty(),
                staff = staff,
                onRefresh = { kiosk.refreshStaff() },
                onUnlock = { member, pin, onResult -> kiosk.unlock(member, pin, onResult) },
                onExit = { member, pin, onResult -> kiosk.exit(member, pin, onResult) }
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

        TmSnackbarHost(state = viewModel.snackBarHost, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
