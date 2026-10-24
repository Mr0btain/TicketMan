package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.estimate.DecisionMethod
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.InvoiceMath
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Narrower than this, estimate lines take two rows */
private val ESTIMATE_MIN_WIDTH = 560.dp

/**
 * Builds an estimate to send the customer, starting from [start] (the last one, or nothing)
 * @param taxRateBasisPoints The shop's rate, for the preview. The server works it out again
 * @param canNotify Whether the shop sends the estimate ready message, so the box is worth showing
 */
@Composable
fun EstimateDialog(
    start: List<Charge>,
    laborCents: Long,
    taxRateBasisPoints: Int,
    canNotify: Boolean,
    onSearchParts: suspend (String) -> List<Inventory>,
    onSend: (lines: List<Charge>, note: String, notify: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val drafts = remember { mutableStateListOf<ChargeDraft>().apply { addAll(start.map { ChargeDraft(it) }) } }
    var note by remember { mutableStateOf("") }
    var notify by remember { mutableStateOf(true) }

    val problem = if (drafts.isEmpty()) "Add at least one line" else chargesProblem(drafts)
    val lines = drafts.mapNotNull { it.toCharge() }
    val totals = InvoiceMath.totals(lines, 0, taxRateBasisPoints)

    TmDialog(
        title = "New estimate",
        description = "The customer approves or declines it on their status page, or tells you. Approving puts these lines on the invoice. One still waiting is withdrawn.",
        onDismissRequest = onDismiss,
        maxWidth = 820.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = "Send estimate",
                confirmEnabled = problem == null,
                onConfirm = { onSend(lines, note.trim(), notify && canNotify) },
                onCancel = onDismiss
            )
        }
    ) {
        BoxWithConstraints {
            val compact = maxWidth < ESTIMATE_MIN_WIDTH
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                ChargeLines(drafts = drafts, compact = compact, laborCents = laborCents, onSearchParts = onSearchParts)
            }
        }

        Column {
            if (totals.taxCents > 0) TmKeyValueRow(label = "Tax, ${taxRateBasisPoints / 100.0}%") { TmMoneyText(totals.taxCents) }
            TmKeyValueRow(label = "Total") { TmMoneyText(totals.totalCents, style = TmTheme.typography.numericLarge) }
        }

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = note,
            onValueChange = { note = it },
            label = "Note for the customer",
            placeholder = "What you found and why",
            singleLine = false
        )

        if (canNotify) TmCheckbox(checked = notify, onCheckedChange = { notify = it }, label = "Tell the customer it's ready")

        if (drafts.isNotEmpty()) problem?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}

/**
 * Writes down the customer's answer to [estimate], given by phone or at the counter
 */
@Composable
fun EstimateAnswerDialog(
    estimate: Estimate,
    onRecord: (approved: Boolean, method: DecisionMethod, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var approved by remember { mutableStateOf(true) }
    var method by remember { mutableStateOf(DecisionMethod.PHONE) }
    var note by remember { mutableStateOf("") }

    TmDialog(
        title = "Customer's answer",
        description = "Approving puts the estimate's lines on the invoice. Who wrote it down and when is kept.",
        onDismissRequest = onDismiss,
        actions = {
            TmButtonRow(
                confirmText = if (approved) "Record approval" else "Record decline",
                onConfirm = { onRecord(approved, method, note.trim()) },
                onCancel = onDismiss
            )
        }
    ) {
        Row {
            Text(text = "Estimate total", style = TmTheme.typography.body, color = TmTheme.colors.textMuted, modifier = Modifier.weight(1f))
            TmMoneyText(estimate.totalCents, style = TmTheme.typography.numericLarge)
        }

        TmSegmented(
            label = "They",
            options = listOf(true, false),
            selected = approved,
            onSelect = { approved = it },
            optionLabel = { if (it) "Approved" else "Declined" }
        )

        TmDropdown(
            modifier = Modifier.fillMaxWidth(),
            label = "How",
            options = DecisionMethod.entries.filter { it != DecisionMethod.STATUS_PAGE },
            selected = method,
            onSelect = { method = it },
            optionLabel = { it.label }
        )

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = note,
            onValueChange = { note = it },
            label = "Note",
            placeholder = "Anything they said",
            singleLine = false
        )
    }
}
