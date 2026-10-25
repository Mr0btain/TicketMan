package org.mr0btain.ticketman.ui.screens.appointments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.mr0btain.ticketman.data.schedule.MyTimeOff
import org.mr0btain.ticketman.data.schedule.ScheduleRules
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffRules
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** "Mon Feb 2" or "Mon Feb 2 to Fri Feb 6", a request's days */
fun timeOffDays(request: TimeOffRequest): String {
    val first = AppointmentTimes.day(request.startsAt)
    val last = AppointmentTimes.day((request.endsAt - 1).coerceAtLeast(request.startsAt))
    return AppointmentTimes.dayLabel(first) + if (last != first) " to ${AppointmentTimes.dayLabel(last)}" else ""
}

/**
 * Someone's own time off: what's left of the year's allowance, asking for more, and their requests with a way to
 * take back one still waiting
 * @param mine Their balance and requests, null while it loads or when it couldn't
 * @param firstDay Where a new request starts, the day the calendar is on
 * @param onRequest Asks, calling back with what went wrong or null once it's asked
 */
@Composable
fun TimeOffDialog(
    mine: MyTimeOff?,
    firstDay: LocalDate,
    onRequest: (TimeOffRequest, (String?) -> Unit) -> Unit,
    onWithdraw: (TimeOffRequest) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var first by remember { mutableStateOf(firstDay.toString()) }
    var last by remember { mutableStateOf(firstDay.toString()) }
    var hoursTyped by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val from = runCatching { LocalDate.parse(first.trim()) }.getOrNull()
    val to = runCatching { LocalDate.parse(last.trim()) }.getOrNull()
    // Weekdays at a full day each, unless they typed their own
    val suggested = if (from != null && to != null && to >= from) TimeOffRules.defaultHours(from, to) else 0.0
    val hours = hoursTyped?.trim()?.toDoubleOrNull() ?: suggested
    val request = if (from != null && to != null) TimeOffRequest(
        startsAt = AppointmentTimes.toMillis(from, 0, 0),
        endsAt = AppointmentTimes.toMillis(to.plus(DatePeriod(days = 1)), 0, 0),
        hours = hours,
        note = note.trim()
    ) else null
    val left = mine?.balance?.leftHours
    val problem = when {
        from == null -> "Enter the first day like 2026-11-02"
        to == null -> "Enter the last day like 2026-11-06"
        else -> request?.problem() ?: left?.takeIf { hours > it }?.let { "Only ${ScheduleRules.hoursText(it)} left this year" }
    }

    TmDialog(
        title = "Time off",
        description = "Ask for days off. An admin approves them onto the schedule",
        onDismissRequest = onDismissRequest,
        maxWidth = 560.dp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TmButton(text = "Close", variant = TmButtonVariant.SECONDARY, onClick = onDismissRequest)
                TmButton(
                    text = "Ask for it",
                    enabled = problem == null && request != null && mine != null,
                    loading = saving,
                    onClick = {
                        val asked = request ?: return@TmButton
                        saving = true
                        error = null
                        onRequest(asked) { failed ->
                            saving = false
                            error = failed
                            if (failed == null) {
                                hoursTyped = null
                                note = ""
                            }
                        }
                    }
                )
            }
        }
    ) {
        mine?.balance?.let { balance ->
            Column {
                TmKeyValueRow(label = "Allowance for ${balance.year}", value = ScheduleRules.hoursText(balance.allowanceHours), numeric = true)
                TmKeyValueRow(label = "Taken", value = ScheduleRules.hoursText(balance.takenHours), numeric = true)
                TmKeyValueRow(label = "Waiting", value = ScheduleRules.hoursText(balance.pendingHours), numeric = true)
                TmKeyValueRow(label = "Left") { Text(text = ScheduleRules.hoursText(balance.leftHours), style = TmTheme.typography.numericLarge, color = colors.text) }
            }
            if (balance.allowanceHours <= 0) TmNotice(text = "You have no time off allowance yet. Ask an admin to set one.", kind = TmNoticeKind.INFO)
        } ?: TmNotice(text = "Couldn't load your time off. Check the connection and try again.", kind = TmNoticeKind.WARNING)

        TmDivider()

        TmFieldPair(
            { TmTextField(modifier = it, value = first, onValueChange = { v -> first = v; if (last < v) last = v }, label = "First day", placeholder = "2026-11-02") },
            { TmTextField(modifier = it, value = last, onValueChange = { v -> last = v }, label = "Last day", placeholder = "2026-11-06") }
        )
        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = hoursTyped ?: suggested.toString().removeSuffix(".0"),
                    onValueChange = { v -> hoursTyped = v.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = "Hours",
                    supportingText = "${TimeOffRules.HOURS_PER_DAY.toInt()} a weekday, fewer for part of a day",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            { TmTextField(modifier = it, value = note, onValueChange = { v -> note = v }, label = "Note", placeholder = "Vacation") }
        )

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
            ?: problem?.takeIf { from != null }?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }

        val requests = mine?.requests.orEmpty()
        if (requests.isNotEmpty()) {
            TmSectionLabel(text = "Your requests")
            requests.take(MY_REQUESTS).forEach { asked ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = timeOffDays(asked), style = TmTheme.typography.label, color = colors.text)
                        Text(
                            text = listOfNotNull(ScheduleRules.hoursText(asked.hours), asked.note.ifBlank { null }, asked.reason.ifBlank { null }?.let { "declined: $it" }).joinToString(", "),
                            style = TmTheme.typography.caption,
                            color = colors.textMuted
                        )
                    }
                    TmBadge(
                        text = asked.status.label,
                        tone = when (asked.status) {
                            TimeOffStatus.PENDING -> colors.warning
                            TimeOffStatus.APPROVED -> colors.success
                            TimeOffStatus.DECLINED -> colors.danger
                            TimeOffStatus.WITHDRAWN -> colors.neutral
                        },
                        dot = false
                    )
                    if (asked.status == TimeOffStatus.PENDING) {
                        TmButton(text = "Withdraw", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { onWithdraw(asked) })
                    }
                }
            }
        }
    }
}

private const val MY_REQUESTS = 10
