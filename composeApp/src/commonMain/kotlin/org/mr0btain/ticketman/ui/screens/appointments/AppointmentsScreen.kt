package org.mr0btain.ticketman.ui.screens.appointments

import org.mr0btain.ticketman.data.schedule.TimeOffRequest
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
import org.mr0btain.ticketman.ui.screens.CheckForUpdates
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
import org.mr0btain.ticketman.ui.screens.appointments.components.CalendarView
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.ui.kit.data.TmScheduleBlock
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import ticketman.composeapp.generated.resources.*

/** Widest the week's list gets, so lines stay readable on big screens */
private val LIST_WIDTH = 880.dp

/**
 * The calendar: appointments and the staff schedule as a day, a week, a month of day squares, or the staff board.
 * Booking, changing and calling off appointments, checking one in at the counter, and for admins writing the
 * schedule. Restricted users can look but not change anything
 * @param onCheckInStarted Goes to the counter once its check in is ready
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentsScreen(viewModel: MainViewModel, onCheckInStarted: () -> Unit, modifier: Modifier = Modifier) {
    val appointments = viewModel.appointments
    val loginState by viewModel.session.loginState.collectAsState()
    val canEdit = viewModel.session.canEdit.collectAsState().value
    val isAdmin = viewModel.session.isAdmin.collectAsState().value
    val view by appointments.view.collectAsState()
    val day by appointments.day.collectAsState()
    val weekStart by appointments.weekStart.collectAsState()
    val booked by appointments.appointments.collectAsState()
    val schedule by appointments.schedule.collectAsState()
    val staff by appointments.staff.collectAsState()
    val loadError by appointments.loadError.collectAsState()
    val workingLocation by viewModel.locations.working.collectAsState()
    val myTimeOff by appointments.myTimeOff.collectAsState()
    val waitingTimeOff by appointments.waitingTimeOff.collectAsState()
    var askingTimeOff by remember { mutableStateOf(false) }

    // The dialogs: null is closed, with isNew the entry is being added
    var editing by remember { mutableStateOf<Pair<Appointment, Boolean>?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }
    var scheduling by remember { mutableStateOf<Pair<ScheduleEntry, Boolean>?>(null) }

    LaunchedEffect(loginState, workingLocation) {
        if (loginState == LoginState.LOGGED_IN) appointments.refresh()
    }

    // Bookings from other devices, on the interval set in Settings, unless a dialog here is open
    val updateInterval by viewModel.settings.updateInterval.collectAsState()
    CheckForUpdates(interval = updateInterval, enabled = loginState == LoginState.LOGGED_IN && editing == null && deleting == null && scheduling == null) {
        appointments.refresh()
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

    scheduling?.let { (entry, isNew) ->
        ScheduleEntryDialog(
            entry = entry,
            isNew = isNew,
            staff = staff,
            onSave = { saved, repeat, scope, onResult ->
                val done: (String?) -> Unit = { problem -> onResult(problem); if (problem == null) scheduling = null }
                if (isNew) appointments.addSchedule(saved, repeat, done) else appointments.updateSchedule(saved, scope, done)
            },
            onRemove = if (isNew) null else { scope, onResult -> appointments.removeSchedule(entry, scope) { problem -> onResult(problem); if (problem == null) scheduling = null } },
            onDismissRequest = { scheduling = null }
        )
    }

    if (askingTimeOff) {
        TimeOffDialog(
            mine = myTimeOff,
            firstDay = day,
            onRequest = { request, onResult -> appointments.requestTimeOff(request, onResult) },
            onWithdraw = { appointments.withdrawTimeOff(it) },
            onDismissRequest = { askingTimeOff = false }
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

    // Time off still waiting shows on the schedule too, to the person and to admins, as an entry that opens nothing
    val shown = schedule + waitingTimeOff.map { it.asWaitingEntry() }
    val openEntry: ((ScheduleEntry) -> Unit)? = if (isAdmin) ({ entry -> if (!entry.isWaitingTimeOff) scheduling = entry to false }) else null
    val (from, to) = appointments.range
    val title = when (view) {
        CalendarView.DAY -> AppointmentTimes.dayLabel(day) + if (day == AppointmentsHandler.today()) ", today" else ""
        CalendarView.WEEK -> "Week of ${AppointmentTimes.dayLabel(weekStart)}"
        CalendarView.MONTH -> day.month.name.lowercase().replaceFirstChar { it.titlecase() } + " ${day.year}"
        CalendarView.STAFF -> "${AppointmentTimes.dayLabel(from)} to ${AppointmentTimes.dayLabel(to)}"
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
        TmPageHeader(
            title = "Calendar",
            onBack = null,
            subtitle = title,
            actions = {
                if (canEdit) {
                    TmButton(text = "Book", leadingIcon = Res.drawable.plus, onClick = { editing = AppointmentDefaults.new(if (view == CalendarView.DAY) day else weekStart) to true })
                }
                TmButton(text = "Time off", leadingIcon = Res.drawable.sun, variant = TmButtonVariant.SECONDARY, onClick = { askingTimeOff = true })
                if (isAdmin) {
                    TmButton(
                        text = "Schedule",
                        leadingIcon = Res.drawable.users,
                        variant = TmButtonVariant.SECONDARY,
                        enabled = staff.isNotEmpty(),
                        onClick = { staff.firstOrNull()?.let { scheduling = newScheduleEntry(it.identifier, day) to true } }
                    )
                }
            }
        )

        // How it shows the days, and moving through them. Wraps under each other on a phone
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            TmSegmented(options = CalendarView.entries, selected = view, onSelect = { appointments.setView(it) }, optionLabel = { it.label })
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TmIconButton(icon = Res.drawable.chevron_left, contentDescription = "Back", onClick = { appointments.move(-1) })
                TmButton(text = "Today", variant = TmButtonVariant.SECONDARY, onClick = { appointments.thisWeek() })
                TmIconButton(icon = Res.drawable.chevron_right, contentDescription = "Forward", onClick = { appointments.move(1) })
            }
        }

        loadError?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        val list: @Composable (List<Appointment>) -> Unit = { shown ->
            shown.forEach { appointment ->
                Box(Modifier.widthIn(max = LIST_WIDTH).fillMaxWidth()) {
                    AppointmentCard(
                        appointment = appointment,
                        canEdit = canEdit,
                        onEdit = { editing = appointment to false },
                        onCheckIn = { appointments.checkIn(appointment, onCheckInStarted) },
                        onStatus = { appointments.setStatus(appointment, it) },
                        onDelete = { deleting = appointment }
                    )
                }
            }
        }

        when (view) {
            CalendarView.DAY -> {
                val today = booked.filter { AppointmentsHandler.dayOf(it.startsAt) == day }.sortedBy { it.startsAt }
                CalendarDay(day = day, appointments = today, schedule = shown, onEntry = openEntry) {
                    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) { list(today) }
                }
            }

            CalendarView.WEEK -> AppointmentWeek(
                weekStart = weekStart,
                appointments = booked,
                canEdit = canEdit,
                onEdit = { editing = it to false },
                onCheckIn = { appointments.checkIn(it, onCheckInStarted) },
                onStatus = { appointment, status -> appointments.setStatus(appointment, status) },
                onDelete = { deleting = it },
                schedule = shown,
                onEntry = openEntry
            )

            CalendarView.MONTH -> CalendarMonth(
                month = day,
                appointments = booked,
                schedule = shown,
                onDay = { appointments.openDay(it) },
                onEntry = openEntry
            )

            CalendarView.STAFF -> StaffBoard(
                start = from,
                dayCount = AppointmentsHandler.STAFF_DAYS,
                staff = staff,
                schedule = shown,
                onEntry = openEntry,
                onEmpty = if (isAdmin) ({ person, onDay -> scheduling = newScheduleEntry(person, onDay) to true }) else null
            )
        }
    }
}

/** A waiting request drawn on the schedule, kind Other so it reads apart from approved time off */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private fun TimeOffRequest.asWaitingEntry(): ScheduleEntry = ScheduleEntry(
    identifier = identifier,
    userIdentifier = userIdentifier ?: identifier,
    userName = userName,
    kind = ScheduleKind.OTHER,
    startsAt = startsAt,
    endsAt = endsAt,
    allDay = true,
    note = "Time off, waiting",
    requestIdentifier = identifier
)

