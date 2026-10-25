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
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import kotlin.time.Clock

/**
 * State and actions for the appointments screen: a week of the calendar, booking and changing appointments, and
 * handing one to the counter to check in. Owned by the main view model
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 * @param onCheckIn Starts the counter's check in for an appointment, with its customer when it has one
 */
class AppointmentsHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean,
    private val onCheckIn: (Appointment, Customer?) -> Unit
) {
    private val repo get() = remoteHandler.appointmentRepo

    private val _weekStart = MutableStateFlow(mondayOf(today()))
    /** The Monday of the week showing */
    val weekStart = _weekStart.asStateFlow()

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    /** The week's, earliest first */
    val appointments = _appointments.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    fun refresh() {
        scope.launch { load() }
    }

    /** Moves [weeks] forward, or back when negative */
    fun moveWeek(weeks: Int) {
        _weekStart.value = _weekStart.value.plus(DatePeriod(days = 7 * weeks))
        refresh()
    }

    /** Back to this week */
    fun thisWeek() {
        _weekStart.value = mondayOf(today())
        refresh()
    }

    /** Shows the week [day] is in */
    fun showWeekOf(day: LocalDate) {
        _weekStart.value = mondayOf(day)
        refresh()
    }

    private suspend fun load() {
        val start = _weekStart.value
        val week = repo.range(start.toString(), start.plus(DatePeriod(days = 6)).toString()).valueOr { error ->
            _loadError.value = "Couldn't load appointments: ${error.message}"
            return
        }
        _loadError.value = null
        _appointments.value = week
    }

    /** Customers to book for */
    suspend fun searchCustomers(text: String): List<Customer> =
        remoteHandler.counterRepo.search(text.trim(), SEARCH_LIMIT).getOrNull()?.customers.orEmpty()

    /** Staff to give it to */
    suspend fun searchTechnicians(text: String): List<SearchResult> =
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
                    _weekStart.value = mondayOf(dayOf(result.data.startsAt))
                    load()
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
        _loadError.value = null
        _weekStart.value = mondayOf(today())
    }

    // Snackbars suspend until dismissed, so show them on the side
    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    companion object {
        private const val SEARCH_LIMIT = 10

        fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun dayOf(millis: Long): LocalDate =
            kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun mondayOf(day: LocalDate): LocalDate =
            day.minus(DatePeriod(days = day.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))
    }
}
