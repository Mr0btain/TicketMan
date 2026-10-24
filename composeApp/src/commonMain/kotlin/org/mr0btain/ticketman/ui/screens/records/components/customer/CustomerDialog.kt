package org.mr0btain.ticketman.ui.screens.records.components.customer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.note.NoteSeed
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.search.TmSearchBar
import org.mr0btain.ticketman.ui.kit.notes.TmEditNoteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmMessageConsent
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.uuid.Uuid

/**
 * Adds or edits a customer. Most changes go straight onto [seed], the name is applied when saved
 * @param onContinue Saves the seed and closes the dialog. Cancelling goes through onDismiss, which reloads the record
 * @param onTicketSearchQuery Tickets matching what's typed in Link a ticket
 * @param onFindTicket Loads a picked ticket and the customer it's on now
 */
@Composable
fun CustomerDialog(
    seed: CustomerSeed,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onImageClick: (noteIdentifier: Uuid, page: Int) -> Unit,
    onTicketSearchQuery: suspend (String) -> List<SearchResult>,
    onFindTicket: suspend (Uuid) -> TmResult<TicketToLink, ServerError>
) {
    if (!isVisible) return

    // Saving links tickets but never unlinks them (a ticket leaves by going to another customer), so only links
    // made in this dialog can be taken back
    val savedTickets = remember { seed.tickets.map { it.identifier }.toSet() }
    // A ticket already on someone else, waiting for the move to be confirmed
    var ticketToMove by remember { mutableStateOf<TicketToLink?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    // Bumped after a pick, which clears the search
    var linkSearchKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var showNoteDialog by remember { mutableStateOf(false) }
    val noteSeed = remember { NoteSeed() }

    var name by remember { mutableStateOf(seed.name) }
    val nameChanged = name.trim() != seed.name
    val spacing = TmTheme.spacing

    TmEditNoteDialog(
        seed = noteSeed,
        label = if (noteSeed.isNew) "Add note" else "Edit note",
        isVisible = showNoteDialog,
        onConfirm = { seed.saveNote(noteSeed.note) },
        onDelete = { seed.deleteNote(noteSeed.identifier) },
        onDismissRequest = {
            noteSeed.clear()
            showNoteDialog = false
        }
    )

    ticketToMove?.let { move ->
        TmConfirmDialog(
            title = "Move ${move.ticket.label}?",
            message = "It's on ${move.currentCustomer?.text?.ifBlank { null } ?: "another customer"} now. Saving moves it to " +
                "${seed.name.ifBlank { "this customer" }}.",
            confirmText = "Move here",
            onConfirm = {
                seed.addTicket(move.ticket)
                ticketToMove = null
            },
            onDismissRequest = { ticketToMove = null }
        )
    }

    TmDialog(
        title = if (seed.isNew) "New customer" else "Edit ${seed.name}",
        onDismissRequest = onDismiss,
        maxWidth = 640.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (seed.isNew) "Add customer" else "Save customer",
                confirmEnabled = name.isNotBlank() && (seed.isDirty || nameChanged),
                onCancel = onDismiss,
                onConfirm = {
                    // Name is only applied on save so a half typed name doesn't flicker into the list
                    if (nameChanged) seed.changeName(name.trim())

                    onContinue()
                }
            )
        }
    ) {
        TmSectionLabel("Name")
        TmTextField(modifier = Modifier.fillMaxWidth(), value = name, onValueChange = { name = it }, label = "Name")

        TmSectionLabel("Contact")
        TmFieldPair(
            { TmTextField(modifier = it, value = seed.phone, onValueChange = { v -> seed.changePhone(v) }, label = "Phone") },
            { TmTextField(modifier = it, value = seed.email, onValueChange = { v -> seed.changeEmail(v) }, label = "Email") }
        )
        TmMessageConsent(
            email = seed.notifyEmail,
            sms = seed.notifySms,
            onEmailChange = { seed.changeNotifyEmail(it) },
            onSmsChange = { seed.changeNotifySms(it) },
            hasEmail = seed.email.isNotBlank(),
            hasPhone = seed.phone.isNotBlank()
        )

        TmSectionLabel("Address")
        TmTextField(modifier = Modifier.fillMaxWidth(), value = seed.street, onValueChange = { seed.changeStreet(it) }, label = "Street")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmTextField(modifier = Modifier.weight(2f), value = seed.city, onValueChange = { seed.changeCity(it) }, label = "City")
            TmTextField(modifier = Modifier.weight(1f), value = seed.state, onValueChange = { seed.changeState(it) }, label = "State")
            TmTextField(modifier = Modifier.weight(1f), value = seed.zip, onValueChange = { seed.changeZip(it) }, label = "Zip")
        }

        TmSectionLabel("Tickets")
        key(linkSearchKey) {
            TmSearchBar(
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Link a ticket by number, device or problem",
                onQuery = onTicketSearchQuery,
                onPick = { result ->
                    linkError = null
                    linkSearchKey++
                    if (seed.tickets.any { it.identifier == result.identifier }) return@TmSearchBar

                    scope.launch {
                        when (val found = onFindTicket(result.identifier)) {
                            is TmResult.Error -> linkError = "Couldn't load that ticket: ${found.error.message}"
                            is TmResult.Success -> {
                                val current = found.data.currentCustomer
                                if (current == null || current.identifier == seed.identifier) seed.addTicket(found.data.ticket)
                                else ticketToMove = found.data
                            }
                        }
                    }
                }
            )
        }
        linkError?.let { Text(text = it, style = TmTheme.typography.caption, color = TmTheme.colors.danger.color) }

        if (seed.tickets.isNotEmpty()) {
            Column {
                seed.tickets.forEach { ticket ->
                    TmListRow(
                        title = ticket.device.toString().ifBlank { "Unknown device" },
                        subtitle = if (ticket.identifier in savedTickets) null else "Linked when saved",
                        leading = { TmTicketIdText(ticket.label, Modifier.width(104.dp)) },
                        trailing = if (ticket.identifier in savedTickets) null else {
                            {
                                TmIconButton(
                                    icon = Res.drawable.x,
                                    contentDescription = "Don't link this ticket",
                                    onClick = { seed.deleteTicket(ticket.identifier) },
                                    size = TmControlSize.SMALL
                                )
                            }
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

        TmNoteBlock(
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
