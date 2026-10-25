package org.mr0btain.ticketman.ui.screens.appointments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.selection.TmMenu
import org.mr0btain.ticketman.ui.kit.selection.TmMenuItem
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.appointments.components.AppointmentsHandler
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import ticketman.composeapp.generated.resources.*

/** Widest the week's list gets, so lines stay readable on big screens */
private val LIST_WIDTH = 880.dp

/**
 * The appointment calendar, a week at a time. Booking, changing and calling off appointments, and checking one in
 * at the counter. Restricted users can look but not change anything
 * @param onCheckInStarted Goes to the counter once its check in is ready
 */
@Composable
fun AppointmentsScreen(viewModel: MainViewModel, onCheckInStarted: () -> Unit, modifier: Modifier = Modifier) {
    val appointments = viewModel.appointments
    val loginState by viewModel.session.loginState.collectAsState()
    val canEdit = viewModel.session.canEdit.collectAsState().value
    val weekStart by appointments.weekStart.collectAsState()
    val week by appointments.appointments.collectAsState()
    val loadError by appointments.loadError.collectAsState()

    // The dialog: null is closed, an appointment with isNew is being booked
    var editing by remember { mutableStateOf<Pair<Appointment, Boolean>?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }

    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN) appointments.refresh()
    }

    editing?.let { (appointment, isNew) ->
        AppointmentDialog(
            appointment = appointment,
            isNew = isNew,
            onSearchCustomers = { appointments.searchCustomers(it) },
            onSearchTechnicians = { appointments.searchTechnicians(it) },
            onSave = { saved, onResult -> appointments.save(saved, isNew) { problem -> onResult(problem); if (problem == null) editing = null } },
            onDismissRequest = { editing = null }
        )
    }

    deleting?.let { appointment ->
        TmConfirmDialog(
            title = "Delete this appointment?",
            message = "It goes from the calendar for good. Cancelling keeps it on the calendar, crossed off.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                appointments.remove(appointment)
                deleting = null
            },
            onDismissRequest = { deleting = null }
        )
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
        TmPageHeader(
            title = "Appointments",
            onBack = null,
            subtitle = "Week of ${AppointmentTimes.dayLabel(weekStart)}",
            actions = {
                TmIconButton(icon = Res.drawable.chevron_left, contentDescription = "Week before", onClick = { appointments.moveWeek(-1) })
                TmButton(text = "Today", variant = TmButtonVariant.SECONDARY, onClick = { appointments.thisWeek() })
                TmIconButton(icon = Res.drawable.chevron_right, contentDescription = "Week after", onClick = { appointments.moveWeek(1) })
                if (canEdit) {
                    TmButton(
                        text = "Book",
                        leadingIcon = Res.drawable.plus,
                        onClick = { editing = AppointmentDefaults.new(weekStart) to true }
                    )
                }
            }
        )

        loadError?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        AppointmentWeek(
            weekStart = weekStart,
            appointments = week,
            canEdit = canEdit,
            onEdit = { editing = it to false },
            onCheckIn = { appointments.checkIn(it, onCheckInStarted) },
            onStatus = { appointment, status -> appointments.setStatus(appointment, status) },
            onDelete = { deleting = it }
        )
    }
}

/** What a new appointment starts as */
private object AppointmentDefaults {
    /** The next hour today when this week is showing, otherwise 10am on the week's first day */
    fun new(weekStart: LocalDate): Appointment {
        val today = AppointmentsHandler.today()
        val day = if (AppointmentsHandler.mondayOf(today) == weekStart) today else weekStart
        return Appointment(startsAt = AppointmentTimes.toMillis(day, if (day == today) AppointmentTimes.nextHour() else 10, 0))
    }
}

