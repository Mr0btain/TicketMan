package org.mr0btain.ticketman.ui.screens.counter.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.components.utils.isBeforeToday
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.ui.composables.TableStatusItem

/**
 * A ticket in a list: number, device, customer and status, the whole row is clickable
 */
@Composable
fun TicketRow(
    ticket: Ticket,
    customerName: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.width(110.dp),
                text = ticket.label,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = ticket.device.toString().ifBlank { "Unknown device" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val details = listOfNotNull(
                    customerName?.takeIf { it.isNotBlank() },
                    ticket.promisedAt?.let { "due ${formatDay(it).lowercase()}" }
                )

                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" - "),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (ticket.isOverdue) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
            }

            TableStatusItem(
                status = ticket.status,
                textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}

@Composable
fun TicketSummaryRow(summary: TicketSummary, onClick: () -> Unit) =
    TicketRow(summary.ticket, summary.customer?.text?.let { cleanName(it) }, onClick)

/** Promised before today and still being worked on */
val Ticket.isOverdue: Boolean
    get() = status.status.isOpen && promisedAt?.let { isBeforeToday(it) } == true

/** Names are stored as first:last */
fun cleanName(name: String): String =
    name.split(":").joinToString(" ") { part -> part.replaceFirstChar { it.titlecase() } }.trim()

/** Small all caps label above a group of fields */
@Composable
fun FieldLabel(text: String) {
    Text(
        modifier = Modifier.padding(top = 8.dp),
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Picked up tickets can't be changed from the counter */
val Ticket.isClosed: Boolean
    get() = status.status == TicketStatusType.PICKED_UP
