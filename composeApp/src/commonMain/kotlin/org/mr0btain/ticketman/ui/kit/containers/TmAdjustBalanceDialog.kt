package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.runtime.*
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented

/**
 * Adds to or takes off a balance the shop holds, like store credit or a gift card, with a reason
 * @param balanceCents What's there now, which can't be taken below zero
 * @param onSave Gets the change (negative to take off) and the reason, calls back with what went wrong or null
 */
@Composable
fun TmAdjustBalanceDialog(
    title: String,
    balanceCents: Long,
    onSave: (Long, String, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    var adding by remember { mutableStateOf(true) }
    var amountCents by remember { mutableLongStateOf(0L) }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problem = when {
        amountCents <= 0 -> "Enter an amount"
        !adding && amountCents > balanceCents -> "Only ${formatMoney(balanceCents)} to take off"
        reason.isBlank() -> "Give a reason"
        else -> null
    }

    TmDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = if (adding) "Add ${formatMoney(amountCents)}" else "Take off ${formatMoney(amountCents)}",
                confirmEnabled = problem == null,
                loading = busy,
                onCancel = onDismissRequest,
                onConfirm = {
                    busy = true
                    error = null
                    onSave(if (adding) amountCents else -amountCents, reason.trim()) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        TmKeyValueRow(label = "Balance now") { TmMoneyText(balanceCents) }

        TmSegmented(options = listOf(true, false), selected = adding, onSelect = { adding = it }, optionLabel = { if (it) "Add" else "Take off" })
        TmMoneyField(label = "Amount", cents = amountCents, onCentsChange = { amountCents = it })
        TmTextField(value = reason, onValueChange = { reason = it }, label = "Reason", placeholder = "Goodwill, correction")

        (error ?: problem)?.let { TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.INFO) }
    }
}
