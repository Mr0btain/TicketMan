package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.ticket.TicketScope
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmPriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.records.components.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tool
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Ticket", width = 112.dp),
    TmColumn("Device"),
    TmColumn("Priority", width = 96.dp),
    TmColumn("Status", width = 136.dp)
)

@Composable
fun TicketListPane(
    ticketList: List<Ticket>,
    ticketScope: TicketScope,
    selectedTicket: Uuid,
    onClick: (Uuid) -> Unit,
    state: LazyListState,
    loadError: String? = null
) {
    RecordsList(
        columns = COLUMNS,
        state = state,
        isEmpty = ticketList.isEmpty(),
        emptyTitle = if (ticketScope == TicketScope.MINE) "No tickets assigned to you" else "No tickets yet",
        emptyIcon = Res.drawable.tool,
        emptyMessage = if (ticketScope == TicketScope.MINE)
            "Tickets show up here once you're picked as the technician. Switch to All tickets to see everyone's."
        else
            "Devices checked in at the counter show up here.",
        loadError = loadError
    ) {
        items(ticketList, key = { it.identifier }) { ticket ->
            TmTableRow(COLUMNS, selected = ticket.identifier == selectedTicket, onClick = { onClick(ticket.identifier) }) { column ->
                when (column) {
                    0 -> TmTicketIdText(ticket.label)
                    1 -> Text(
                        text = ticket.device.toString().ifBlank { "Unknown device" },
                        style = TmTheme.typography.label,
                        color = TmTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    2 -> TmPriorityBadge(ticket.priority.priority)
                    3 -> TmStatusBadge(ticket.status.status)
                }
            }
        }
    }
}
