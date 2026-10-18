package org.mr0btain.ticketman.ui.screens.dashboard.components.customer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.time.createdAt
import org.mr0btain.ticketman.ui.screens.shared.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.StatusBadge
import org.mr0btain.ticketman.ui.screens.dashboard.components.DetailSection
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.user
import kotlin.uuid.Uuid

@Composable
fun CustomerInfoPane(
    selectedCustomer: CustomerSeed,
    onEditClick: () -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onImageClick: (Uuid) -> Unit
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
                KeyValueRow(label = "Phone", value = seed.phone.ifBlank { "None" })
                KeyValueRow(label = "Email", value = seed.email.ifBlank { "None" })
                val cityLine = listOf(seed.city, seed.state, seed.zip).filter { it.isNotBlank() }.joinToString(" ")
                val address = listOf(seed.street, cityLine).filter { it.isNotBlank() }.joinToString("\n")
                KeyValueRow(label = "Address", value = address.ifBlank { "None" })
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
                        leading = { TicketIdText(ticket.label, Modifier.width(104.dp)) },
                        trailing = { StatusBadge(ticket.status.status) },
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
