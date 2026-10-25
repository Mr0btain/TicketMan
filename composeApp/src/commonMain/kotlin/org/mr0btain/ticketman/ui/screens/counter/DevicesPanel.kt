package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.mr0btain.ticketman.data.ticket.CustomerDevice
import org.mr0btain.ticketman.data.ticket.DeviceKind
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.TicketRow
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.cpu
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.smartphone
import ticketman.composeapp.generated.resources.tablet
import ticketman.composeapp.generated.resources.tv
import ticketman.composeapp.generated.resources.watch
import kotlin.uuid.Uuid

/** An icon for each sort of device */
fun deviceIcon(kind: DeviceKind): DrawableResource = when (kind) {
    DeviceKind.PHONE -> Res.drawable.smartphone
    DeviceKind.TABLET -> Res.drawable.tablet
    DeviceKind.LAPTOP, DeviceKind.DESKTOP -> Res.drawable.monitor
    DeviceKind.CONSOLE -> Res.drawable.tv
    DeviceKind.WATCH -> Res.drawable.watch
    DeviceKind.OTHER -> Res.drawable.cpu
}

/**
 * The customer's devices, each opening to its repairs
 * @param tickets The customer's tickets, to show each device's
 * @param onSave Saves a device's details, null for view only; calls back with what went wrong or null
 */
@Composable
fun DevicesPanel(
    modifier: Modifier,
    devices: List<CustomerDevice>,
    tickets: List<Ticket>,
    onTicketClick: (Uuid) -> Unit,
    onSave: ((CustomerDevice, (String?) -> Unit) -> Unit)?,
    onRemove: ((CustomerDevice) -> Unit)?
) {
    var open by remember { mutableStateOf<Uuid?>(null) }
    var editing by remember { mutableStateOf<CustomerDevice?>(null) }
    var removing by remember { mutableStateOf<CustomerDevice?>(null) }

    editing?.let { device ->
        if (onSave != null) DeviceDialog(device = device, onSave = onSave, onDismiss = { editing = null })
    }

    removing?.let { device ->
        TmConfirmDialog(
            title = "Delete ${device.label}?",
            message = "Its repairs stay on the customer's tickets, they just won't be grouped under it.",
            confirmText = "Delete device",
            destructive = true,
            onConfirm = {
                onRemove?.invoke(device)
                removing = null
            },
            onDismissRequest = { removing = null }
        )
    }

    TmPanel(modifier = modifier, title = "Devices", subtitle = "Each one's repairs over time", icon = Res.drawable.smartphone) {
        if (devices.isEmpty()) {
            Text("No devices yet, they're added at check in", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }

        Column {
            devices.forEach { device ->
                val repairs = tickets.filter { it.deviceIdentifier == device.identifier }
                TmListRow(
                    title = device.label,
                    subtitle = listOfNotNull(
                        device.device.kind.label,
                        device.color.ifBlank { null },
                        if (repairs.size == 1) "1 repair" else "${repairs.size} repairs"
                    ).joinToString(", "),
                    icon = deviceIcon(device.device.kind),
                    selected = open == device.identifier,
                    trailing = onSave?.let { { TmButton(text = "Edit", onClick = { editing = device }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL) } },
                    onClick = { open = if (open == device.identifier) null else device.identifier }
                )

                if (open == device.identifier) {
                    Column(Modifier.padding(start = TmTheme.spacing.lg)) {
                        if (device.notes.isNotBlank()) Text(device.notes, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                        repairs.forEach { ticket -> TicketRow(ticket, onClick = { onTicketClick(ticket.identifier) }) }
                        if (onRemove != null) {
                            TmButton(text = "Delete device", onClick = { removing = device }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDialog(device: CustomerDevice, onSave: (CustomerDevice, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(device) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TmDialog(
        title = "Edit ${device.label}",
        onDismissRequest = onDismiss,
        maxWidth = 520.dp,
        actions = {
            TmButtonRow(
                confirmText = "Save device",
                confirmEnabled = draft != device && (draft.device.manufacturer.isNotBlank() || draft.device.model.isNotBlank()),
                loading = busy,
                onCancel = onDismiss,
                onConfirm = {
                    busy = true
                    error = null
                    onSave(draft) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismiss()
                    }
                }
            )
        }
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = draft.device.manufacturer, onValueChange = { v -> draft = draft.copy(device = draft.device.copy(manufacturer = v)) }, label = "Make") },
            { TmTextField(modifier = it, value = draft.device.model, onValueChange = { v -> draft = draft.copy(device = draft.device.copy(model = v)) }, label = "Model") }
        )
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.device.serial,
            onValueChange = { draft = draft.copy(device = draft.device.copy(serial = it)) },
            label = "Serial or IMEI",
            textStyle = TmTheme.typography.numeric
        )
        TmFieldPair(
            { TmDropdown(modifier = it, label = "Kind", options = DeviceKind.entries, selected = draft.device.kind, onSelect = { kind -> draft = draft.copy(device = draft.device.copy(kind = kind)) }, optionLabel = { kind -> kind.label }) },
            { TmTextField(modifier = it, value = draft.color, onValueChange = { v -> draft = draft.copy(color = v) }, label = "Colour") }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = draft.notes, onValueChange = { draft = draft.copy(notes = it) }, label = "Notes", singleLine = false, minLines = 2)

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}
