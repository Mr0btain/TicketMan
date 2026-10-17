package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.kit.data.TicketIdText
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.PriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.StatusBadge
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.main.expanded.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tool
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Ticket", width = 112.dp),
    TmColumn("Device"),
    TmColumn("Priority", width = 96.dp),
    TmColumn("Status", width = 136.dp)
)

// TODO: When we switch from all tickets to my tickets we need to not scroll to the item if it doesn't exist
@Composable
fun TicketListPane(
    ticketList: List<Ticket>,
    selectedTicket: Uuid,
    onClick: (Uuid) -> Unit,
    state: LazyListState
) {
    RecordsList(
        columns = COLUMNS,
        state = state,
        isEmpty = ticketList.isEmpty(),
        emptyTitle = "No tickets yet",
        emptyIcon = Res.drawable.tool,
        emptyMessage = "Devices checked in at the counter show up here."
    ) {
        items(ticketList, key = { it.identifier }) { ticket ->
            TmTableRow(COLUMNS, selected = ticket.identifier == selectedTicket, onClick = { onClick(ticket.identifier) }) { column ->
                when (column) {
                    0 -> TicketIdText(ticket.label)
                    1 -> Text(
                        text = ticket.device.toString().ifBlank { "Unknown device" },
                        style = TmTheme.typography.label,
                        color = TmTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    2 -> PriorityBadge(ticket.priority.priority)
                    3 -> StatusBadge(ticket.status.status)
                }
            }
        }
    }
}
