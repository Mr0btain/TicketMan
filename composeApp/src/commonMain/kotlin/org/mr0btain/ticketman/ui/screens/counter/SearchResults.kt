package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.screens.counter.components.TicketSummaryRow
import kotlin.uuid.Uuid

/**
 * Tickets and customers matching the search bar. With nothing typed it says what can be searched
 */
@Composable
fun SearchResults(
    query: String,
    results: CounterSearchResponse,
    searching: Boolean,
    onTicketClick: (Uuid) -> Unit,
    onCustomerClick: (Uuid) -> Unit
) {
    if (query.isBlank()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Search by ticket number, phone number, customer name, email or device.\n\n" +
                    "F2 search  -  F4 price check  -  Esc back",
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())

        SectionCard(title = "Customers (${results.customers.size})") {
            if (results.customers.isEmpty()) EmptyMessage(if (searching) "Searching..." else "No customers match")

            results.customers.forEach { customer ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    onClick = { onCustomerClick(customer.identifier) },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent
                ) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = customer.cleanName(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" - "), fontSize = 13.sp)
                    }
                }
            }
        }

        SectionCard(title = "Tickets (${results.tickets.size})") {
            if (results.tickets.isEmpty()) EmptyMessage(if (searching) "Searching..." else "No tickets match")

            results.tickets.forEach { summary ->
                TicketSummaryRow(summary, onClick = { onTicketClick(summary.ticket.identifier) })
            }
        }
    }
}
