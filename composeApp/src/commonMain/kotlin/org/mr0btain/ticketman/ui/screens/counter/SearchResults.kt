package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmProgressBar
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.TicketSummaryRow
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.search
import ticketman.composeapp.generated.resources.tool
import ticketman.composeapp.generated.resources.user
import kotlin.uuid.Uuid

/** Keyboard shortcuts on the counter, shown before anything is searched */
private val SHORTCUTS = listOf(
    "F2" to "Search",
    "F3" to "Check in a device",
    "F4" to "Price check",
    "F5" to "Pick up the open ticket",
    "Esc" to "Back"
)

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
    val spacing = TmTheme.spacing

    if (query.isBlank()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TmEmptyState(
                icon = Res.drawable.search,
                title = "Search the shop",
                message = "Type a ticket number, phone number, customer name, email or device."
            )

            TmPanel(modifier = Modifier.widthIn(max = 360.dp), title = "Shortcuts") {
                Column {
                    SHORTCUTS.forEach { (key, action) -> TmKeyValueRow(label = action, value = key, numeric = true) }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        if (searching) TmProgressBar()

        TmPanel(title = "Customers", subtitle = countText(results.customers.size, searching), icon = Res.drawable.user) {
            Column {
                results.customers.forEach { customer ->
                    TmListRow(
                        title = customer.name,
                        subtitle = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                        onClick = { onCustomerClick(customer.identifier) }
                    )
                }
            }
        }

        TmPanel(title = "Tickets", subtitle = countText(results.tickets.size, searching), icon = Res.drawable.tool) {
            Column {
                results.tickets.forEach { summary ->
                    TicketSummaryRow(summary, onClick = { onTicketClick(summary.ticket.identifier) })
                }
            }
        }
    }
}

private fun countText(count: Int, searching: Boolean): String = when {
    searching && count == 0 -> "Searching"
    count == 0 -> "No matches"
    count == 1 -> "1 match"
    else -> "$count matches"
}
