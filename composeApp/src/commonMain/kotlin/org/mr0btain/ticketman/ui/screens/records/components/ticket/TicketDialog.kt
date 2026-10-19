package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.notes.TmEditNoteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.label
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchBar
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

/**
 * Adds or edits a ticket. Changes go straight onto [seed]
 * @param onContinue Saves the seed and closes the dialog. Cancelling goes through onDismiss, which reloads the record
 */
@Composable
fun TicketDialog(
    seed: TicketSeed,
    isVisible: Boolean,
    onCustomerSearchQuery: suspend (String) -> List<SearchResult>,
    onUserSearchQuery: suspend (String) -> List<SearchResult>,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var showNoteDialog by remember { mutableStateOf(false) }
    val noteSeed = remember { NoteSeed() }
    val colors = TmTheme.colors

    TmEditNoteDialog(
        seed = noteSeed,
        label = if (noteSeed.message.isBlank()) "Add update" else "Edit update",
        isVisible = showNoteDialog,
        onConfirm = { seed.saveNote(noteSeed.note) },
        onDelete = { seed.deleteNote(noteSeed.identifier) },
        onDismissRequest = {
            noteSeed.clear()
            showNoteDialog = false
        }
    )

    TmDialog(
        title = if (seed.isNew) "New ticket" else "Edit ${seed.ticket.label}",
        onDismissRequest = onDismiss,
        maxWidth = 640.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (seed.isNew) "Add ticket" else "Save ticket",
                confirmEnabled = seed.isDirty,
                onCancel = onDismiss,
                onConfirm = onContinue
            )
        }
    ) {
        TmSectionLabel("People")
        TmFieldPair(
            {
                TmSearchBar(
                    modifier = it,
                    label = "Customer",
                    placeholder = "Search customers",
                    initialQuery = seed.customer?.text ?: "",
                    onPick = { result -> seed.changeCustomer(EntityReference(result.identifier, result.text)) },
                    onQuery = onCustomerSearchQuery
                )
            },
            {
                TmSearchBar(
                    modifier = it,
                    label = "Technician",
                    placeholder = "Search staff",
                    initialQuery = seed.user?.text ?: "",
                    onPick = { result -> seed.changeUser(EntityReference(result.identifier, result.text)) },
                    onQuery = onUserSearchQuery
                )
            }
        )

        TmSectionLabel("Device")
        TmFieldPair(
            { TmTextField(modifier = it, value = seed.device.manufacturer, onValueChange = { v -> seed.changeDevice(seed.device.copy(manufacturer = v)) }, label = "Make") },
            { TmTextField(modifier = it, value = seed.device.model, onValueChange = { v -> seed.changeDevice(seed.device.copy(model = v)) }, label = "Model") }
        )
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = seed.problem,
            onValueChange = { seed.changeProblem(it) },
            label = "Problem",
            singleLine = false,
            minLines = 2
        )

        TmSectionLabel("Progress")
        TmFieldPair(
            {
                TmDropdown(
                    modifier = it,
                    label = "Status",
                    // A picked up ticket is closed, the server won't change its status
                    options = if (seed.status.status == TicketStatusType.PICKED_UP) listOf(TicketStatusType.PICKED_UP) else TicketStatusType.selectable,
                    selected = seed.status.status,
                    onSelect = { status -> seed.changeStatus(TicketStatus(status)) },
                    optionLabel = { status -> status.label },
                    optionLeading = { status -> TmDot(colors.tone(status).color) }
                )
            },
            {
                TmDropdown(
                    modifier = it,
                    label = "Priority",
                    options = TicketPriorityType.entries,
                    selected = seed.priority.priority,
                    onSelect = { priority -> seed.changePriority(TicketPriority(priority)) },
                    optionLabel = { priority -> formatEnumName(priority.name) },
                    optionLeading = { priority -> TmDot(colors.tone(priority).color) }
                )
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TmSectionLabel("Updates") }
            TmButton(
                text = "Add update",
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.SMALL,
                leadingIcon = Res.drawable.plus,
                onClick = {
                    noteSeed.clear()
                    showNoteDialog = true
                }
            )
        }

        TmNoteBlock(
            notes = seed.notes,
            editable = true,
            emptyText = "No updates yet",
            onEditClick = { note ->
                noteSeed.setNote(note)
                showNoteDialog = true
            }
        )
    }
}