@Composable
internal fun AppointmentWeek(
    weekStart: LocalDate,
    appointments: List<Appointment>,
    canEdit: Boolean,
    onEdit: (Appointment) -> Unit,
    onCheckIn: (Appointment) -> Unit,
    onStatus: (Appointment, AppointmentStatus) -> Unit,
    onDelete: (Appointment) -> Unit
) {
    val byDay = appointments.groupBy { AppointmentsHandler.dayOf(it.startsAt) }
    val today = AppointmentsHandler.today()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
    ) {
        (0 until 7).map { weekStart.plus(DatePeriod(days = it)) }.forEach { day ->
            val booked = byDay[day].orEmpty()
            Column(Modifier.widthIn(max = LIST_WIDTH).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                TmSectionLabel(text = AppointmentTimes.dayLabel(day) + if (day == today) ", today" else "")
                if (booked.isEmpty()) {
                    Text(text = "Nothing booked", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                }
                booked.forEach { appointment ->
                    AppointmentCard(
                        appointment = appointment,
                        canEdit = canEdit,
                        onEdit = { onEdit(appointment) },
                        onCheckIn = { onCheckIn(appointment) },
                        onStatus = { onStatus(appointment, it) },
                        onDelete = { onDelete(appointment) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
    onStatus: (AppointmentStatus) -> Unit,
    onDelete: () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    val muted = appointment.status == AppointmentStatus.CANCELLED || appointment.status == AppointmentStatus.NO_SHOW

    TmSurface(modifier = Modifier.fillMaxWidth(), onClick = if (canEdit && appointment.isOpen) onEdit else null) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(72.dp)) {
                Text(text = formatTime(appointment.startsAt), style = TmTheme.typography.numeric, color = if (muted) colors.textMuted else colors.text)
                Text(text = "${appointment.minutes} min", style = TmTheme.typography.caption, color = colors.textMuted)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = appointment.customerName.ifBlank { "No name" }, style = TmTheme.typography.label, color = if (muted) colors.textMuted else colors.text)
                val device = appointment.device.toString().trim()
                listOfNotNull(
                    listOf(device, appointment.problem).filter { it.isNotBlank() }.joinToString(": ").takeIf { it.isNotBlank() },
                    appointment.phone.takeIf { it.isNotBlank() && appointment.customerIdentifier == null },
                    appointment.technicianName.takeIf { it.isNotBlank() }?.let { "With $it" },
                    appointment.notes.takeIf { it.isNotBlank() }
                ).forEach { line ->
                    Text(text = line, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 2)
                }
            }

            TmBadge(text = appointment.status.label, tone = colors.tone(appointment.status))

            if (canEdit && appointment.isOpen) {
                TmButton(text = "Check in", leadingIcon = Res.drawable.log_in, variant = TmButtonVariant.SECONDARY, onClick = onCheckIn)
            }

            if (canEdit && appointment.status != AppointmentStatus.ARRIVED) {
                Box {
                    TmIconButton(icon = Res.drawable.more_vertical, contentDescription = "More", onClick = { menuOpen = true })
                    TmMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun pick(action: () -> Unit) {
                            menuOpen = false
                            action()
                        }
                        if (appointment.isOpen) {
                            TmMenuItem(text = "Edit", leadingIcon = Res.drawable.edit_2, onClick = { pick(onEdit) })
                            TmMenuItem(text = "No show", leadingIcon = Res.drawable.user_x, onClick = { pick { onStatus(AppointmentStatus.NO_SHOW) } })
                            TmMenuItem(text = "Cancel", leadingIcon = Res.drawable.x_circle, onClick = { pick { onStatus(AppointmentStatus.CANCELLED) } })
                        } else {
                            TmMenuItem(text = "Book again", leadingIcon = Res.drawable.rotate_ccw, onClick = { pick { onStatus(AppointmentStatus.BOOKED) } })
                        }
                        TmMenuItem(text = "Delete", leadingIcon = Res.drawable.trash_2, onClick = { pick(onDelete) })
                    }
                }
            }
        }
    }
}
