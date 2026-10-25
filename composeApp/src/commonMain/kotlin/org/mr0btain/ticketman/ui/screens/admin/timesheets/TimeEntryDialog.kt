package org.mr0btain.ticketman.ui.screens.admin.timesheets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.trash_2
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.appointments.AppointmentTimes
import org.mr0btain.ticketman.ui.screens.appointments.components.AppointmentsHandler
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Adds time on the clock by hand, or changes a stretch: the day, when they clocked in and out (blank out means
 * still on), and a note. Whose time it is only picked when adding
 * @param entry The one being changed, null to add one
 * @param onSave Gets the entry, calls back with what went wrong or null once it's saved
 * @param onDelete Asks to delete the one being changed, null hides the button
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun TimeEntryDialog(
    entry: TimeEntry?,
    users: List<User>,
    onSave: (TimeEntry, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val people = remember(users) { users.filter { it.status != UserStatus.DEACTIVATED } }
    var user by remember { mutableStateOf(entry?.let { e -> people.firstOrNull { it.identifier == e.userIdentifier } }) }
    var day by remember { mutableStateOf((entry?.let { AppointmentTimes.day(it.clockedInAt) } ?: AppointmentsHandler.today()).toString()) }
    var clockIn by remember { mutableStateOf(entry?.let { formatTime(it.clockedInAt) } ?: "09:00") }
    var clockOut by remember { mutableStateOf(entry?.clockedOutAt?.let { formatTime(it) } ?: if (entry == null) "17:00" else "") }
    var note by remember { mutableStateOf(entry?.note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val date = runCatching { LocalDate.parse(day.trim()) }.getOrNull()
    val start = AppointmentTimes.parseTime(clockIn)
    val end = clockOut.trim().takeIf { it.isNotEmpty() }?.let { AppointmentTimes.parseTime(it) }
    val inAt = if (date != null && start != null) AppointmentTimes.toMillis(date, start.first, start.second) else 0L
    // Out before in means it ran past midnight
    val outAt = if (date != null && end != null) {
        val sameDay = AppointmentTimes.toMillis(date, end.first, end.second)
        if (sameDay > inAt) sameDay else AppointmentTimes.toMillis(date.plus(DatePeriod(days = 1)), end.first, end.second)
    } else null

    val owner = entry?.userIdentifier ?: user?.identifier
    val result = owner?.let {
        (entry ?: TimeEntry(identifier = Uuid.generateV7(), userIdentifier = it, clockedInAt = inAt))
            .copy(clockedInAt = inAt, clockedOutAt = outAt, note = note.trim())
    }
    val problem = when {
        owner == null -> "Pick whose time it is"
        date == null -> "Enter the day like 2026-10-27"
        start == null -> "Enter when they clocked in, like 09:00"
        clockOut.isNotBlank() && end == null -> "Enter when they clocked out, like 17:30, or leave it blank if they're still on"
        else -> result?.problem()
    }

    TmDialog(
        title = if (entry == null) "Add time" else "Change ${entry.userName}'s time",
        description = "Changed by hand, with your name on it",
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = "Save",
                confirmEnabled = problem == null,
                loading = saving,
                onCancel = onDismissRequest,
                onConfirm = {
                    val saved = result ?: return@TmButtonRow
                    saving = true
                    error = null
                    onSave(saved) { failed ->
                        saving = false
                        error = failed
                    }
                }
            )
        }
    ) {
        if (entry == null) {
            TmDropdown(
                modifier = Modifier.fillMaxWidth(),
                label = "Who",
                options = people,
                selected = user,
                onSelect = { user = it },
                optionLabel = { it.name.ifBlank { it.username } }
            )
        }

        TmTextField(modifier = Modifier.fillMaxWidth(), value = day, onValueChange = { day = it }, label = "Day", placeholder = "2026-10-27")
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(modifier = Modifier.weight(1f), value = clockIn, onValueChange = { clockIn = it }, label = "Clocked in", placeholder = "09:00")
            TmTextField(modifier = Modifier.weight(1f), value = clockOut, onValueChange = { clockOut = it }, label = "Clocked out", placeholder = "Still on")
        }
        TmTextField(modifier = Modifier.fillMaxWidth(), value = note, onValueChange = { note = it }, label = "Note", placeholder = "Forgot to clock out")

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
            ?: problem?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }

        onDelete?.let { delete ->
            TmButton(text = "Delete this time", variant = TmButtonVariant.GHOST, leadingIcon = Res.drawable.trash_2, onClick = delete)
        }
    }
}
