package org.mr0btain.ticketman.ui.screens.admin.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * Each user's commission on the labor they bill, as a percent. Blank is none
 * @param onSave Gets every rate that's more than nothing
 */
@Composable
fun CommissionDialog(
    users: List<User>,
    rates: List<CommissionRate>,
    saving: Boolean,
    onSave: (List<CommissionRate>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val saved = remember(rates) { rates.associate { it.userIdentifier to it.basisPoints } }
    val shown = remember(users, saved) { users.filter { it.status != UserStatus.DEACTIVATED || it.identifier in saved } }
    val typed = remember(shown) {
        mutableStateMapOf<Uuid, String>().apply {
            shown.forEach { user -> put(user.identifier, saved[user.identifier]?.let { percentText(it) }.orEmpty()) }
        }
    }

    val parsed = shown.associate { it.identifier to basisPoints(typed[it.identifier].orEmpty()) }
    val valid = parsed.values.none { it == null }

    TmDialog(
        title = "Commission rates",
        description = "A percent of the labor each tech bills, worked out on the reports. Only admins see them.",
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = "Save",
                confirmEnabled = valid && shown.isNotEmpty(),
                loading = saving,
                onCancel = onDismissRequest,
                onConfirm = {
                    onSave(parsed.mapNotNull { (user, points) -> points?.takeIf { it > 0 }?.let { CommissionRate(user, it) } })
                }
            )
        }
    ) {
        if (shown.isEmpty()) TmNotice(text = "No users loaded", kind = TmNoticeKind.INFO)

        shown.forEach { user ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = user.name.ifBlank { user.username },
                    style = TmTheme.typography.body,
                    color = TmTheme.colors.text
                )
                TmTextField(
                    modifier = Modifier.width(120.dp),
                    value = typed[user.identifier].orEmpty(),
                    onValueChange = { typed[user.identifier] = it },
                    placeholder = "0",
                    trailing = { Text(text = "%", style = TmTheme.typography.body, color = TmTheme.colors.textMuted) },
                    error = if (parsed[user.identifier] == null) "0 to 100" else null,
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
    }
}

// Like 12.5 for 1250
private fun percentText(basisPoints: Int): String =
    if (basisPoints % 100 == 0) (basisPoints / 100).toString() else (basisPoints / 100.0).toString()

// Blank is none, null when it isn't a percent from 0 to 100 with two decimals at most
private fun basisPoints(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    val value = trimmed.toDoubleOrNull() ?: return null
    val points = (value * 100).roundToInt()
    return points.takeIf { it in 0..CommissionRate.MAX_BASIS_POINTS && kotlin.math.abs(value * 100 - points) < 1e-6 }
}
