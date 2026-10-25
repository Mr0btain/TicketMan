package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.ui.kit.data.LocalTaxes
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.TicketDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.file_text

/** Narrower than this, invoice lines take two rows */
private val INVOICE_MIN_WIDTH = 560.dp


/**
 * Builds the invoice, takes payment and hands the ticket back
 * @param onFinish Saves the lines if they changed, takes [PaymentRequest]s and finishes, calls back with what went
 * wrong or null if it worked
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickupScreen(
    detail: TicketDetail,
    onSearchParts: suspend (String) -> List<Inventory>,
    onSave: (List<Charge>, Long, (String?) -> Unit) -> Unit,
    onRemovePayment: (Payment) -> Unit,
    onLookupGiftCard: suspend (String) -> GiftCard?,
    onFinish: (List<Charge>, Long, List<PaymentRequest>, (String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val ticket = detail.ticket
    val invoice = detail.invoice ?: return
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    // Rebuilt when the server sends back new lines, but not when only the payments changed so edits aren't lost
    val drafts = remember(invoice.identifier, invoice.charges) { mutableStateListOf<ChargeDraft>().apply { addAll(invoice.charges.map { ChargeDraft(it) }) } }
    var discountCents by remember(invoice.identifier, invoice.discountCents) { mutableLongStateOf(invoice.discountCents) }
    val pending = remember(ticket.identifier) { mutableStateListOf<PaymentRequest>() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val validCharges = drafts.mapNotNull { it.toCharge() }
    val problem = chargesProblem(drafts)

    // Preview with the same math the server uses
    // The rates the invoice kept, and the shop's for classes it hasn't used yet, like the server does
    val totals = InvoiceMath.totals(validCharges, discountCents, invoice.taxRateBasisPoints, TaxClasses.merged(invoice.taxRates, LocalTaxes.current.classes))
    val unsaved = problem == null && (validCharges != invoice.charges || discountCents != invoice.discountCents)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPageHeader(
            title = "Pickup",
            subtitle = listOfNotNull(invoice.label, detail.customer?.name).joinToString(", "),
            onBack = onBack,
            titleContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Pickup", style = TmTheme.typography.display, color = colors.text)
                    TmTicketIdText(number = ticket.label, style = TmTheme.typography.numericDisplay)
                }
            }
        )

        // Side by side when there's room. On narrow windows each panel takes the width and each line takes two rows
        BoxWithConstraints {
            val compact = maxWidth < INVOICE_MIN_WIDTH
            val paymentWidth = min(400.dp, maxWidth)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // ------------------------------------------ Lines ------------------------------------------
                TmPanel(
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = INVOICE_MIN_WIDTH, max = 760.dp),
                    title = "Invoice",
                    subtitle = if (drafts.isEmpty()) "No charges yet, add labor or parts below" else if (drafts.size == 1) "1 line" else "${drafts.size} lines",
                    icon = Res.drawable.file_text
                ) {
                    ChargeLines(drafts = drafts, compact = compact, laborCents = ticket.estimateCents ?: 0, onSearchParts = onSearchParts, giftCards = true)

                    TmMoneyField(
                        modifier = Modifier.width(200.dp),
                        label = "Discount",
                        cents = discountCents,
                        onCentsChange = { discountCents = it }
                    )
                }

                // ------------------------------------------ Totals and payment ------------------------------------------
                PaymentPanel(
                    modifier = Modifier.width(paymentWidth),
                    totals = totals,
                    taxLabel = "Tax, ${invoice.taxRateBasisPoints / 100.0}%",
                    taken = invoice.payments,
                    onRemoveTaken = onRemovePayment,
                    pending = pending,
                    problem = message ?: problem,
                    finishText = "Finish pickup",
                    finishEnabled = problem == null,
                    busy = busy,
                    creditCents = detail.customer?.creditCents,
                    onLookupGiftCard = onLookupGiftCard,
                    onFinish = {
                        busy = true
                        message = null
                        onFinish(validCharges, discountCents, pending.toList()) { failed ->
                            busy = false
                            message = failed
                        }
                    }
                ) {
                    TmButton(
                        text = "Save invoice",
                        enabled = unsaved && !busy,
                        variant = TmButtonVariant.GHOST,
                        size = TmControlSize.LARGE,
                        onClick = {
                            busy = true
                            message = null
                            onSave(validCharges, discountCents) { failed ->
                                busy = false
                                message = failed
                            }
                        }
                    )
                }
            }
        }
    }
}

/** How many of a part are left, in words */
fun stockText(stock: Int): String = when {
    stock == Int.MAX_VALUE -> "Stock not tracked"
    stock <= 0 -> "Out of stock"
    else -> "$stock in stock"
}

/**
 * Shown after a pickup or a sale with what was paid and any change to hand back
 * @param title The badge on top, "Picked up" or "Sold"
 * @param actions Room for more buttons, like printing the receipt
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickedUpView(
    invoice: Invoice,
    onDone: () -> Unit,
    title: String = "Picked up",
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically)
    ) {
        TmBadge(text = title, tone = colors.success)
        TmTicketIdText(number = invoice.label, style = TmTheme.typography.numericDisplay)
        Text(
            text = "Total ${formatMoney(invoice.totalCents)}, paid ${formatMoney(invoice.paidCents)}",
            style = TmTheme.typography.bodyLarge,
            color = colors.textMuted
        )

        if (invoice.balanceCents < 0) {
            Text(text = "Change due", style = TmTheme.typography.title, color = colors.text)
            TmMoneyText(
                cents = -invoice.balanceCents,
                style = TmTheme.typography.numericHero,
                color = colors.success.color
            )
        }

        Spacer(Modifier.height(spacing.lg))

        // Wraps on a phone, where the three buttons don't fit side by side
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            actions()
            TmButton(text = "Done", onClick = onDone, size = TmControlSize.LARGE)
        }
    }
}
