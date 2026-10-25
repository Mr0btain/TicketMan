package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.invoice.InvoiceMath
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
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
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.credit_card
import ticketman.composeapp.generated.resources.x

/**
 * Totals, the payments already on the invoice and the ones being taken now, and the button that finishes. Shared by
 * pickup and counter sales
 * @param taken Payments already saved on the invoice, like a deposit
 * @param onRemoveTaken Takes one of [taken] off, null when they can't be
 * @param pending Payments taken now, sent with [onFinish]
 * @param problem Shown over the buttons, like what went wrong saving
 * @param secondaryAction A button beside the finishing one, like saving the invoice
 */
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
            TmKeyValueRow(label = taxLabel) { TmMoneyText(totals.taxCents) }
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
                        detail = listOfNotNull(payment.note.ifBlank { null }, formatDateTime(payment.receivedAt)).joinToString(", "),
                        onRemove = onRemoveTaken?.let { { it(payment) } }
                    )
                }

                // Taken now, sent when finishing
                pending.forEach { payment ->
                    PaymentRow(
                        amountCents = payment.amountCents,
                        method = payment.method,
                        detail = "Taking now",
                        onRemove = { pending.remove(payment) }
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

        TmSegmented(
            label = "Paid by",
            options = PaymentMethod.entries,
            selected = paymentMethod,
            onSelect = { paymentMethod = it },
            optionLabel = { formatEnumName(it.name) }
        )

        // Leaving it at 0.00 takes whatever is still owed
        val amount = if (paymentCents == 0L) balance.takeIf { it > 0 } else paymentCents

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
                    amount?.let { pending.add(PaymentRequest(paymentMethod, it)) }
                    paymentCents = 0
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

@Composable
private fun PaymentRow(amountCents: Long, method: PaymentMethod, detail: String, onRemove: (() -> Unit)?) {
    TmListRow(
        title = formatEnumName(method.name),
        subtitle = detail,
        leading = { TmMoneyText(amountCents, Modifier.width(96.dp)) },
        trailing = onRemove?.let { remove ->
            { TmIconButton(icon = Res.drawable.x, contentDescription = "Remove payment", onClick = remove, size = TmControlSize.SMALL) }
        }
    )
}
