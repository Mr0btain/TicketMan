package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmPriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.records.components.DetailSection
import org.mr0btain.ticketman.ui.screens.records.components.RecordDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tag
import kotlin.uuid.Uuid

@Composable
fun TicketInfoPane(
    seed: TicketSeed,
    onEditClick: () -> Unit,
    canEdit: Boolean = true,
    onImageClick: (noteIdentifier: Uuid, page: Int) -> Unit,
    work: @Composable () -> Unit = {}
) {
    val ticket = seed.ticket

    RecordDetail(
        title = ticket.label,
        subtitle = ticket.device.toString().ifBlank { "Unknown device" },
        icon = Res.drawable.tag,
        isEmpty = seed.isNew,
        emptyTitle = "No ticket picked",
        onEdit = onEditClick,
        canEdit = canEdit
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            TmStatusBadge(ticket.status.status)
            TmPriorityBadge(ticket.priority.priority)
        }

        DetailSection("Details") {
            Column {
                TmKeyValueRow(label = "Customer", value = seed.customer?.text ?: "None")
                TmKeyValueRow(label = "Technician", value = seed.user?.text ?: "Not assigned")
                TmKeyValueRow(label = "Make", value = ticket.device.manufacturer.ifBlank { "Not set" })
                TmKeyValueRow(label = "Model", value = ticket.device.model.ifBlank { "Not set" })
                if (ticket.device.serial.isNotBlank()) TmKeyValueRow(label = "Serial", value = ticket.device.serial, numeric = true)
                TmKeyValueRow(label = "Created", value = formatDateTime(seed.time.toEpochMilliseconds()))
                ticket.estimateCents?.let { TmKeyValueRow(label = "Estimate") { TmMoneyText(it) } }
            }
        }

        if (ticket.problem.isNotBlank()) {
            DetailSection("Problem") {
                Text(text = ticket.problem, style = TmTheme.typography.body, color = TmTheme.colors.text)
            }
        }

        work()

        DetailSection("Updates") {
            TmNoteBlock(notes = seed.notes, onImageClick = onImageClick, emptyText = "No updates yet")
        }
    }
}
