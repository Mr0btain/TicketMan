package org.mr0btain.ticketman.ui.screens.appointments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.schedule.ScheduleRepeat
import org.mr0btain.ticketman.data.schedule.ScheduleRules
import org.mr0btain.ticketman.data.schedule.ScheduleScope
import org.mr0btain.ticketman.data.schedule.TimeOffRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import kotlin.uuid.Uuid

/** Weekdays for a repeat, ISO numbers with their short names */
private val WEEKDAYS = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

/**
 * Writes someone onto the staff schedule, or changes or takes off an entry: who, what kind, when, a note, and for a
 * new timed entry a weekly repeat. An entry in a series asks how much of it the change goes to
 * @param entry The entry, a new one when [isNew]
 * @param staff Who can be scheduled
 * @param onSave Gets the entry and a repeat for a new one, and the scope for a change; calls back with what went
 * wrong or null once saved
 * @param onRemove Takes it off with the scope picked, null for a new one
 */
@Composable
fun ScheduleEntryDialog(
    entry: ScheduleEntry,
    isNew: Boolean,
    staff: List<User>,
    onSave: (ScheduleEntry, ScheduleRepeat?, ScheduleScope, (String?) -> Unit) -> Unit,
    onRemove: ((ScheduleScope, (String?) -> Unit) -> Unit)?,
    onDismissRequest: () -> Unit
) {
    val spacing = TmTheme.spacing
    val firstDay = AppointmentTimes.day(entry.startsAt)
    val lastDay = AppointmentTimes.day((entry.endsAt - 1).coerceAtLeast(entry.startsAt))

    var person by remember { mutableStateOf(entry.userIdentifier) }
    var kind by remember { mutableStateOf(entry.kind) }
    var day by remember { mutableStateOf(firstDay.toString()) }
    var lastDayText by remember { mutableStateOf(lastDay.toString()) }
    var from by remember { mutableStateOf(formatTime(entry.startsAt)) }
    var to by remember { mutableStateOf(formatTime(entry.endsAt)) }
    var note by remember { mutableStateOf(entry.note) }
    // Time off's hours against the allowance, worked out from the days until typed
    var hoursOff by remember { mutableStateOf(if (isNew || entry.kind != ScheduleKind.TIME_OFF) null else entry.timeOffHours.toString().removeSuffix(".0")) }
    var repeats by remember { mutableStateOf(false) }
    var weekdays by remember { mutableStateOf(setOf(firstDay.dayOfWeek.ordinal + 1)) }
    var until by remember { mutableStateOf(firstDay.plus(DatePeriod(months = 1)).toString()) }
    var scope by remember { mutableStateOf(ScheduleScope.ONE) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val date = runCatching { LocalDate.parse(day.trim()) }.getOrNull()
    val last = runCatching { LocalDate.parse(lastDayText.trim()) }.getOrNull()
    val start = AppointmentTimes.parseTime(from)
    val end = AppointmentTimes.parseTime(to)

    // Times on the day, a shift ending at or before it starts running past midnight
    val result: ScheduleEntry? = when {
        date == null -> null
        kind.allDay -> last?.let {
            entry.copy(
                userIdentifier = person, kind = kind, allDay = true, note = note.trim(),
                timeOffHours = hoursOff?.trim()?.toDoubleOrNull() ?: TimeOffRules.defaultHours(date, it),
                startsAt = AppointmentTimes.toMillis(date, 0, 0),
                endsAt = AppointmentTimes.toMillis(it.plus(DatePeriod(days = 1)), 0, 0)
            )
        }
        start == null || end == null -> null
        else -> {
            val startsAt = AppointmentTimes.toMillis(date, start.first, start.second)
            var endsAt = AppointmentTimes.toMillis(date, end.first, end.second)
            if (endsAt <= startsAt) endsAt = AppointmentTimes.toMillis(date.plus(DatePeriod(days = 1)), end.first, end.second)
            entry.copy(userIdentifier = person, kind = kind, allDay = false, note = note.trim(), startsAt = startsAt, endsAt = endsAt, timeOffHours = 0.0)
        }
    }
    val repeat = if (isNew && repeats && !kind.allDay) ScheduleRepeat(weekdays.sorted(), until.trim()) else null
    val problem = when {
        date == null -> "Enter the day like 2026-10-27"
        kind.allDay && last == null -> "Enter the last day like 2026-10-30"
        !kind.allDay && (start == null || end == null) -> "Enter the times like 9:00 and 17:30"
        repeat != null && weekdays.isEmpty() -> "Pick the days it repeats on"
        repeat != null && runCatching { LocalDate.parse(until.trim()) }.getOrNull() == null -> "Enter the last day it repeats like 2026-12-31"
        else -> result?.problem()
    }

    TmDialog(
        title = if (isNew) "Add to the schedule" else "Schedule entry",
        onDismissRequest = onDismissRequest,
        maxWidth = 560.dp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                onRemove?.let { remove ->
                    TmButton(text = "Delete", variant = TmButtonVariant.DANGER, enabled = !saving, onClick = {
                        saving = true
                        remove(scope) { failed ->
                            saving = false
                            error = failed
                        }
                    })
                    Spacer(Modifier.weight(1f))
                }
                TmButton(text = "Cancel", variant = TmButtonVariant.SECONDARY, onClick = onDismissRequest)
                TmButton(
                    text = if (isNew) "Add" else "Save",
                    enabled = problem == null && result != null,
                    loading = saving,
                    onClick = {
                        val saved = result ?: return@TmButton
                        saving = true
                        error = null
                        onSave(saved, repeat, scope) { failed ->
                            saving = false
                            error = failed
                        }
                    }
                )
            }
        }
    ) {
        TmDropdown(
            modifier = Modifier.fillMaxWidth(),
            label = "Who",
            options = staff,
            selected = staff.firstOrNull { it.identifier == person },
            onSelect = { person = it.identifier },
            optionLabel = { it.name.ifBlank { it.username } },
            placeholder = entry.userName.ifBlank { "Pick someone" }
        )

        TmSegmented(label = "What", options = ScheduleKind.entries, selected = kind, onSelect = { kind = it }, optionLabel = { it.label })

        if (kind.allDay) {
            TmFieldPair(
                { TmTextField(modifier = it, value = day, onValueChange = { v -> day = v }, label = "First day", placeholder = "2026-10-27") },
                { TmTextField(modifier = it, value = lastDayText, onValueChange = { v -> lastDayText = v }, label = "Last day", placeholder = "2026-10-30") }
            )
            TmTextField(
                modifier = Modifier.width(220.dp),
                value = hoursOff ?: result?.timeOffHours?.toString()?.removeSuffix(".0").orEmpty(),
                onValueChange = { hoursOff = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = "Hours off",
                supportingText = "Taken from their allowance, 0 for none",
                textStyle = TmTheme.typography.numeric
            )
        } else {
            TmTextField(modifier = Modifier.fillMaxWidth(), value = day, onValueChange = { day = it }, label = "Day", placeholder = "2026-10-27")
            TmFieldPair(
                { TmTextField(modifier = it, value = from, onValueChange = { v -> from = v }, label = "From", placeholder = "9:00") },
                { TmTextField(modifier = it, value = to, onValueChange = { v -> to = v }, label = "To", placeholder = "17:00") }
            )
            result?.takeIf { !it.allDay && it.hours > 0 }?.let {
                Text(text = "${ScheduleRules.hoursText(it.hours)} on the schedule", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
            }
        }

        TmTextField(modifier = Modifier.fillMaxWidth(), value = note, onValueChange = { note = it }, label = "Note", placeholder = if (kind.allDay) "Vacation" else "Opening")

        // A regular week entered once
        if (isNew && !kind.allDay) {
            TmCheckbox(checked = repeats, onCheckedChange = { repeats = it }, label = "Repeat every week")
            if (repeats) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    WEEKDAYS.forEach { (number, name) ->
                        TmFilterChip(text = name, selected = number in weekdays, onClick = { weekdays = if (number in weekdays) weekdays - number else weekdays + number })
                    }
                }
                TmTextField(modifier = Modifier.fillMaxWidth(), value = until, onValueChange = { until = it }, label = "Until", placeholder = "2026-12-31")
            }
        }

        // How much of a series a change or delete goes to
        if (!isNew && entry.seriesIdentifier != null) {
            TmSegmented(label = "Change", options = ScheduleScope.entries, selected = scope, onSelect = { scope = it }, optionLabel = { it.label })
        }

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
            ?: problem?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }
    }
}

/** A new shift for [person] on [day], 9 to 5, or a new entry for whoever is first */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun newScheduleEntry(person: Uuid, day: LocalDate): ScheduleEntry =
    ScheduleEntry(userIdentifier = person, startsAt = AppointmentTimes.toMillis(day, 9, 0), endsAt = AppointmentTimes.toMillis(day, 17, 0))
