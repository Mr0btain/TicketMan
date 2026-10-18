package org.mr0btain.ticketman.ui.screens.dashboard.components.customer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.ui.screens.shared.NoteBlock
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.EditNoteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.TicketIdText
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.uuid.Uuid

// TODO: Add ability to assign already existing tickets to customer.
/**
 * Adds or edits a customer. Most changes go straight onto [seed], the name is applied when saved
 * @param onContinue Saves the seed
 */
@Composable
fun CustomerDialog(
    seed: CustomerSeed,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onImageClick: (Uuid) -> Unit
) {
    if (!isVisible) return

    var ticketToRemove by remember { mutableStateOf<Uuid?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    val noteSeed = remember { NoteSeed() }

    var firstName by remember { mutableStateOf(seed.firstName) }
    var lastName by remember { mutableStateOf(seed.lastName) }
    val spacing = TmTheme.spacing

    EditNoteDialog(
        seed = noteSeed,
        label = if (noteSeed.message.isBlank()) "Add note" else "Edit note",
        isVisible = showNoteDialog,
        onConfirm = { seed.saveNote(noteSeed.note) },
        onDismissRequest = {
            noteSeed.clear()
            showNoteDialog = false
        }
    )

    ConfirmDeleteDialog(
        dataType = DataType.TICKET,
        isVisible = ticketToRemove != null,
        onConfirm = {
            ticketToRemove?.let { seed.deleteTicket(it) }
            ticketToRemove = null
        },
        onCancel = { ticketToRemove = null }
    )

    TmDialog(
        title = if (seed.isNew) "New customer" else "Edit ${seed.name.joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }.trim()}",
        onDismissRequest = onDismiss,
        maxWidth = 640.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (seed.isNew) "Add customer" else "Save customer",
                confirmEnabled = firstName.isNotBlank(),
                onCancel = onDismiss,
                onConfirm = {
                    // Name is only applied on save so a half typed name doesn't flicker into the list
                    if (firstName != seed.firstName || lastName != seed.lastName) {
                        seed.changeName(listOf(firstName, lastName))
                    }

                    onContinue()
                    onDismiss()
                }
            )
        }
    ) {
        TmSectionLabel("Name")
        TmFieldPair(
            { TmTextField(modifier = it, value = firstName, onValueChange = { v -> firstName = v }, label = "First name") },
            { TmTextField(modifier = it, value = lastName, onValueChange = { v -> lastName = v }, label = "Last name") }
        )

        TmSectionLabel("Contact")
        TmFieldPair(
            { TmTextField(modifier = it, value = seed.phone, onValueChange = { v -> seed.changePhone(v) }, label = "Phone") },
            { TmTextField(modifier = it, value = seed.email, onValueChange = { v -> seed.changeEmail(v) }, label = "Email") }
        )

        TmSectionLabel("Address")
        TmTextField(modifier = Modifier.fillMaxWidth(), value = seed.street, onValueChange = { seed.changeStreet(it) }, label = "Street")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmTextField(modifier = Modifier.weight(2f), value = seed.city, onValueChange = { seed.changeCity(it) }, label = "City")
            TmTextField(modifier = Modifier.weight(1f), value = seed.state, onValueChange = { seed.changeState(it) }, label = "State")
            TmTextField(modifier = Modifier.weight(1f), value = seed.zip, onValueChange = { seed.changeZip(it) }, label = "Zip")
        }

        if (seed.tickets.isNotEmpty()) {
            TmSectionLabel("Tickets")
            Column {
                seed.tickets.forEach { ticket ->
                    TmListRow(
                        title = ticket.device.toString().ifBlank { "Unknown device" },
                        leading = { TicketIdText(ticket.label, Modifier.width(104.dp)) },
                        trailing = {
                            TmIconButton(
                                icon = Res.drawable.x,
                                contentDescription = "Remove ticket from customer",
                                onClick = { ticketToRemove = ticket.identifier },
                                size = TmControlSize.SMALL
                            )
                        }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TmSectionLabel("Notes") }
            TmButton(
                text = "Add note",
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.SMALL,
                leadingIcon = Res.drawable.plus,
                onClick = {
                    noteSeed.clear()
                    showNoteDialog = true
                }
            )
        }

        NoteBlock(
            notes = seed.notes,
            editable = true,
            onImageClick = onImageClick,
            onEditClick = { note ->
                noteSeed.setNote(note)
                showNoteDialog = true
            }
        )
    }
}
