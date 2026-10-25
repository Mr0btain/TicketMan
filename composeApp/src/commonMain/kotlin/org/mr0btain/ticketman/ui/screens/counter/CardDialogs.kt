package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.data.TmQrCode
import org.mr0btain.ticketman.ui.kit.data.qrFits
import org.mr0btain.ticketman.ui.kit.feedback.TmLoading
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * An amount sent to a card reader: waiting for the card with a way to call it off, or why it didn't go through.
 * Closes on its own once it's paid
 */
@Composable
fun ReaderChargeDialog(
    charge: CardCharge,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    val waiting = charge.status == CardChargeStatus.PENDING

    TmDialog(
        title = if (waiting) "Waiting for the card" else "Card not taken",
        description = charge.readerName,
        onDismissRequest = { if (!waiting) onClose() },
        dismissOnOutsideClick = false,
        actions = {
            if (waiting) TmButton(text = "Cancel", variant = TmButtonVariant.SECONDARY, onClick = onCancel)
            else TmButton(text = "Close", onClick = onClose)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TmMoneyText(cents = charge.amountCents, style = TmTheme.typography.numericHero)
            chargeBreakdown(charge)?.let { Text(text = it, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted) }
            if (waiting) {
                TmLoading(text = "Tap, insert or swipe on the reader")
            }
        }

        if (!waiting) {
            TmNotice(
                text = charge.error.ifBlank { charge.status.label },
                kind = if (charge.status == CardChargeStatus.FAILED) TmNoticeKind.ERROR else TmNoticeKind.INFO
            )
        }
    }
}

/**
 * A payment link for the customer: a QR code to scan at the counter when it fits in one, and the address to send
 * them. It lands on the invoice once they've paid
 */
@Composable
fun PaymentLinkDialog(
    charge: CardCharge,
    onClose: () -> Unit
) {
    val colors = TmTheme.colors

    TmDialog(
        title = "Payment link",
        description = "${charge.invoiceLabel}. The payment shows on the invoice once it's paid",
        onDismissRequest = onClose,
        actions = { TmButton(text = "Done", onClick = onClose) }
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            TmMoneyText(cents = charge.amountCents, style = TmTheme.typography.numericLarge)
            chargeBreakdown(charge)?.let { Text(text = it, style = TmTheme.typography.caption, color = colors.textMuted) }
            if (qrFits(charge.url)) TmQrCode(text = charge.url, contentDescription = "QR code for the payment link")
        }

        SelectionContainer {
            Text(text = charge.url, style = TmTheme.typography.caption, color = colors.textMuted)
        }
    }
}

/** "$100.00 + $15.00 tip + $3.00 surcharge" when the charge has more than the invoice's amount, null otherwise */
@Composable
private fun chargeBreakdown(charge: CardCharge): String? {
    if (charge.tipCents == 0L && charge.surchargeCents == 0L) return null
    return listOfNotNull(
        formatMoney(charge.appliedCents),
        charge.tipCents.takeIf { it != 0L }?.let { "${formatMoney(it)} tip" },
        charge.surchargeCents.takeIf { it != 0L }?.let { "${formatMoney(it)} surcharge" }
    ).joinToString(" + ")
}
