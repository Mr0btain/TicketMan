package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.ui.screens.shared.NoteBlock
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.data.MoneyText
import org.mr0btain.ticketman.ui.kit.feedback.PriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.StatusBadge
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.cleanName
import org.mr0btain.ticketman.ui.screens.main.expanded.DetailSection
import org.mr0btain.ticketman.ui.screens.main.expanded.RecordDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tag
import kotlin.uuid.Uuid

@Composable
fun TicketInfoPane(
    seed: TicketSeed,
    onEditClick: () -> Unit,
    onImageClick: (Uuid) -> Unit
) {
    val ticket = seed.ticket

    RecordDetail(
        title = ticket.label,
        subtitle = ticket.device.toString().ifBlank { "Unknown device" },
        icon = Res.drawable.tag,
        isEmpty = seed.isNew,
        emptyTitle = "No ticket picked",
        onEdit = onEditClick
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            StatusBadge(ticket.status.status)
            PriorityBadge(ticket.priority.priority)
        }

        DetailSection("Details") {
            Column {
                KeyValueRow(label = "Customer", value = seed.customer?.text?.let { cleanName(it) } ?: "None")
                KeyValueRow(label = "Technician", value = seed.user?.text?.let { cleanName(it) } ?: "Not assigned")
                KeyValueRow(label = "Make", value = ticket.device.manufacturer.ifBlank { "Not set" })
                KeyValueRow(label = "Model", value = ticket.device.model.ifBlank { "Not set" })
                if (ticket.device.serial.isNotBlank()) KeyValueRow(label = "Serial", value = ticket.device.serial, numeric = true)
                KeyValueRow(label = "Created", value = formatDateTime(seed.time.toEpochMilliseconds()))
                ticket.estimateCents?.let { KeyValueRow(label = "Estimate") { MoneyText(it) } }
            }
        }

        if (ticket.problem.isNotBlank()) {
            DetailSection("Problem") {
                androidx.compose.material3.Text(text = ticket.problem, style = TmTheme.typography.body, color = TmTheme.colors.text)
            }
        }

        DetailSection("Updates") {
            NoteBlock(notes = seed.notes, onImageClick = onImageClick, emptyText = "No updates yet")
        }
    }
}
