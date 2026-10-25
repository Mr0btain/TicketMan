package org.mr0btain.ticketman.ui.screens.counter.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import org.mr0btain.ticketman.ui.kit.feedback.TmLocationBadge
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDayInSentence
import org.mr0btain.ticketman.components.utils.isBeforeToday
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge

/**
 * A ticket in a list: number, device, customer and status, the whole row is clickable
 * @param showStatus Leave the status off where the list is already grouped by it, like the queue
 */
@Composable
fun TicketRow(
    ticket: Ticket,
    customerName: String? = null,
    showStatus: Boolean = true,
    onClick: () -> Unit
) {
    val details = listOfNotNull(
        customerName?.takeIf { it.isNotBlank() },
        ticket.promisedAt?.let { (if (ticket.isOverdue) "overdue, was due " else "due ") + formatDayInSentence(it) }
    )

    TmListRow(
        title = ticket.device.toString().ifBlank { "Unknown device" },
        subtitle = details.joinToString(", ").ifBlank { null },
        onClick = onClick,
        // Wider for a location's code, like DT-26-00000042
        leading = { TmTicketIdText(ticket.label, Modifier.widthIn(min = 104.dp)) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                // Only when it's at another location than this device's
                TmLocationBadge(ticket.locationIdentifier)
                if (showStatus) TmStatusBadge(ticket.status.choice)
            }
        }
    )
}

@Composable
fun TicketSummaryRow(summary: TicketSummary, showStatus: Boolean = true, onClick: () -> Unit) =
    TicketRow(summary.ticket, summary.customer?.text, showStatus, onClick)

/** Promised before today and still being worked on */
val Ticket.isOverdue: Boolean
    get() = status.status.isOpen && promisedAt?.let { isBeforeToday(it) } == true

/** Picked up tickets can't be changed from the counter */
val Ticket.isClosed: Boolean
    get() = status.status == TicketStatusType.PICKED_UP
