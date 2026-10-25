package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.components.print.printDocument
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.ui.screens.admin.invoices.ExportRange
import kotlin.time.Clock

/**
 * The Reports tab: how the shop did over some days, and the techs' commission rates
 * @param shopProfile The shop for the printed header, when the server tab has it loaded
 */
class AdminReports(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val shopProfile: () -> ShopProfile?
) {
    private val reports get() = remoteHandler.reportRepo

    private val _range = MutableStateFlow(ExportRange.THIS_MONTH)
    val range = _range.asStateFlow()

    private val _report = MutableStateFlow<PeriodReport?>(null)
    /** Null until loaded, or when it couldn't be */
    val report = _report.asStateFlow()

    private val _commissions = MutableStateFlow<List<CommissionRate>>(emptyList())
    val commissions = _commissions.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    /** Everyone a rate can be given to */
    val users = _users.asStateFlow()

    // The days a custom range was last shown for
    private var customDays: Pair<LocalDate, LocalDate>? = null

    suspend fun reload() {
        loadReport()
        _commissions.value = reports.commissions().valueOr { error ->
            support.notify("Couldn't load the commission rates: ${error.message}")
            _commissions.value
        }
        _users.value = remoteHandler.userRepo.fetch(0, USER_LIMIT).valueOr { _users.value }
    }

    /** The report again for the same days, for the periodic check */
    suspend fun reloadReport() = loadReport()

    /** Shows [range]; a custom one waits for [showDays] */
    fun pick(range: ExportRange) {
        _range.value = range
        if (range != ExportRange.CUSTOM) support.scope.launch { support.loading { loadReport() } }
    }

    /** Shows the custom range from [from] to [to] */
    fun showDays(from: LocalDate, to: LocalDate) {
        _range.value = ExportRange.CUSTOM
        customDays = from to to
        support.scope.launch { support.loading { loadReport() } }
    }

    /** First and last day of what's picked, null for a custom range not yet given */
    fun days(): Pair<LocalDate, LocalDate>? =
        _range.value.days(today()) ?: customDays

    private suspend fun loadReport() {
        val (from, to) = days() ?: return

        _report.value = reports.period(from.toString(), to.toString()).valueOr { error ->
            support.notify("Couldn't load the report: ${error.message}")
            null
        }
    }

    /** Saves [rates] as every user's commission, a user left out has none */
    fun saveCommissions(rates: List<CommissionRate>, onSaved: () -> Unit) {
        support.scope.launch {
            support.loading {
                when (val result = reports.setCommissions(rates)) {
                    is TmResult.Success -> {
                        _commissions.value = rates.filter { it.basisPoints > 0 }
                        support.notify("Commission rates saved")
                        onSaved()
                        loadReport()
                    }

                    is TmResult.Error -> support.notify(result.error.message)
                }
                support.auditChanged()
            }
        }
    }

    /** Prints the report that's showing */
    fun print(paper: PaperSize, onResult: (String?) -> Unit) {
        val report = _report.value ?: return

        support.scope.launch {
            val shop = shopProfile() ?: remoteHandler.adminRepo.getShopProfile() ?: ShopProfile()
            onResult(printDocument(PrintTemplates.periodReport(shop, report), paper))
        }
    }

    fun clear() {
        _report.value = null
        _commissions.value = emptyList()
        _users.value = emptyList()
    }

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private companion object {
        const val USER_LIMIT = 200
    }
}
