package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.utils.formatHours
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrNull
import kotlin.time.Clock

/**
 * The signed in user's own time clock: whether they're on it, their hours this week, and clocking in or out from
 * this device (the user menu and Settings). Clocking at a station by PIN is the kiosk's
 */
class TimeClockHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState
) {
    private val repo get() = remoteHandler.timeClockRepo

    private val _current = MutableStateFlow<TimeEntry?>(null)
    /** Their stretch on the clock now, null when they're off it */
    val current = _current.asStateFlow()

    private val _weekHours = MutableStateFlow<Double?>(null)
    /** Hours since Monday, null until loaded */
    val weekHours = _weekHours.asStateFlow()

    fun refresh() {
        scope.launch { load() }
    }

    private suspend fun load() {
        repo.me().getOrNull()?.let { _current.value = it.entry }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        _weekHours.value = repo.myTimesheet(monday.toString(), monday.plus(DatePeriod(days = 6)).toString()).getOrNull()?.people?.sumOf { it.hours }
    }

    /** Clocks in, or out when they're on the clock, and says which */
    fun punch() {
        scope.launch {
            when (val result = repo.mePunch()) {
                is TmResult.Success -> {
                    val entry = result.data.entry
                    val now = Clock.System.now().toEpochMilliseconds()
                    notify(
                        if (result.data.clockedIn) "Clocked in at ${formatTime(entry.clockedInAt)}"
                        else "Clocked out after ${formatHours(entry.hours(now))}"
                    )
                    load()
                }

                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    fun clear() {
        _current.value = null
        _weekHours.value = null
    }

    // Snackbars suspend until dismissed, so show them on the side
    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }
}
