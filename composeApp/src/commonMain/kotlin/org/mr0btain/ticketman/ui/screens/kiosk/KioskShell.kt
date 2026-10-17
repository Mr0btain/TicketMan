package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.snackbar.CustomSnackBar
import org.mr0btain.ticketman.ui.screens.counter.CounterScreen
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

/**
 * What a kiosk shows instead of the normal app: the counter with no navigation, and the PIN screen when locked.
 * Any touch or key press counts as activity for the idle lock
 */
@Composable
fun KioskShell(viewModel: MainViewModel) {
    val kiosk = viewModel.kiosk
    val locked by kiosk.locked.collectAsState()
    val staff by kiosk.staff.collectAsState()
    val station by viewModel.station.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    Scaffold(
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
            },
        snackbarHost = { CustomSnackBar(state = viewModel.snackBarHost) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (locked) {
                LockScreen(
                    stationName = station?.name.orEmpty(),
                    staff = staff,
                    onRefresh = { kiosk.refreshStaff() },
                    onUnlock = { member, pin, onResult -> kiosk.unlock(member, pin, onResult) },
                    onExit = { member, pin, onResult -> kiosk.exit(member, pin, onResult) }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(modifier = Modifier.weight(1f), text = station?.name.orEmpty(), fontSize = 14.sp)

                        currentUser?.let { user ->
                            Text(
                                text = "Signed in: ${user.cleanName().ifBlank { user.username }}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.padding(8.dp))

                        Button(onClick = { kiosk.lock() }) { Text("Lock") }
                    }

                    HorizontalDivider()

                    CounterScreen(viewModel = viewModel)
                }
            }
        }
    }
}
