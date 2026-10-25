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
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.selection.TmMenu
import org.mr0btain.ticketman.ui.kit.selection.TmMenuItem
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.MainViewModel
import ticketman.composeapp.generated.resources.*
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Invoice", width = 172.dp),
    TmColumn("Created", weight = 1f),
    TmColumn("Status", width = 96.dp),
    TmColumn("Total", width = 104.dp, end = true),
    TmColumn("Paid", width = 104.dp, end = true),
    TmColumn("Balance", width = 104.dp, end = true),
    TmColumn("", width = 64.dp, end = true),
    TmColumn("", width = 216.dp, end = true)
)

/**
 * Every invoice, newest first. Clicking one opens its ticket at the counter, a paid one can be refunded and any
 * that isn't void can be voided.
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
    val giftCards by counter.invoices.giftCards.collectAsState()
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    var voiding by remember { mutableStateOf<Invoice?>(null) }
    var deleting by remember { mutableStateOf<Invoice?>(null) }
    var refunding by remember { mutableStateOf<Invoice?>(null) }
    var exporting by remember { mutableStateOf(false) }

    if (exporting) {
        InvoiceExportDialog(
            onExport = { from, to, format, onResult -> counter.invoices.export(from, to, format, onResult) },
            onDismissRequest = { exporting = false }
        )
    }

    LaunchedEffect(Unit) {
        counter.invoices.load(reset = true)
        counter.invoices.loadGiftCards()
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

    refunding?.let { invoice ->
        RefundDialog(
            invoice = invoice,
            onRefund = { request, onResult -> counter.invoices.refund(invoice, request, onResult) },
            onDismissRequest = { refunding = null }
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

    InvoiceList(
        invoices = invoices,
        endReached = endReached,
        giftCards = giftCards,
        onOpenTicket = onOpenTicket,
        onExport = { exporting = true },
        onPdf = { counter.printer.savePdf(it) },
        onRefund = { refunding = it },
        onVoid = { voiding = it },
        onDelete = { deleting = it },
        onLoadMore = { counter.invoices.load(reset = false) },
        onAdjustGiftCard = { card, amount, reason, onResult -> counter.invoices.adjustGiftCard(card, amount, reason, onResult) }
    )
}

/** Narrower than this, each invoice is a compact row with its actions in a menu instead of the table */
private val TABLE_WIDTH = 860.dp

/**
 * The invoice list and the gift cards, without the dialogs, which [AdminInvoicesTab] owns
 * @param onOpenTicket Shows an invoice's ticket at the counter. Not offered for a sale or a deleted ticket
 */
