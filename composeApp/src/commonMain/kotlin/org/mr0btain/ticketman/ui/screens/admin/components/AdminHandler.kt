package org.mr0btain.ticketman.ui.screens.admin.components

import org.mr0btain.ticketman.error.valueOr
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.components.print.printDocument
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.DailyReport
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * State and actions for the admin screen. Owned by the main view model so it lives as long as the app does.
 * Holds the overview, low stock and audit log itself, and one part per group of tabs: [accounts], [devices]
 * and [server]. The invoices tab uses the counter's invoice list
 * @param localStorage Where the low stock threshold is kept between runs
 * @param onUsersChanged Called after a user is added, changed or removed so lists can reload
 * @param onShopChanged Called after the shop profile is saved, so its currency shows everywhere
 * @param onImported Called after customers or inventory are imported, so the lists reload
 */
class AdminHandler(
    private val remoteHandler: RemoteHandler,
    scope: CoroutineScope,
    snackBarHost: SnackbarHostState,
    private val localStorage: LocalStorage,
    onUsersChanged: (Uuid?) -> Unit,
    onShopChanged: (ShopProfile) -> Unit = {},
    onImported: () -> Unit = {}
) {
    private val admin get() = remoteHandler.adminRepo

    private val support = AdminSupport(scope, snackBarHost, reloadAudit = { loadAudit(reset = true) })
    private val scope get() = support.scope

    val isLoading = support.isLoading

    /** Users tab: accounts, requests, invites and PINs */
    val accounts = AdminAccounts(
        remoteHandler, support, onUsersChanged,
        reloadOverview = { loadOverview() },
        reloadSessions = { devices.reloadSessions() }
    )

    /** Stations and Sessions tabs */
    val devices = AdminDevices(remoteHandler, support, reloadOverview = { loadOverview() })

    val messages = AdminMessages(remoteHandler, support)

    /** Server tab */
    val server = AdminServer(remoteHandler, support, onShopChanged, onSeeded = {
        onUsersChanged(null)
        loadOverview()
        loadLowStock()
    })

    /** Server tab: customers and inventory from a CSV */
    val import = AdminImport(remoteHandler, support, onImported = {
        onImported()
        loadLowStock()
    })

    private val _stats = MutableStateFlow<AdminStatsResponse?>(null)
    val stats = _stats.asStateFlow()

    private val _dailyReport = MutableStateFlow<DailyReport?>(null)
    val dailyReport = _dailyReport.asStateFlow()

    private val _lowStockThreshold = MutableStateFlow(localStorage.lowStockThreshold.coerceIn(LOW_STOCK_RANGE))
    val lowStockThreshold = _lowStockThreshold.asStateFlow()

    private val _lowStock = MutableStateFlow<List<Inventory>>(emptyList())
    val lowStock = _lowStock.asStateFlow()

    private val _auditEntries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val auditEntries = _auditEntries.asStateFlow()

    private val _auditEndReached = MutableStateFlow(false)
    val auditEndReached = _auditEndReached.asStateFlow()

    /**
     * Reloads what [tab] shows, plus the waiting account requests, which badge the Users tab from any tab.
     * The invoices tab loads its own list through the counter
     */
    fun refresh(tab: AdminTab) {
        scope.launch {
            support.loading {
                accounts.reloadPending()

                when (tab) {
                    AdminTab.OVERVIEW -> {
                        loadOverview()
                        _dailyReport.value = admin.getDailyReport(null)
                        // For the report's default paper size
                        server.reloadShopProfile()
                    }

                    AdminTab.USERS, AdminTab.INVOICES -> Unit
                    AdminTab.SESSIONS -> devices.reloadSessions()
                    AdminTab.STATIONS -> devices.reloadStations()
                    AdminTab.LOW_STOCK -> loadLowStock()
                    AdminTab.SERVER -> server.reload()
                    AdminTab.MESSAGES -> {
                        messages.reload()
                        // For the statuses that can send a message
                        server.reloadShopProfile()
                    }
                    AdminTab.AUDIT -> loadAudit(reset = true)
                }
            }
        }
    }

    /**
     * The periodic check for updates on [tab]: like [refresh] but with no loading spinner, and leaving alone what
     * would lose someone's place: the audit log and invoices (reloading starts them from the top), the end of day
     * report (they may be looking at another day) and the shop profile (it may be half edited)
     */
    fun checkForUpdates(tab: AdminTab) {
        scope.launch {
            accounts.reloadPending()

            when (tab) {
                AdminTab.OVERVIEW -> loadOverview()
                AdminTab.SESSIONS -> devices.reloadSessions()
                AdminTab.STATIONS -> devices.reloadStations()
                AdminTab.LOW_STOCK -> loadLowStock()
                AdminTab.SERVER -> server.reloadStatus()
                AdminTab.MESSAGES -> messages.reloadOutbox()
                AdminTab.USERS, AdminTab.INVOICES, AdminTab.AUDIT -> Unit
            }
        }
    }

    // ------------------------------------------ Overview ------------------------------------------

    /**
     * Moves the end of day report [days] from the one showing, nothing past today
     */
    fun moveDailyReport(days: Int) {
        val current = _dailyReport.value?.date?.let { LocalDate.parse(it) } ?: return
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val target = current.plus(DatePeriod(days = days)).let { if (it > today) today else it }

        scope.launch { _dailyReport.value = admin.getDailyReport(target.toString()) }
    }

    /** Prints the end of day report that's showing */
    fun printDailyReport(paper: PaperSize, onResult: (String?) -> Unit) {
        val report = _dailyReport.value ?: return

        scope.launch {
            val shop = server.shopProfile.value ?: admin.getShopProfile() ?: ShopProfile()
            onResult(printDocument(PrintTemplates.dailyReport(shop, report), paper))
        }
    }

    private suspend fun loadOverview() {
        _stats.value = admin.getStats(_lowStockThreshold.value, RECENT_TICKET_COUNT)
    }

    // ------------------------------------------ Low stock ------------------------------------------

    /** Changes what counts as low stock, saves it on this device, and reloads the list and overview */
    fun setLowStockThreshold(threshold: Int) {
        val value = threshold.coerceIn(LOW_STOCK_RANGE)
        _lowStockThreshold.value = value
        localStorage.lowStockThreshold = value

        scope.launch {
            loadLowStock()
            loadOverview()
        }
    }

    private suspend fun loadLowStock() {
        _lowStock.value = admin.getLowStock(_lowStockThreshold.value, LOW_STOCK_LIMIT)
    }

    // ------------------------------------------ Audit log ------------------------------------------

    /** Loads the next page of the audit log, unless the last one was the end */
    fun loadMoreAudit() {
        if (_auditEndReached.value) return

        scope.launch { loadAudit(reset = false) }
    }

    private suspend fun loadAudit(reset: Boolean) {
        val offset = if (reset) 0L else _auditEntries.value.size.toLong()
        val page = admin.fetchAudit(offset, AUDIT_PAGE_SIZE).valueOr { error ->
            support.notify("Couldn't load the audit log: ${error.message}")
            return
        }

        _auditEntries.value = if (reset) page else _auditEntries.value + page
        _auditEndReached.value = page.size < AUDIT_PAGE_SIZE
    }

    /** Clears out everything, used on log out so the next user doesn't see it */
    fun clear() {
        _stats.value = null
        _dailyReport.value = null
        _lowStock.value = emptyList()
        _auditEntries.value = emptyList()
        _auditEndReached.value = false
        accounts.clear()
        devices.clear()
        server.clear()
        import.close()
    }

    companion object {
        val LOW_STOCK_RANGE = 0..10_000
        private const val LOW_STOCK_LIMIT = 200
        private const val RECENT_TICKET_COUNT = 10
        private const val AUDIT_PAGE_SIZE = 50
    }
}
