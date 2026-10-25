package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.schedule.TimeOffBalance
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr

/**
 * The Time off tab: requests waiting for an answer and recent ones, and everyone's allowance and balance
 * @param onChanged Run after answering, so the calendar shows the time off
 */
class AdminTimeOff(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val onChanged: () -> Unit = {}
) {
    private val repo get() = remoteHandler.timeOffRepo

    private val _requests = MutableStateFlow<List<TimeOffRequest>>(emptyList())
    /** Every request, newest first */
    val requests = _requests.asStateFlow()

    private val _balances = MutableStateFlow<List<TimeOffBalance>>(emptyList())
    /** This year's balance for each active user */
    val balances = _balances.asStateFlow()

    private val _waiting = MutableStateFlow(0)
    /** How many are waiting, for the tab's badge */
    val waiting = _waiting.asStateFlow()

    suspend fun reload() {
        _requests.value = repo.requests().valueOr { error ->
            support.notify("Couldn't load time off requests: ${error.message}")
            return
        }
        _waiting.value = _requests.value.count { it.status == TimeOffStatus.PENDING }
        _balances.value = repo.balances().getOrNull().orEmpty()
    }

    /** Just the count, for the badge from any tab */
    suspend fun reloadWaiting() {
        repo.requests(TimeOffStatus.PENDING).getOrNull()?.let { _waiting.value = it.size }
    }

    fun approve(request: TimeOffRequest) = answer("Approved ${request.userName}'s time off") { repo.approve(request.identifier) }

    fun decline(request: TimeOffRequest, reason: String) = answer("Declined ${request.userName}'s time off") { repo.decline(request.identifier, reason) }

    /** Sets someone's yearly allowance in hours */
    fun setAllowance(balance: TimeOffBalance, hours: Double) {
        support.scope.launch {
            when (val result = repo.setAllowance(balance.userIdentifier, hours)) {
                is TmResult.Success -> {
                    _balances.value = _balances.value.map { if (it.userIdentifier == balance.userIdentifier) result.data.copy(userName = it.userName) else it }
                    support.notify("${balance.userName} gets ${hours.toString().removeSuffix(".0")}h a year")
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
            support.auditChanged()
        }
    }

    private fun answer(message: String, call: suspend () -> TmResult<TimeOffRequest, *>) {
        support.scope.launch {
            when (val result = call()) {
                is TmResult.Success -> {
                    support.notify(message)
                    reload()
                    onChanged()
                }

                is TmResult.Error -> support.notify((result.error as? org.mr0btain.ticketman.error.Error)?.message ?: "That didn't go through")
            }
            support.auditChanged()
        }
    }
}
