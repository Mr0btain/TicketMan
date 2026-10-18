package org.mr0btain.ticketman.ui.screens.records.components.customer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.time.createdAt
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.screens.records.components.DetailSection
import org.mr0btain.ticketman.ui.screens.records.components.RecordDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.user
import kotlin.uuid.Uuid

@Composable
fun CustomerInfoPane(
    selectedCustomer: CustomerSeed,
    onEditClick: () -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onImageClick: (noteIdentifier: Uuid, page: Int) -> Unit
) {
    val seed = selectedCustomer

    RecordDetail(
        title = seed.name.joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }.trim().ifBlank { "Unnamed customer" },
        subtitle = "Customer since ${formatDateTime(seed.time.toEpochMilliseconds())}",
        icon = Res.drawable.user,
        isEmpty = seed.isNew,
        emptyTitle = "No customer picked",
        onEdit = onEditClick
    ) {
        DetailSection("Contact") {
            Column {
                TmKeyValueRow(label = "Phone", value = seed.phone.ifBlank { "None" })
                TmKeyValueRow(label = "Email", value = seed.email.ifBlank { "None" })
                val cityLine = listOf(seed.city, seed.state, seed.zip).filter { it.isNotBlank() }.joinToString(" ")
                val address = listOf(seed.street, cityLine).filter { it.isNotBlank() }.joinToString("\n")
                TmKeyValueRow(label = "Address", value = address.ifBlank { "None" })
            }
        }

        DetailSection("Tickets") {
            if (seed.tickets.isEmpty()) {
                TmNoteBlock(notes = emptyList(), emptyText = "No tickets yet")
            }

            Column {
                seed.tickets.sortedByDescending { it.identifier.createdAt() }.forEach { ticket ->
                    TmListRow(
                        title = ticket.device.toString().ifBlank { "Unknown device" },
                        leading = { TmTicketIdText(ticket.label, Modifier.width(104.dp)) },
                        trailing = { TmStatusBadge(ticket.status.status) },
                        onClick = { onTicketClick(ticket.identifier) }
                    )
                }
            }
        }

        DetailSection("Notes") {
            TmNoteBlock(notes = seed.notes, onImageClick = onImageClick)
        }
    }
}
