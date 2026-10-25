package org.mr0btain.ticketman.ui.screens.admin.timeoff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.schedule.ScheduleRules
import org.mr0btain.ticketman.data.schedule.TimeOffBalance
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmTextEditDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import org.mr0btain.ticketman.ui.screens.appointments.timeOffDays
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.calendar
import ticketman.composeapp.generated.resources.users

/**
 * Time off: requests to answer, waiting ones first, and each person's yearly allowance with what's taken, waiting
 * and left
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminTimeOffTab(
    requests: List<TimeOffRequest>,
    balances: List<TimeOffBalance>,
    onApprove: (TimeOffRequest) -> Unit,
    onDecline: (TimeOffRequest, String) -> Unit,
    onAllowance: (TimeOffBalance, Double) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var declining by remember { mutableStateOf<TimeOffRequest?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmPanel(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                title = "Requests",
                subtitle = "Approving puts the time off on the schedule and takes its hours from the allowance",
                icon = Res.drawable.calendar
            ) {
                if (requests.isEmpty()) TmEmptyState(title = "No time off asked for", message = "Staff ask from the calendar's Time off button")

                requests.sortedWith(compareByDescending<TimeOffRequest> { it.status == TimeOffStatus.PENDING }.thenByDescending { it.createdAt })
                    .take(RECENT)
                    .forEachIndexed { index, request ->
                        if (index > 0) TmDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = "${request.userName}, ${timeOffDays(request)}", style = TmTheme.typography.label, color = colors.text)
                                Text(
                                    text = listOfNotNull(
                                        ScheduleRules.hoursText(request.hours),
                                        request.note.ifBlank { null },
                                        "asked ${formatDateTime(request.createdAt)}",
                                        request.reason.ifBlank { null }?.let { "declined: $it" }
                                    ).joinToString(", "),
                                    style = TmTheme.typography.caption,
                                    color = colors.textMuted
                                )
                            }
                            TmBadge(text = request.status.label, tone = colors.timeOffTone(request.status), dot = false)
                            if (request.status == TimeOffStatus.PENDING) {
                                TmButton(text = "Decline", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { declining = request })
                                TmButton(text = "Approve", size = TmControlSize.SMALL, onClick = { onApprove(request) })
                            }
                        }
                    }
            }

            TmPanel(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                title = "Allowances",
                subtitle = "Hours of time off each person has a year, starting over on 1 January",
                icon = Res.drawable.users
            ) {
                if (balances.isEmpty()) TmEmptyState(title = "No staff yet")
                balances.forEachIndexed { index, balance ->
                    if (index > 0) TmDivider()
                    AllowanceRow(balance, onAllowance)
                }
            }
        }
    }

    TmTextEditDialog(
        label = "Why it's declined",
        value = "",
        visible = declining != null,
        onConfirm = { reason ->
            declining?.let { onDecline(it, reason.trim()) }
            declining = null
            true
        },
        onCancel = { declining = null },
        keyboardType = KeyboardType.Text,
        hint = "They see this with their request. Leave it empty to say nothing"
    )
}

@Composable
private fun AllowanceRow(balance: TimeOffBalance, onAllowance: (TimeOffBalance, Double) -> Unit) {
    val spacing = TmTheme.spacing
    var hours by remember(balance) { mutableStateOf(balance.allowanceHours.toString().removeSuffix(".0")) }
    val typed = hours.trim().toDoubleOrNull()?.takeIf { it >= 0 }

    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = balance.userName, style = TmTheme.typography.label, color = TmTheme.colors.text)
            Text(
                text = "${ScheduleRules.hoursText(balance.takenHours)} taken, ${ScheduleRules.hoursText(balance.pendingHours)} waiting, " +
                    "${ScheduleRules.hoursText(balance.leftHours)} left in ${balance.year}",
                style = TmTheme.typography.caption,
                color = TmTheme.colors.textMuted
            )
        }
        TmTextField(
            modifier = Modifier.width(96.dp),
            value = hours,
            onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
            size = TmControlSize.SMALL,
            textStyle = TmTheme.typography.numeric,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            error = if (typed == null) "" else null
        )
        TmButton(
            text = "Save",
            variant = TmButtonVariant.SECONDARY,
            size = TmControlSize.SMALL,
            enabled = typed != null && typed != balance.allowanceHours,
            onClick = { typed?.let { onAllowance(balance, it) } }
        )
    }
}

private fun TmColors.timeOffTone(status: TimeOffStatus): TmTone = when (status) {
    TimeOffStatus.PENDING -> warning
    TimeOffStatus.APPROVED -> success
    TimeOffStatus.DECLINED -> danger
    TimeOffStatus.WITHDRAWN -> neutral
}

private const val RECENT = 60
