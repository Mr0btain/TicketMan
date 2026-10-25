package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.timeclock.Timesheet
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.ui.screens.admin.invoices.ExportRange
import kotlin.time.Clock

/**
 * The Timesheets tab: everyone's time on the clock over some days, who's on now, and fixing times by hand
 */
class AdminTimesheets(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport
) {
    private val clock get() = remoteHandler.timeClockRepo

    private val _range = MutableStateFlow(ExportRange.THIS_MONTH)
    val range = _range.asStateFlow()

    private val _sheet = MutableStateFlow<Timesheet?>(null)
    /** Null until loaded, or when it couldn't be */
    val sheet = _sheet.asStateFlow()

    private val _onNow = MutableStateFlow<List<TimeEntry>>(emptyList())
    /** Everyone on the clock now */
    val onNow = _onNow.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    /** Who time can be added for */
    val users = _users.asStateFlow()

    // The days a custom range was last shown for
    private var customDays: Pair<LocalDate, LocalDate>? = null

    suspend fun reload() {
        load()
        _users.value = remoteHandler.userRepo.fetch(0, USER_LIMIT).valueOr { _users.value }
    }

    /** Who's on now and the sheet, for the periodic check */
    suspend fun load() {
        _onNow.value = clock.now().getOrNull() ?: _onNow.value
        val (from, to) = days() ?: return
        _sheet.value = clock.timesheet(from.toString(), to.toString()).valueOr { error ->
            support.notify("Couldn't load the timesheets: ${error.message}")
            null
        }
    }

    /** Shows [range]; a custom one waits for [showDays] */
    fun pick(range: ExportRange) {
        _range.value = range
        if (range != ExportRange.CUSTOM) support.scope.launch { support.loading { load() } }
    }

    fun showDays(from: LocalDate, to: LocalDate) {
        _range.value = ExportRange.CUSTOM
        customDays = from to to
        support.scope.launch { support.loading { load() } }
    }

    /** First and last day of what's picked, null for a custom range not yet given */
    fun days(): Pair<LocalDate, LocalDate>? =
        _range.value.days(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date) ?: customDays

    /**
     * Adds time by hand, or saves changes to an entry
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun save(entry: TimeEntry, isNew: Boolean, onResult: (String?) -> Unit) {
        support.scope.launch {
            val result = if (isNew) clock.add(entry) else clock.update(entry)
            when (result) {
                is TmResult.Success -> {
                    onResult(null)
                    support.notify(if (isNew) "Time added" else "Time saved")
                    load()
                }

                is TmResult.Error -> onResult(result.error.message)
            }
            support.auditChanged()
        }
    }

    fun remove(entry: TimeEntry) {
        support.scope.launch {
            support.report(clock.remove(entry.identifier), "Time deleted") { load() }
        }
    }

    fun clear() {
        _sheet.value = null
        _onNow.value = emptyList()
        _users.value = emptyList()
    }

    private companion object {
        const val USER_LIMIT = 200
    }
}
