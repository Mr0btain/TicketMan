package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.screens.counter.components.CustomerDetail
import org.mr0btain.ticketman.ui.screens.counter.components.TicketRow
import org.mr0btain.ticketman.ui.screens.counter.components.isClosed
import kotlin.uuid.Uuid

/**
 * A customer and their tickets, newest first
 * @param actions Room for buttons added by later screens, like checking in a device for them
 */
@Composable
fun CustomerCard(
    detail: CustomerDetail,
    onTicketClick: (Uuid) -> Unit,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val customer = detail.customer
    val notPickedUp = detail.tickets.count { !it.isClosed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(text = customer.cleanName(), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            actions()
        }

        FlowRow {
            SectionCard(modifier = Modifier.width(360.dp), title = "Contact") {
                SimpleTextItem("Phone", customer.phone.ifBlank { "-" })
                SimpleTextItem("Email", customer.email.ifBlank { "-" })

                val address = customer.address
                val lines = listOf(address.street, listOf(address.city, address.state, address.zip).filter { it.isNotBlank() }.joinToString(" "))
                    .filter { it.isNotBlank() }

                if (lines.isNotEmpty()) {
                    Text(modifier = Modifier.padding(top = 8.dp), text = lines.joinToString("\n"), fontSize = 14.sp)
                }
            }

            SectionCard(modifier = Modifier.width(560.dp), title = "Tickets (${detail.tickets.size}, $notPickedUp not picked up)") {
                if (detail.tickets.isEmpty()) EmptyMessage("No tickets yet")

                detail.tickets.forEach { ticket ->
                    TicketRow(ticket, onClick = { onTicketClick(ticket.identifier) })
                }
            }
        }
    }
}
