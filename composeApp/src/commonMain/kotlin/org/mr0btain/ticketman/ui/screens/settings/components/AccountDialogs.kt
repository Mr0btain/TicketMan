package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.ui.composables.PinTextField
import org.mr0btain.ticketman.ui.screens.admin.components.formatDateTime
import org.mr0btain.ticketman.ui.screens.login.components.PasswordTextField

/**
 * Changes your own station PIN. Asks for your password so someone at an unlocked screen can't do it
 * @param onSave Sends it, calls back with null when done or what went wrong
 */
@Composable
fun ChangePinDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSave: (password: String, pin: String, onResult: (String?) -> Unit) -> Unit
) {
    if (!isVisible) return

    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val problem = when {
        password.isBlank() -> "Enter your password"
        PinRules.problem(pin) != null -> PinRules.problem(pin)
        pin != confirmPin -> "PINs don't match"
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(380.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Change PIN", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                PasswordTextField(
                    modifier = Modifier.padding(top = 8.dp),
                    text = password,
                    labelText = "Your Password",
                    onTextChanged = { password = it }
                )

                PinTextField(value = pin, label = "New PIN", onValueChange = { pin = it })
                PinTextField(
                    value = confirmPin,
                    label = "Confirm PIN",
                    isError = confirmPin.length == PinRules.LENGTH && confirmPin != pin,
                    onValueChange = { confirmPin = it }
                )

                (serverError ?: problem.takeIf { pin.isNotEmpty() })?.let { message ->
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.padding(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }

                    Button(
                        enabled = problem == null && !saving,
                        onClick = {
                            saving = true
                            serverError = null
                            onSave(password, pin) { error ->
                                saving = false
                                serverError = error
                                if (error == null) onDismiss()
                            }
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}

/**
 * Registers this device as a counter station, or shows how its registration is going
 * @param station This device's station, null if it isn't one
 * @param onRegister Sends the name, calls back with null when done or what went wrong
 */
@Composable
fun StationDialog(
    isVisible: Boolean,
    station: Station?,
    defaultName: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onRegister: (name: String, onResult: (String?) -> Unit) -> Unit
) {
    if (!isVisible) return

    var name by remember(station) { mutableStateOf(station?.name ?: defaultName) }
    var registering by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Counter Station", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    text = when (station?.status) {
                        null -> "Make this device a station so staff can sign in with their PIN. An admin has to approve it first."
                        StationStatus.PENDING -> "Registered as \"${station.name}\" and waiting for an admin to approve it."
                        StationStatus.APPROVED -> "\"${station.name}\" was approved" +
                            (station.approvedBy?.let { " by $it" } ?: "") +
                            (station.approvedAt?.let { " on ${formatDateTime(it)}" } ?: "") + ". Staff can PIN in here."
                    }
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station Name") },
                    singleLine = true
                )

                if (station != null) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = "Registering again needs a new approval.",
                        fontSize = 12.sp
                    )
                }

                serverError?.let {
                    Text(modifier = Modifier.padding(top = 8.dp), text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.padding(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Close") }

                    if (station?.status == StationStatus.PENDING) {
                        OutlinedButton(onClick = onRefresh) { Text("Check Again") }
                    }

                    Button(
                        enabled = name.isNotBlank() && !registering,
                        onClick = {
                            registering = true
                            serverError = null
                            onRegister(name) { error ->
                                registering = false
                                serverError = error
                            }
                        }
                    ) { Text(if (station == null) "Register" else "Register Again") }
                }
            }
        }
    }
}
