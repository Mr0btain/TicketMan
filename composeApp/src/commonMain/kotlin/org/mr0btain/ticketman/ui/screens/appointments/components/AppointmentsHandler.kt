package org.mr0btain.ticketman.ui.screens.appointments.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleRepeat
import org.mr0btain.ticketman.data.schedule.ScheduleRequest
import org.mr0btain.ticketman.data.schedule.ScheduleScope
import org.mr0btain.ticketman.data.schedule.MyTimeOff
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import kotlin.time.Clock

/** How the calendar shows its days */
enum class CalendarView(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    STAFF("Staff")
}

/**
 * State and actions for the calendar: a day, week, month or the staff board around the day picked, the
 * appointments and the staff schedule in it, booking and changing appointments, admins writing the schedule, and
 * handing an appointment to the counter to check in. Owned by the main view model
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 * @param isAdmin Whether they can write the staff schedule
 * @param onCheckIn Starts the counter's check in for an appointment, with its customer when it has one
 */
class AppointmentsHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean,
    private val onCheckIn: (Appointment, Customer?) -> Unit,
    private val localStorage: LocalStorage? = null,
    private val isAdmin: () -> Boolean = { false }
) {
    private val repo get() = remoteHandler.appointmentRepo
    private val scheduleRepo get() = remoteHandler.scheduleRepo

    private val _view = MutableStateFlow(CalendarView.entries.firstOrNull { it.name == localStorage?.calendarView } ?: CalendarView.WEEK)
    val view = _view.asStateFlow()

    private val _day = MutableStateFlow(today())
    /** The day picked, which the view is around */
    val day = _day.asStateFlow()

    private val _weekStart = MutableStateFlow(mondayOf(today()))
    /** The Monday of the week [day] is in */
    val weekStart = _weekStart.asStateFlow()

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    /** The days showing, earliest first */
    val appointments = _appointments.asStateFlow()

    private val _schedule = MutableStateFlow<List<ScheduleEntry>>(emptyList())
    /** The staff schedule touching the days showing, earliest first */
    val schedule = _schedule.asStateFlow()

    private val _staff = MutableStateFlow<List<User>>(emptyList())
    /** Active staff where this device works, by name, for the staff board and the schedule dialog */
    val staff = _staff.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    private val _myTimeOff = MutableStateFlow<MyTimeOff?>(null)
    /** The signed in user's time off balance and requests */
    val myTimeOff = _myTimeOff.asStateFlow()

    private val _waitingTimeOff = MutableStateFlow<List<TimeOffRequest>>(emptyList())
    /** Time off asked for and not answered yet: everyone's for an admin, their own for anyone else */
    val waitingTimeOff = _waitingTimeOff.asStateFlow()

    /** The first and last days the view shows */
    val range: Pair<LocalDate, LocalDate>
        get() = rangeOf(_view.value, _day.value)

    fun refresh() {
        scope.launch { load() }
    }

    fun setView(view: CalendarView) {
        _view.value = view
        localStorage?.calendarView = view.name
        refresh()
    }

    /** Forward one day, week, month or staff board week, or back when [steps] is negative */
    fun move(steps: Int) {
        val day = _day.value
        show(
            when (_view.value) {
                CalendarView.DAY -> day.plus(DatePeriod(days = steps))
                CalendarView.WEEK, CalendarView.STAFF -> day.plus(DatePeriod(days = 7 * steps))
                CalendarView.MONTH -> LocalDate(day.year, day.month, 1).plus(DatePeriod(months = steps))
            }
        )
    }

    /** Moves [weeks] forward, or back when negative */
    fun moveWeek(weeks: Int) = show(_day.value.plus(DatePeriod(days = 7 * weeks)))

    /** Back to today */
    fun thisWeek() = show(today())

    /** Shows the week [day] is in */
    fun showWeekOf(day: LocalDate) = show(day)

    /** Opens [day] by itself, from a month square */
    fun openDay(day: LocalDate) {
        _view.value = CalendarView.DAY
        localStorage?.calendarView = CalendarView.DAY.name
        show(day)
    }

    private fun show(day: LocalDate) {
        _day.value = day
        _weekStart.value = mondayOf(day)
        refresh()
    }

    private suspend fun load() {
        val (from, to) = range
        val booked = repo.range(from.toString(), to.toString()).valueOr { error ->
            _loadError.value = "Couldn't load appointments: ${error.message}"
            return
        }
        val schedule = scheduleRepo.range(from.toString(), to.toString()).valueOr { error ->
            _loadError.value = "Couldn't load the staff schedule: ${error.message}"
            return
        }
        _loadError.value = null
        _appointments.value = booked
        _schedule.value = schedule

        // Who works here, for the staff board's rows
        remoteHandler.userRepo.fetch(0, STAFF_LIMIT).getOrNull()?.let { users ->
            _staff.value = users.filter { it.status == UserStatus.ACTIVE }.sortedBy { it.name.ifBlank { it.username }.lowercase() }
        }
        loadTimeOff()
    }

    private suspend fun loadTimeOff() {
        val mine = remoteHandler.timeOffRepo.mine().getOrNull()
        _myTimeOff.value = mine
        _waitingTimeOff.value = if (isAdmin()) remoteHandler.timeOffRepo.requests(TimeOffStatus.PENDING).getOrNull().orEmpty()
            else mine?.requests.orEmpty().filter { it.status == TimeOffStatus.PENDING }
    }

    // ------------------------------------------ Time off ------------------------------------------

    /**
     * Asks for time off for the signed in user
     * @param onResult Gets null when it's asked, otherwise what went wrong
     */
    fun requestTimeOff(request: TimeOffRequest, onResult: (String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.timeOffRepo.request(request)) {
                is TmResult.Success -> {
                    onResult(null)
                    notify("Time off asked for, an admin will answer it")
                    loadTimeOff()
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Takes back one of their own requests while it's waiting */
    fun withdrawTimeOff(request: TimeOffRequest) {
        scope.launch {
            when (val result = remoteHandler.timeOffRepo.withdraw(request.identifier)) {
                is TmResult.Success -> loadTimeOff()
                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    // ------------------------------------------ Staff schedule ------------------------------------------

    /**
     * Writes a new schedule entry, repeating weekly when [repeat] says, admins only
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun addSchedule(entry: ScheduleEntry, repeat: ScheduleRepeat?, onResult: (String?) -> Unit) {
        if (!isAdmin()) return
        scope.launch {
            when (val result = scheduleRepo.add(ScheduleRequest(entry, repeat))) {
                is TmResult.Success -> {
                    onResult(null)
                    notify(if (result.data.size > 1) "${result.data.size} entries added" else "Added to the schedule")
                    load()
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Changes an entry, and with [scope] more of its series */
    fun updateSchedule(entry: ScheduleEntry, scope: ScheduleScope, onResult: (String?) -> Unit) {
        if (!isAdmin()) return
        this.scope.launch {
            when (val result = scheduleRepo.update(entry, scope)) {
                is TmResult.Success -> {
                    onResult(null)
                    notify("Schedule saved")
                    load()
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Takes an entry off, and with [scope] more of its series */
    fun removeSchedule(entry: ScheduleEntry, scope: ScheduleScope, onResult: (String?) -> Unit) {
        if (!isAdmin()) return
        this.scope.launch {
            when (val result = scheduleRepo.remove(entry.identifier, scope)) {
                is TmResult.Success -> {
                    onResult(null)
                    notify("Taken off the schedule")
                    load()
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Customers to book for */
    suspend fun searchCustomers(text: String): List<Customer> =
        remoteHandler.counterRepo.search(text.trim(), SEARCH_LIMIT).getOrNull()?.customers.orEmpty()

    /** Staff to give it to */
    suspend fun searchTechnicians(text: String): List<TmSearchResult> =
        remoteHandler.userRepo.search(text.trim(), SEARCH_LIMIT).getOrDefault(emptyList())

    /**
     * Books a new appointment or saves changes to one, then shows its week
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun save(appointment: Appointment, isNew: Boolean, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            val result = if (isNew) repo.add(appointment) else repo.update(appointment)
            when (result) {
                is TmResult.Success -> {
                    onResult(null)
                    notify(if (isNew) "Appointment booked" else "Appointment saved")
                    show(dayOf(result.data.startsAt))
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Cancels it, marks it a no show, or books it again */
    fun setStatus(appointment: Appointment, status: AppointmentStatus) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.setStatus(appointment.identifier, status)) {
                is TmResult.Success -> load()
                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    fun remove(appointment: Appointment) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.remove(appointment.identifier)) {
                is TmResult.Success -> {
                    notify("Appointment deleted")
                    load()
                }

                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    /**
     * Starts the counter's check in for it, with its customer when it has one
     * @param onStarted Called once it's ready, to go to the counter
     */
    fun checkIn(appointment: Appointment, onStarted: () -> Unit) {
        if (!canEdit() || !appointment.isOpen) return
        scope.launch {
            val customer = appointment.customerIdentifier?.let { remoteHandler.customerRepo.get(it).getOrNull() }
            onCheckIn(appointment, customer)
            onStarted()
        }
    }

    fun clear() {
        _appointments.value = emptyList()
        _schedule.value = emptyList()
        _staff.value = emptyList()
        _myTimeOff.value = null
        _waitingTimeOff.value = emptyList()
        _loadError.value = null
        _day.value = today()
        _weekStart.value = mondayOf(today())
    }

    // Snackbars suspend until dismissed, so show them on the side
    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    companion object {
        private const val SEARCH_LIMIT = 10
        private const val STAFF_LIMIT = 200

        /** Days the staff board shows, two weeks from the Monday */
        const val STAFF_DAYS = 14

        /**
         * The first and last days [view] shows around [day]: the day, its week, its month as whole weeks (Monday
         * before the 1st to the Sunday after the last), or two weeks from its Monday
         */
        fun rangeOf(view: CalendarView, day: LocalDate): Pair<LocalDate, LocalDate> = when (view) {
            CalendarView.DAY -> day to day
            CalendarView.WEEK -> mondayOf(day) to mondayOf(day).plus(DatePeriod(days = 6))
            CalendarView.STAFF -> mondayOf(day) to mondayOf(day).plus(DatePeriod(days = STAFF_DAYS - 1))
            CalendarView.MONTH -> {
                val first = LocalDate(day.year, day.month, 1)
                val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                mondayOf(first) to mondayOf(last).plus(DatePeriod(days = 6))
            }
        }

        fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun dayOf(millis: Long): LocalDate =
            kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun mondayOf(day: LocalDate): LocalDate =
            day.minus(DatePeriod(days = day.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))
    }
}
