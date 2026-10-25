package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDate
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmAdjustBalanceDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.CustomerDetail
import org.mr0btain.ticketman.ui.screens.counter.components.TicketRow
import org.mr0btain.ticketman.ui.screens.counter.components.isClosed
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.gift
import ticketman.composeapp.generated.resources.inbox
import ticketman.composeapp.generated.resources.phone
import ticketman.composeapp.generated.resources.tool
import kotlin.uuid.Uuid

/**
 * A customer with their tickets, store credit and purchases, newest first
 * @param onAdjustCredit Gives or takes store credit, null for anyone but admins
 * @param actions Room for buttons added by later screens, like checking in a device for them
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerCard(
    detail: CustomerDetail,
    onTicketClick: (Uuid) -> Unit,
    onBack: () -> Unit,
    onAdjustCredit: ((Long, String, (String?) -> Unit) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val customer = detail.customer
    val colors = TmTheme.colors
    var adjusting by remember { mutableStateOf(false) }

    if (adjusting && onAdjustCredit != null) {
        TmAdjustBalanceDialog(
            title = "Store credit for ${customer.name}",
            balanceCents = customer.creditCents,
            onSave = onAdjustCredit,
            onDismissRequest = { adjusting = false }
        )
    }
    val notPickedUp = detail.tickets.count { !it.isClosed }
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPageHeader(title = customer.name, onBack = onBack, actions = actions)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            TmPanel(modifier = Modifier.width(340.dp), title = "Contact", icon = Res.drawable.phone) {
                Column {
                    TmKeyValueRow(label = "Phone", value = customer.phone.ifBlank { "None" })
                    TmKeyValueRow(label = "Email", value = customer.email.ifBlank { "None" })

                    val address = customer.address
                    val lines = listOf(address.street, listOf(address.city, address.state, address.zip).filter { it.isNotBlank() }.joinToString(" "))
                        .filter { it.isNotBlank() }

                    if (lines.isNotEmpty()) TmKeyValueRow(label = "Address", value = lines.joinToString("\n"))
                }
            }

            TmPanel(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
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

            // ------------------------------------------ Store credit ------------------------------------------
            if (customer.creditCents > 0 || detail.credit.isNotEmpty() || onAdjustCredit != null) {
                TmPanel(
                    modifier = Modifier.width(340.dp),
                    title = "Store credit",
                    icon = Res.drawable.gift,
                    actions = {
                        if (onAdjustCredit != null) {
                            TmButton(text = "Adjust", onClick = { adjusting = true }, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL)
                        }
                    }
                ) {
                    TmKeyValueRow(label = "To spend") { TmMoneyText(customer.creditCents, style = TmTheme.typography.numericLarge) }

                    Column {
                        detail.credit.take(CREDIT_ROWS).forEach { entry ->
                            TmKeyValueRow(label = "${entry.reason}, ${formatDate(entry.at)}") {
                                TmMoneyText(entry.amountCents, signed = true, color = if (entry.amountCents < 0) colors.textMuted else colors.success.color)
                            }
                        }
                    }
                }
            }

            // ------------------------------------------ Purchases ------------------------------------------
            if (detail.invoices.isNotEmpty()) {
                TmPanel(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    title = "Invoices",
                    subtitle = "Repairs and sales, newest first",
                    icon = Res.drawable.file_text
                ) {
                    Column {
                        detail.invoices.forEach { invoice ->
                            TmListRow(
                                title = invoice.label,
                                subtitle = listOf(
                                    if (invoice.isSale) "Counter sale" else "Repair",
                                    formatEnumName(invoice.status.name),
                                    formatDate(invoice.createdAt)
                                ).joinToString(", "),
                                trailing = { TmMoneyText(invoice.totalCents) },
                                onClick = invoice.ticketIdentifier?.takeIf { !invoice.ticketRemoved }?.let { ticket -> { onTicketClick(ticket) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How many credit changes the card lists */
private const val CREDIT_ROWS = 6
