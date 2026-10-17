package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.ui.screens.counter.components.cleanName

/**
 * The kiosk's signed out screen: pick your name, type your PIN
 * @param onUnlock Signs someone in, calls back with what went wrong or null if it worked
 * @param onExit Leaves kiosk mode with an admin's PIN, calls back the same way
 */
@Composable
fun LockScreen(
    stationName: String,
    staff: List<StaffMember>,
    onRefresh: () -> Unit,
    onUnlock: (StaffMember, String, (String?) -> Unit) -> Unit,
    onExit: (StaffMember, String, (String?) -> Unit) -> Unit
) {
    var selected by remember { mutableStateOf<StaffMember?>(null) }
    var exiting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stationName, fontSize = 18.sp)
            Text(
                text = when {
                    selected == null && exiting -> "Admin, pick your name to leave kiosk mode"
                    selected == null -> "Who's at the counter?"
                    else -> "Hi ${cleanName(selected!!.name).ifBlank { selected!!.username }}, enter your PIN"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.padding(16.dp))

            val member = selected

            if (member == null) {
                if (staff.isEmpty()) {
                    Text(text = "Nobody can sign in here yet. Staff set a PIN from Settings.", fontSize = 16.sp)
                    TextButton(onClick = onRefresh) { Text("Check Again") }
                }

                FlowRow(
                    modifier = Modifier.widthIn(max = 900.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    staff.forEach { person ->
                        ElevatedButton(
                            modifier = Modifier.size(width = 200.dp, height = 88.dp),
                            onClick = {
                                selected = person
                                error = null
                            }
                        ) {
                            Text(
                                text = cleanName(person.name).ifBlank { person.username },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.padding(24.dp))

                TextButton(onClick = { exiting = !exiting }) {
                    Text(if (exiting) "Never mind" else "Leave kiosk mode")
                }
            } else {
                PinPad(
                    error = error,
                    busy = busy,
                    onSubmit = { pin ->
                        busy = true
                        error = null

                        val done: (String?) -> Unit = { problem ->
                            busy = false
                            error = problem
                            if (problem == null) {
                                selected = null
                                exiting = false
                            }
                        }

                        if (exiting) onExit(member, pin, done) else onUnlock(member, pin, done)
                    }
                )

                Spacer(Modifier.padding(16.dp))

                OutlinedButton(onClick = {
                    selected = null
                    error = null
                }) { Text("Not me") }
            }
        }
    }
}
