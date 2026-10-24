package org.mr0btain.ticketman.ui.screens.counter.components

import androidx.compose.foundation.layout.width
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
        leading = { TmTicketIdText(ticket.label, Modifier.width(104.dp)) },
        trailing = if (showStatus) ({ TmStatusBadge(ticket.status.choice) }) else null
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