@Composable
internal fun InvoiceList(
    invoices: List<Invoice>,
    endReached: Boolean,
    giftCards: List<GiftCard>,
    onOpenTicket: (Uuid) -> Unit,
    onExport: () -> Unit,
    onPdf: (Invoice) -> Unit,
    onRefund: (Invoice) -> Unit,
    onVoid: (Invoice) -> Unit,
    onDelete: (Invoice) -> Unit,
    onLoadMore: () -> Unit,
    onAdjustGiftCard: (GiftCard, Long, String, (String?) -> Unit) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TABLE_WIDTH

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            TmPanel(
                title = "Invoices",
                subtitle = if (wide) "Newest first, click one to open its ticket at the counter" else "Newest first",
                icon = Res.drawable.file_text,
                actions = {
                    if (wide) {
                        TmButton(text = "Export CSV", onClick = onExport, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.download)
                    } else {
                        TmIconButton(icon = Res.drawable.download, contentDescription = "Export CSV", onClick = onExport)
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
                    if (wide) TmTableHeader(COLUMNS)

                    invoices.forEach { invoice ->
                        // A deleted ticket, or a sale, has nothing to open
                        val ticket = invoice.ticketIdentifier?.takeIf { !invoice.ticketRemoved }
                        val open = ticket?.let { { onOpenTicket(it) } }

                        if (wide) {
                            TmTableRow(COLUMNS, onClick = open) { column ->
                                when (column) {
                                    0 -> InvoiceLabel(invoice)
                                    1 -> Text(formatDateTime(invoice.createdAt), style = TmTheme.typography.body, color = colors.textMuted)
                                    2 -> StatusBadge(invoice)
                                    3 -> TmMoneyText(invoice.totalCents)
                                    4 -> Column(horizontalAlignment = Alignment.End) {
                                        TmMoneyText(invoice.paidCents, color = colors.textMuted)
                                        if (invoice.refundedCents > 0) {
                                            Text("${formatMoney(invoice.refundedCents)} back", style = TmTheme.typography.caption, color = colors.warning.color)
                                        }
                                    }
                                    5 -> TmMoneyText(invoice.balanceCents, color = if (invoice.balanceCents > 0) colors.danger.color else colors.text)
                                    6 -> TmButton(text = "PDF", onClick = { onPdf(invoice) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                    7 -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                        if (invoice.status == InvoiceStatus.PAID) {
                                            TmButton(text = "Refund", onClick = { onRefund(invoice) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                        }
                                        if (invoice.status != InvoiceStatus.VOID) {
                                            TmButton(text = "Void", onClick = { onVoid(invoice) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                        }
                                        if (invoice.ticketRemoved) {
                                            TmButton(text = "Delete", onClick = { onDelete(invoice) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                        }
                                    }
                                }
                            }
                        } else {
                            CompactInvoiceRow(invoice, open, onPdf, onRefund, onVoid, onDelete)
                        }
                    }

                    Box(Modifier.fillMaxWidth().padding(spacing.md), contentAlignment = Alignment.Center) {
                        if (endReached) Text("That's all of them", style = TmTheme.typography.caption, color = colors.textFaint)
                        else TmButton(text = "Load older invoices", onClick = onLoadMore, variant = TmButtonVariant.SECONDARY)
                    }
                }
            }

            GiftCardsPanel(cards = giftCards, onAdjust = onAdjustGiftCard)
        }
    }
}

/** The number, and under it whether it was a counter sale or its ticket was deleted */
@Composable
private fun InvoiceLabel(invoice: Invoice) {
    val colors = TmTheme.colors
    Column {
        TmTicketIdText(invoice.label)
        if (invoice.ticketRemoved) {
            Text("Ticket ${invoice.ticketLabel} deleted", style = TmTheme.typography.caption, color = colors.textMuted)
        } else if (invoice.isSale) {
            Text("Counter sale", style = TmTheme.typography.caption, color = colors.textMuted)
        }
    }
}

@Composable
private fun StatusBadge(invoice: Invoice) {
    val colors = TmTheme.colors
    TmBadge(
        text = formatEnumName(invoice.status.name),
        tone = when (invoice.status) {
            InvoiceStatus.PAID -> colors.success
            InvoiceStatus.VOID -> colors.danger
            InvoiceStatus.OPEN -> colors.warning
        }
    )
}

/** One invoice on a narrow window: number, status and total on top, the date and what's paid under, actions in a menu */
@Composable
private fun CompactInvoiceRow(
    invoice: Invoice,
    onOpen: (() -> Unit)?,
    onPdf: (Invoice) -> Unit,
    onRefund: (Invoice) -> Unit,
    onVoid: (Invoice) -> Unit,
    onDelete: (Invoice) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }

    TmListRow(
        title = invoice.label,
        subtitle = listOfNotNull(
            when {
                invoice.ticketRemoved -> "Ticket ${invoice.ticketLabel} deleted"
                invoice.isSale -> "Counter sale"
                else -> null
            },
            formatDateTime(invoice.createdAt),
            if (invoice.balanceCents > 0 && invoice.status == InvoiceStatus.OPEN) "${formatMoney(invoice.balanceCents)} owed" else null,
            if (invoice.refundedCents > 0) "${formatMoney(invoice.refundedCents)} back" else null
        ).joinToString(" · "),
        onClick = onOpen,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    TmMoneyText(invoice.totalCents, color = colors.text)
                    StatusBadge(invoice)
                }
                Box {
                    TmIconButton(icon = Res.drawable.more_vertical, contentDescription = "More for ${invoice.label}", onClick = { menuOpen = true })
                    TmMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun pick(action: (Invoice) -> Unit) {
                            menuOpen = false
                            action(invoice)
                        }
                        TmMenuItem(text = "Save PDF", leadingIcon = Res.drawable.download, onClick = { pick(onPdf) })
                        if (invoice.status == InvoiceStatus.PAID) TmMenuItem(text = "Refund", leadingIcon = Res.drawable.rotate_ccw, onClick = { pick(onRefund) })
                        if (invoice.status != InvoiceStatus.VOID) TmMenuItem(text = "Void", leadingIcon = Res.drawable.x_circle, onClick = { pick(onVoid) })
                        if (invoice.ticketRemoved) TmMenuItem(text = "Delete", leadingIcon = Res.drawable.trash_2, onClick = { pick(onDelete) })
                    }
                }
            }
        }
    )
}
