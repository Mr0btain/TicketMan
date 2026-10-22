package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmPinField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair

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

    TmDialog(
        title = "Change PIN",
        description = "Used to sign in at counter stations.",
        onDismissRequest = onDismiss,
        maxWidth = 420.dp,
        actions = {
            TmButtonRow(
                confirmText = "Save PIN",
                confirmEnabled = problem == null,
                loading = saving,
                onCancel = onDismiss,
                onConfirm = {
                    saving = true
                    serverError = null
                    onSave(password, pin) { error ->
                        saving = false
                        serverError = error
                        if (error == null) onDismiss()
                    }
                }
            )
        }
    ) {
        TmPasswordField(value = password, onValueChange = { password = it }, label = "Your password")

        TmFieldPair(
            { TmPinField(modifier = it, value = pin, onValueChange = { v -> pin = v }, label = "New PIN") },
            {
                TmPinField(
                    modifier = it,
                    value = confirmPin,
                    onValueChange = { v -> confirmPin = v },
                    label = "Confirm PIN",
                    error = if (confirmPin.length == PinRules.LENGTH && confirmPin != pin) "PINs don't match" else null
                )
            }
        )

        (serverError ?: problem.takeIf { pin.isNotEmpty() })?.let { message ->
            TmNotice(text = message, kind = if (serverError != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
        }
    }
}

/**
 * Registers this device as a counter station, or shows how its registration is going
 * @param station This device's station, null if it isn't one
 * @param onRegister Sends the name, calls back with null when done or what went wrong
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationDialog(
    isVisible: Boolean,
    station: Station?,
    defaultName: String,
    kioskActive: Boolean,
    onResumeKiosk: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onRegister: (name: String, onResult: (String?) -> Unit) -> Unit
) {
    if (!isVisible) return

    var name by remember(station) { mutableStateOf(station?.name ?: defaultName) }
    var registering by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    TmDialog(
        title = "Counter station",
        description = when (station?.status) {
            null -> "Make this device a station so staff can sign in with their PIN. An admin has to approve it first."
            StationStatus.PENDING -> "Registered as \"${station.name}\" and waiting for an admin to approve it."
            StationStatus.APPROVED -> "\"${station.name}\" was approved" +
                (station.approvedBy?.let { " by $it" } ?: "") +
                (station.approvedAt?.let { " on ${formatDateTime(it)}" } ?: "") + ". Staff can PIN in here."
        },
        onDismissRequest = onDismiss,
        maxWidth = 460.dp,
        actions = {
            // Wraps when three buttons don't fit across a phone
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
            ) {
                if (station?.status == StationStatus.PENDING) {
                    TmButton(text = "Check again", onClick = onRefresh, variant = TmButtonVariant.GHOST)
                }

                Spacer(Modifier.weight(1f))

                TmButton(text = "Close", onClick = onDismiss, variant = TmButtonVariant.GHOST)
                TmButton(
                    text = if (station == null) "Register station" else "Register again",
                    enabled = name.isNotBlank(),
                    loading = registering,
                    onClick = {
                        registering = true
                        serverError = null
                        onRegister(name) { error ->
                            registering = false
                            serverError = error
                        }
                    }
                )
            }
        }
    ) {
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = name,
            onValueChange = { name = it },
            label = "Station name",
            supportingText = if (station != null) "Registering again needs a new approval." else null
        )

        // An admin left kiosk mode on this device, let them put it back
        if (station?.kioskEnabled == true && !kioskActive) {
            TmNotice(kind = TmNoticeKind.WARNING, text = "Kiosk mode is on for this station but was left on this device.")
            TmButton(
                text = "Go back to kiosk mode",
                variant = TmButtonVariant.SECONDARY,
                onClick = {
                    onResumeKiosk()
                    onDismiss()
                }
            )
        }

        serverError?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}
