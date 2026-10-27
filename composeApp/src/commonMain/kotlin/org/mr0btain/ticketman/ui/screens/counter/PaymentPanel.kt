package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.data.payment.CardExtras
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.invoice.InvoiceMath
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.credit_card
import ticketman.composeapp.generated.resources.x

/**
 * Totals with the tax on each rate, the payments already on the invoice and the ones being taken now, and the button that finishes. Shared by
 * pickup and counter sales
 * @param taken Payments already saved on the invoice, like a deposit
 * @param onRemoveTaken Takes one of [taken] off, null when they can't be
 * @param pending Payments taken now, sent with [onFinish]
 * @param problem Shown over the buttons, like what went wrong saving
 * @param creditCents The customer's store credit, offered as a way to pay when there's some. Null with no customer
 * @param onLookupGiftCard Finds a gift card by its code, null when gift cards can't be taken here
 * @param secondaryAction A button beside the finishing one, like saving the invoice
 * @param readers The shop's card readers, offered for card payments when [onReaderCharge] is set
 * @param onReaderCharge Sends an amount and a tip to a reader, null when there's no processor
 * @param tipPercents Tips to offer before sending to a reader, none when the shop doesn't take them
 * @param surchargePercent What the shop adds to card payments, blank for none
 * @param onPaymentLink Shows a payment link for the balance, null when links can't be made here
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentPanel(
    totals: InvoiceMath.Totals,
    taxLabel: String,
    taken: List<Payment>,
    onRemoveTaken: ((Payment) -> Unit)?,
    pending: SnapshotStateList<PaymentRequest>,
    problem: String?,
    finishText: String,
    finishEnabled: Boolean,
    busy: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    creditCents: Long? = null,
    onLookupGiftCard: (suspend (String) -> GiftCard?)? = null,
    readers: List<CardReader> = emptyList(),
    onReaderCharge: ((CardReader, Long, Long) -> Unit)? = null,
    tipPercents: List<Int> = emptyList(),
    surchargePercent: String = "",
    onPaymentLink: (() -> Unit)? = null,
    secondaryAction: @Composable RowScope.() -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    var paymentCents by remember { mutableLongStateOf(0L) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CARD) }

    val balance = paymentBalance(totals, taken, pending)

    TmPanel(modifier = modifier, title = "Payment", icon = Res.drawable.credit_card) {
        Column {
            TmKeyValueRow(label = "Subtotal") { TmMoneyText(totals.subtotalCents) }
            if (totals.discountCents > 0) TmKeyValueRow(label = "Discount") { TmMoneyText(-totals.discountCents, signed = true) }
            // One row per rate when there's more than one
            if (totals.taxLines.size > 1) {
                totals.taxLines.forEach { tax -> TmKeyValueRow(label = tax.label) { TmMoneyText(tax.taxCents) } }
            } else {
                TmKeyValueRow(label = totals.taxLines.singleOrNull()?.label ?: taxLabel) { TmMoneyText(totals.taxCents) }
            }
            TmKeyValueRow(label = "Total") { TmMoneyText(totals.totalCents, style = TmTheme.typography.numericLarge) }
        }

        if (taken.isNotEmpty() || pending.isNotEmpty()) {
            TmDivider()

            Column {
                // Deposits and anything else already taken
                taken.forEach { payment ->
                    PaymentRow(
                        amountCents = payment.amountCents,
                        method = payment.method,
                        detail = listOfNotNull(
                            payment.reference.ifBlank { null },
                            payment.note.ifBlank { null },
                            payment.tipCents.takeIf { it != 0L }?.let { "${formatMoney(it)} tip" },
                            payment.surchargeCents.takeIf { it != 0L }?.let { "${formatMoney(it)} surcharge" },
                            formatDateTime(payment.receivedAt)
                        ).joinToString(", "),
                        onRemove = onRemoveTaken?.let { { it(payment) } }
                    )
                }

                // Taken now, sent when finishing. One from the card reader is already taken, so it stays
                pending.forEach { payment ->
                    val onReader = payment.chargeIdentifier != null
                    PaymentRow(
                        amountCents = payment.amountCents,
                        method = payment.method,
                        detail = listOfNotNull(payment.reference.ifBlank { null }, if (onReader) "Paid on the reader" else "Taking now").joinToString(", "),
                        onRemove = if (onReader) null else ({ pending.remove(payment) })
                    )
                }
            }
        }

        TmDivider()

        // The number the person at the till is looking for
        TmKeyValueRow(label = if (balance >= 0) "Balance due" else "Change due") {
            TmMoneyText(
                cents = if (balance >= 0) balance else -balance,
                style = TmTheme.typography.numericDisplay,
                color = if (balance > 0) colors.danger.color else colors.success.color
            )
        }

        // Store credit only when they have some, gift cards only where they're taken
        val creditLeft = (creditCents ?: 0) - pending.filter { it.method == PaymentMethod.STORE_CREDIT }.sumOf { it.amountCents }
        val methods = PaymentMethod.entries.filter { method ->
            when (method) {
                PaymentMethod.STORE_CREDIT -> creditLeft > 0 || paymentMethod == method
                PaymentMethod.GIFT_CARD -> onLookupGiftCard != null
                else -> true
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = "Paid by", style = TmTheme.typography.label, color = colors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                methods.forEach { method ->
                    TmFilterChip(text = methodLabel(method), selected = paymentMethod == method, onClick = { paymentMethod = method })
                }
            }
        }

        // A gift card is looked up first, so its balance is known before it's taken
        var cardCode by remember { mutableStateOf("") }
        var card by remember { mutableStateOf<GiftCard?>(null) }
        var cardProblem by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val cardLeft = card?.let { found -> found.balanceCents - pending.filter { it.method == PaymentMethod.GIFT_CARD && it.reference == found.code }.sumOf { it.amountCents } }

        if (paymentMethod == PaymentMethod.GIFT_CARD && onLookupGiftCard != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                TmTextField(
                    modifier = Modifier.weight(1f),
                    label = "Gift card code",
                    value = cardCode,
                    onValueChange = {
                        cardCode = it
                        card = null
                        cardProblem = null
                    },
                    placeholder = "Scan or type it",
                    textStyle = TmTheme.typography.numeric,
                    error = cardProblem
                )
                TmButton(
                    text = "Check",
                    enabled = cardCode.isNotBlank(),
                    variant = TmButtonVariant.SECONDARY,
                    onClick = {
                        scope.launch {
                            card = onLookupGiftCard(cardCode)
                            cardProblem = if (card == null) "There's no gift card with that code" else null
                        }
                    }
                )
            }
            cardLeft?.let { Text(text = "${formatMoney(it)} on ${card!!.code}", style = TmTheme.typography.caption, color = colors.textMuted) }
        }

        if (paymentMethod == PaymentMethod.STORE_CREDIT) {
            Text(text = "${formatMoney(creditLeft.coerceAtLeast(0))} of store credit to spend", style = TmTheme.typography.caption, color = colors.textMuted)
        }

        // Leaving it at 0.00 takes whatever is still owed, or what's left on the card or credit if that's less
        val limit = when (paymentMethod) {
            PaymentMethod.STORE_CREDIT -> creditLeft
            PaymentMethod.GIFT_CARD -> cardLeft ?: 0
            else -> null
        }
        val amount = (if (paymentCents == 0L) balance.takeIf { it > 0 }?.let { owed -> limit?.let { minOf(owed, it) } ?: owed } else paymentCents)
            ?.takeIf { it > 0 && (limit == null || it <= limit) }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
            TmMoneyField(
                modifier = Modifier.weight(1f),
                label = "Amount",
                cents = paymentCents,
                onCentsChange = { paymentCents = it }
            )

            TmButton(
                text = amount?.let { "Take ${formatMoney(it)}" } ?: "Take",
                enabled = amount != null,
                variant = TmButtonVariant.SECONDARY,
                onClick = {
                    amount?.let { pending.add(PaymentRequest(paymentMethod, it, reference = if (paymentMethod == PaymentMethod.GIFT_CARD) card?.code.orEmpty() else "")) }
                    paymentCents = 0
                }
            )
        }

        // The amount goes to a reader instead, and is only on the invoice once the card goes through
        var readerIdentifier by remember { mutableStateOf<String?>(null) }
        val reader = readers.firstOrNull { it.identifier == readerIdentifier } ?: readers.firstOrNull()
        if (paymentMethod == PaymentMethod.CARD && reader != null && onReaderCharge != null) {
            if (readers.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    readers.forEach { option ->
                        TmFilterChip(text = option.name, selected = option == reader, onClick = { readerIdentifier = option.identifier })
                    }
                }
            }

            // A tip, picked with the customer before it goes to the reader, and the shop's surcharge, both on top
            var tipPercent by remember { mutableStateOf(0) }
            if (tipPercents.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    TmFilterChip(text = "No tip", selected = tipPercent == 0, onClick = { tipPercent = 0 })
                    tipPercents.forEach { percent ->
                        TmFilterChip(
                            text = amount?.let { "$percent% (${formatMoney(CardExtras.tipCents(it, percent))})" } ?: "$percent%",
                            selected = tipPercent == percent,
                            onClick = { tipPercent = percent }
                        )
                    }
                }
            }
            val tip = amount?.let { CardExtras.tipCents(it, tipPercent.takeIf { percent -> percent in tipPercents } ?: 0) } ?: 0
            val surcharge = amount?.let { CardExtras.surchargeCents(it, surchargePercent) } ?: 0
            val charged = amount?.let { it + tip + surcharge }
            if (amount != null && (tip > 0 || surcharge > 0)) {
                Text(
                    text = listOfNotNull(
                        formatMoney(amount),
                        tip.takeIf { it > 0 }?.let { "${formatMoney(it)} tip" },
                        surcharge.takeIf { it > 0 }?.let { "${formatMoney(it)} card surcharge ($surchargePercent%)" }
                    ).joinToString(" + "),
                    style = TmTheme.typography.caption,
                    color = colors.textMuted
                )
            }
            TmButton(
                modifier = Modifier.fillMaxWidth(),
                text = charged?.let { "Charge ${formatMoney(it)} on ${reader.name}" } ?: "Charge on ${reader.name}",
                enabled = amount != null && !busy,
                onClick = {
                    amount?.let { onReaderCharge(reader, it, tip) }
                    paymentCents = 0
                    tipPercent = 0
                }
            )
        }

        if (balance > 0) {
            Text(
                text = "Leave it at 0.00 to take the full ${formatMoney(balance)}",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        }

        if (onPaymentLink != null && balance > 0) {
            TmButton(text = "Payment link for the customer", variant = TmButtonVariant.GHOST, enabled = !busy, onClick = onPaymentLink)
        }

        problem?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        TmDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            secondaryAction()

            TmButton(
                modifier = Modifier.weight(1f),
                text = finishText,
                enabled = finishEnabled && balance <= 0,
                loading = busy,
                size = TmControlSize.LARGE,
                onClick = onFinish
            )
        }
    }
}

/** What's still owed after [taken] and [pending], negative when change is due */
fun paymentBalance(totals: InvoiceMath.Totals, taken: List<Payment>, pending: List<PaymentRequest>): Long =
    totals.totalCents - taken.sumOf { it.amountCents } - pending.sumOf { it.amountCents }

/** How a payment method reads on screen */
fun methodLabel(method: PaymentMethod): String = formatEnumName(method.name)

@Composable
private fun PaymentRow(amountCents: Long, method: PaymentMethod, detail: String, onRemove: (() -> Unit)?) {
    TmListRow(
        title = methodLabel(method),
        subtitle = detail,
        leading = { TmMoneyText(amountCents, Modifier.width(96.dp)) },
        trailing = onRemove?.let { remove ->
            { TmIconButton(icon = Res.drawable.x, contentDescription = "Remove payment", onClick = remove, size = TmControlSize.SMALL) }
        }
    )
}