/** A waiting request made by [asWaitingEntry], not an entry on the schedule */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private val ScheduleEntry.isWaitingTimeOff: Boolean
    get() = requestIdentifier == identifier

/** What a new appointment starts as */
private object AppointmentDefaults {
    /** The next hour today when this week is showing, otherwise 10am on the week's first day */
    fun new(weekStart: LocalDate): Appointment {
        val today = AppointmentsHandler.today()
        val day = if (AppointmentsHandler.mondayOf(today) == weekStart) today else weekStart
        return Appointment(startsAt = AppointmentTimes.toMillis(day, if (day == today) AppointmentTimes.nextHour() else 10, 0))
    }
}

/**
 * A week as a list of days, each with who's working and its appointments
 * @param schedule The staff schedule touching the week
 * @param onEntry Opens an entry, null when the schedule can't be changed here
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppointmentWeek(
    weekStart: LocalDate,
    appointments: List<Appointment>,
    canEdit: Boolean,
    onEdit: (Appointment) -> Unit,
    onCheckIn: (Appointment) -> Unit,
    onStatus: (Appointment, AppointmentStatus) -> Unit,
    onDelete: (Appointment) -> Unit,
    schedule: List<ScheduleEntry> = emptyList(),
    onEntry: ((ScheduleEntry) -> Unit)? = null
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
                // Who's working, shifts by start then time off
                val working = schedule.filter { it.isOn(day, kotlinx.datetime.TimeZone.currentSystemDefault()) }.sortedWith(compareBy<ScheduleEntry> { it.allDay }.thenBy { it.startsAt })
                if (working.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                        working.forEach { entry ->
                            TmScheduleBlock(
                                modifier = Modifier.widthIn(min = 120.dp, max = 220.dp),
                                title = entry.userName.ifBlank { entry.kind.label },
                                subtitle = if (entry.kind == ScheduleKind.SHIFT) entry.hoursLabel() else "${entry.kind.label}, ${entry.hoursLabel().lowercase()}",
                                tone = TmTheme.colors.tone(entry.kind.tone),
                                onClick = onEntry?.let { open -> { open(entry) } }
                            )
                        }
                    }
                }
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
internal fun AppointmentCard(
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

    val time: @Composable () -> Unit = {
        Column(Modifier.width(72.dp)) {
            Text(text = formatTime(appointment.startsAt), style = TmTheme.typography.numeric, color = if (muted) colors.textMuted else colors.text)
            Text(text = "${appointment.minutes} min", style = TmTheme.typography.caption, color = colors.textMuted)
        }
    }

    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    }

    val actions: @Composable () -> Unit = {
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

    TmSurface(modifier = Modifier.fillMaxWidth(), onClick = if (canEdit && appointment.isOpen) onEdit else null) {
        BoxWithConstraints {
            if (maxWidth < CARD_ROW_WIDTH) {
                // Narrow: time, who and the status on top, then the details, then the buttons
                Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Top) {
                        time()
                        Box(Modifier.weight(1f)) { details() }
                        TmBadge(text = appointment.status.label, tone = colors.tone(appointment.status))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) { actions() }
                }
            } else {
                Row(
                    modifier = Modifier.padding(spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    time()
                    Box(Modifier.weight(1f)) { details() }
                    TmBadge(text = appointment.status.label, tone = colors.tone(appointment.status))
                    actions()
                }
            }
        }
    }
}

/** Narrower than this, a card's buttons go under its details */
private val CARD_ROW_WIDTH = 560.dp
