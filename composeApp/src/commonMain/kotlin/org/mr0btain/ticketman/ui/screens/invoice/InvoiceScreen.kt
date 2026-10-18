package org.mr0btain.ticketman.ui.screens.invoice

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmLazyColumn
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.MainViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.refresh_cw
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Invoice", width = 172.dp),
    TmColumn("Created", weight = 1f),
    TmColumn("Status", width = 96.dp),
    TmColumn("Total", width = 104.dp, end = true),
    TmColumn("Paid", width = 104.dp, end = true),
    TmColumn("Balance", width = 104.dp, end = true),
    TmColumn("", width = 72.dp, end = true)
)

/**
 * Every invoice, newest first. Clicking one opens its ticket at the counter, admins can void them
 * @param onOpenTicket Shows the invoice's ticket on the counter screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InvoiceScreen(
    viewModel: MainViewModel,
    onOpenTicket: (Uuid) -> Unit
) {
    val counter = viewModel.counter
    val invoices by counter.invoices.collectAsState()
    val endReached by counter.invoicesEndReached.collectAsState()
    val loginState by viewModel.session.loginState.collectAsState()
    val isAdmin by viewModel.session.isAdmin.collectAsState()
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    var voiding by remember { mutableStateOf<Invoice?>(null) }

    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN) counter.loadInvoices(reset = true)
    }

    voiding?.let { invoice ->
        TmConfirmDialog(
            title = "Void ${invoice.label}?",
            message = if (invoice.status == InvoiceStatus.PAID) "Its parts go back into stock. The ticket stays picked up and the money isn't refunded automatically."
                else "It can't be used again.",
            confirmText = "Void invoice",
            destructive = true,
            onConfirm = {
                counter.voidInvoice(invoice)
                voiding = null
            },
            onDismissRequest = { voiding = null }
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        TmPageHeader(
            title = "Invoices",
            subtitle = "Newest first, click one to open its ticket at the counter",
            onBack = null,
            actions = {
                TmButton(
                    text = "Refresh",
                    onClick = { counter.loadInvoices(reset = true) },
                    variant = TmButtonVariant.SECONDARY,
                    leadingIcon = Res.drawable.refresh_cw
                )
            }
        )

        TmSurface(Modifier.fillMaxSize()) {
            if (invoices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TmEmptyState(
                        icon = Res.drawable.file_text,
                        title = if (loginState == LoginState.LOGGED_IN) "No invoices yet" else "Log in to see invoices",
                        message = if (loginState == LoginState.LOGGED_IN) "Invoices are made when a deposit is taken or a ticket is picked up." else null
                    )
                }
                return@TmSurface
            }

            Column(Modifier.padding(spacing.sm)) {
                TmTableHeader(COLUMNS)

                TmLazyColumn(Modifier.fillMaxSize()) {
                    items(invoices, key = { it.identifier }) { invoice ->
                        TmTableRow(COLUMNS, onClick = { onOpenTicket(invoice.ticketIdentifier) }) { column ->
                            when (column) {
                                0 -> TmTicketIdText(invoice.label)
                                1 -> Text(formatDateTime(invoice.createdAt), style = TmTheme.typography.body, color = colors.textMuted)
                                2 -> TmBadge(
                                    text = formatEnumName(invoice.status.name),
                                    tone = when (invoice.status) {
                                        InvoiceStatus.PAID -> colors.success
                                        InvoiceStatus.VOID -> colors.danger
                                        InvoiceStatus.OPEN -> colors.warning
                                    }
                                )
                                3 -> TmMoneyText(invoice.totalCents)
                                4 -> TmMoneyText(invoice.paidCents, color = colors.textMuted)
                                5 -> TmMoneyText(invoice.balanceCents, color = if (invoice.balanceCents > 0) colors.danger.color else colors.text)
                                6 -> if (isAdmin && invoice.status != InvoiceStatus.VOID) {
                                    TmButton(text = "Void", onClick = { voiding = invoice }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                }
                            }
                        }
                    }

                    item {
                        Box(Modifier.fillMaxWidth().padding(spacing.md), contentAlignment = Alignment.Center) {
                            if (endReached) Text("That's all of them", style = TmTheme.typography.caption, color = colors.textFaint)
                            else TmButton(text = "Load older invoices", onClick = { counter.loadInvoices(reset = false) }, variant = TmButtonVariant.SECONDARY)
                        }
                    }
                }
            }
        }
    }
}
