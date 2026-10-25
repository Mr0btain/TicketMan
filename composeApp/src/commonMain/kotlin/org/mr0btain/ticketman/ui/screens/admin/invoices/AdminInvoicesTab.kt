package org.mr0btain.ticketman.ui.screens.admin.invoices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.MainViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.refresh_cw
import ticketman.composeapp.generated.resources.download
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Invoice", width = 172.dp),
    TmColumn("Created", weight = 1f),
    TmColumn("Status", width = 96.dp),
    TmColumn("Total", width = 104.dp, end = true),
    TmColumn("Paid", width = 104.dp, end = true),
    TmColumn("Balance", width = 104.dp, end = true),
    TmColumn("", width = 64.dp, end = true),
    TmColumn("", width = 144.dp, end = true)
)

/**
 * Every invoice, newest first. Clicking one opens its ticket at the counter, and any that isn't void can be voided.
 * One kept after its ticket was deleted says so, and can be deleted
 * @param onOpenTicket Shows the invoice's ticket on the counter screen
 */
@Composable
fun AdminInvoicesTab(
    viewModel: MainViewModel,
    onOpenTicket: (Uuid) -> Unit
) {
    val counter = viewModel.counter
    val invoices by counter.invoices.list.collectAsState()
    val endReached by counter.invoices.endReached.collectAsState()
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    var voiding by remember { mutableStateOf<Invoice?>(null) }
    var deleting by remember { mutableStateOf<Invoice?>(null) }
    var exporting by remember { mutableStateOf(false) }

    if (exporting) {
        InvoiceExportDialog(
            onExport = { from, to, onResult -> counter.invoices.export(from, to, onResult) },
            onDismissRequest = { exporting = false }
        )
    }

    LaunchedEffect(Unit) {
        counter.invoices.load(reset = true)
    }

    voiding?.let { invoice ->
        TmConfirmDialog(
            title = "Void ${invoice.label}?",
            message = if (invoice.status == InvoiceStatus.PAID) "Its parts go back into stock. The ticket stays picked up and the money isn't refunded automatically."
                else "It can't be used again.",
            confirmText = "Void invoice",
            destructive = true,
            onConfirm = {
                counter.invoices.void(invoice)
                voiding = null
            },
            onDismissRequest = { voiding = null }
        )
    }

    deleting?.let { invoice ->
        TmConfirmDialog(
            title = "Delete ${invoice.label}?",
            message = "Its ticket ${invoice.ticketLabel} was deleted, and this was kept because money was taken or it " +
                "was voided. Deleting it takes it, and the payments on it, out of the books and the end of day " +
                "report for good." + if (invoice.status == InvoiceStatus.PAID) " Parts it took stay out of stock; void it first to put them back." else "",
            confirmText = "Delete invoice",
            destructive = true,
            onConfirm = {
                counter.invoices.remove(invoice)
                deleting = null
            },
            onDismissRequest = { deleting = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TmPanel(
            title = "Invoices",
            subtitle = "Newest first, click one to open its ticket at the counter",
            icon = Res.drawable.file_text,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TmButton(
                        text = "Export CSV",
                        onClick = { exporting = true },
                        variant = TmButtonVariant.SECONDARY,
                        leadingIcon = Res.drawable.download
                    )
                    TmButton(
                        text = "Refresh",
                        onClick = { counter.invoices.load(reset = true) },
                        variant = TmButtonVariant.SECONDARY,
                        leadingIcon = Res.drawable.refresh_cw
                    )
                }
            }
        ) {
            if (invoices.isEmpty()) {
                TmEmptyState(
                    icon = Res.drawable.file_text,
                    title = "No invoices yet",
                    message = "Invoices are made when a deposit is taken or a ticket is picked up."
                )
                return@TmPanel
            }

            Column {
                TmTableHeader(COLUMNS)

                invoices.forEach { invoice ->
                    // A deleted ticket, or a sale, has nothing to open
                    val ticket = invoice.ticketIdentifier?.takeIf { !invoice.ticketRemoved }
                    TmTableRow(COLUMNS, onClick = ticket?.let { { onOpenTicket(it) } }) { column ->
                        when (column) {
                            0 -> Column {
                                TmTicketIdText(invoice.label)
                                if (invoice.ticketRemoved) {
                                    Text("Ticket ${invoice.ticketLabel} deleted", style = TmTheme.typography.caption, color = colors.textMuted)
                                } else if (invoice.isSale) {
                                    Text("Counter sale", style = TmTheme.typography.caption, color = colors.textMuted)
                                }
                            }
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
                            6 -> TmButton(text = "PDF", onClick = { counter.printer.savePdf(invoice) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                            7 -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                if (invoice.status != InvoiceStatus.VOID) {
                                    TmButton(text = "Void", onClick = { voiding = invoice }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                }
                                if (invoice.ticketRemoved) {
                                    TmButton(text = "Delete", onClick = { deleting = invoice }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().padding(spacing.md), contentAlignment = Alignment.Center) {
                    if (endReached) Text("That's all of them", style = TmTheme.typography.caption, color = colors.textFaint)
                    else TmButton(text = "Load older invoices", onClick = { counter.invoices.load(reset = false) }, variant = TmButtonVariant.SECONDARY)
                }
            }
        }
    }
}
