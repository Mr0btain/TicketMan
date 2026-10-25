package org.mr0btain.ticketman.ui.screens.admin.invoices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.invoice.ReturnedPart
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import kotlin.uuid.Uuid

/**
 * Money back on a paid invoice, with the parts coming back, if any. Starts with everything still paid and no parts
 * @param onRefund Sends it, calls back with what went wrong or null when it's done
 */
@Composable
fun RefundDialog(
    invoice: Invoice,
    onRefund: (RefundRequest, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    val spacing = TmTheme.spacing

    var amountCents by remember { mutableLongStateOf(invoice.netPaidCents) }
    var method by remember { mutableStateOf(invoice.payments.lastOrNull { !it.isRefund }?.method ?: PaymentMethod.CASH) }
    var reason by remember { mutableStateOf("") }
    val returning = remember { mutableStateMapOf<Uuid, String>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Each part sold with how many can still come back
    val sold = invoice.partsSold
    val returned = invoice.returnedParts
    val returnable = sold.mapNotNull { (item, count) ->
        val left = count - (returned[item] ?: 0)
        val name = invoice.charges.first { it.inventoryIdentifier == item }.description
        if (left > 0) Triple(item, name, left) else null
    }

    val returns = returnable.mapNotNull { (item, _, _) -> returning[item]?.toIntOrNull()?.takeIf { it > 0 }?.let { ReturnedPart(item, it) } }
    val problem = when {
        reason.isBlank() -> "Give a reason"
        amountCents > invoice.netPaidCents -> "Only ${formatMoney(invoice.netPaidCents)} was paid"
        amountCents == 0L && returns.isEmpty() -> "Refund an amount or return a part"
        returnable.any { (item, _, left) -> returning[item]?.let { text -> text.isNotBlank() && (text.toIntOrNull()?.let { it in 0..left } != true) } == true } ->
            "Return a whole number of each part, up to how many are left"
        else -> null
    }

    TmDialog(
        title = "Refund ${invoice.label}",
        description = "Money goes back to the customer and returned parts go back in stock. The invoice stays paid.",
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = if (amountCents > 0) "Refund ${formatMoney(amountCents)}" else "Return parts",
                confirmEnabled = problem == null,
                loading = busy,
                confirmVariant = TmButtonVariant.DANGER,
                onCancel = onDismissRequest,
                onConfirm = {
                    busy = true
                    error = null
                    onRefund(RefundRequest(method, amountCents, reason.trim(), returns)) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        Column {
            TmKeyValueRow(label = "Paid") { TmMoneyText(invoice.paidCents) }
            if (invoice.refundedCents > 0) TmKeyValueRow(label = "Already refunded") { TmMoneyText(invoice.refundedCents) }
        }

        TmMoneyField(label = "Refund", cents = amountCents, onCentsChange = { amountCents = it })

        TmSegmented(
            label = "Given back as",
            options = PaymentMethod.entries,
            selected = method,
            onSelect = { method = it },
            optionLabel = { formatEnumName(it.name) }
        )

        TmTextField(value = reason, onValueChange = { reason = it }, label = "Reason", placeholder = "Didn't fit, faulty, changed their mind")

        if (returnable.isNotEmpty()) {
            Text(text = "Parts coming back", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)

            returnable.forEach { (item, name, left) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(text = name, style = TmTheme.typography.body, color = TmTheme.colors.text)
                        Text(text = "$left can come back", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                    }
                    TmTextField(
                        modifier = Modifier.width(80.dp),
                        value = returning[item].orEmpty(),
                        onValueChange = { returning[item] = it.filter(Char::isDigit) },
                        placeholder = "0",
                        textStyle = TmTheme.typography.numeric
                    )
                }
            }
        }

        (error ?: problem)?.let { TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.INFO) }
    }
}
