package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.CustomerDetail
import org.mr0btain.ticketman.ui.screens.counter.components.TicketRow
import org.mr0btain.ticketman.ui.screens.counter.components.isClosed
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.inbox
import ticketman.composeapp.generated.resources.phone
import ticketman.composeapp.generated.resources.tool
import kotlin.uuid.Uuid

/**
 * A customer and their tickets, newest first
 * @param actions Room for buttons added by later screens, like checking in a device for them
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerCard(
    detail: CustomerDetail,
    onTicketClick: (Uuid) -> Unit,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val customer = detail.customer
    val notPickedUp = detail.tickets.count { !it.isClosed }
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPageHeader(title = customer.cleanName(), onBack = onBack, actions = actions)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            TmPanel(modifier = Modifier.width(340.dp), title = "Contact", icon = Res.drawable.phone) {
                Column {
                    KeyValueRow(label = "Phone", value = customer.phone.ifBlank { "None" })
                    KeyValueRow(label = "Email", value = customer.email.ifBlank { "None" })

                    val address = customer.address
                    val lines = listOf(address.street, listOf(address.city, address.state, address.zip).filter { it.isNotBlank() }.joinToString(" "))
                        .filter { it.isNotBlank() }

                    if (lines.isNotEmpty()) KeyValueRow(label = "Address", value = lines.joinToString("\n"))
                }
            }

            TmPanel(
                modifier = Modifier.widthIn(min = 420.dp, max = 640.dp),
                title = "Tickets",
                subtitle = "${detail.tickets.size} total, $notPickedUp not picked up",
                icon = Res.drawable.tool
            ) {
                if (detail.tickets.isEmpty()) {
                    TmEmptyState(icon = Res.drawable.inbox, title = "No tickets yet")
                }

                Column {
                    detail.tickets.forEach { ticket ->
                        TicketRow(ticket, onClick = { onTicketClick(ticket.identifier) })
                    }
                }
            }
        }
    }
}
