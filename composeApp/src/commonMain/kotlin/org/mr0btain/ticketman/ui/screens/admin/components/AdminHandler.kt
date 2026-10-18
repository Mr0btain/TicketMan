package org.mr0btain.ticketman.ui.screens.admin.components

import androidx.compose.material3.SnackbarHostState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.network.transfer.DownloadOutcome
import org.mr0btain.ticketman.components.network.transfer.fileBody
import org.mr0btain.ticketman.components.network.transfer.saveDownload
import org.mr0btain.ticketman.components.utils.formatBytes
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.components.print.printDocument
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.DailyReport
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * State and actions for the admin screen. Owned by the main view model so it lives as long as the app does.
 * @param onUsersChanged Called after a user is added, changed or removed so lists can reload
 */
@Suppress("PrivatePropertyName")
class AdminHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val onUsersChanged: (Uuid?) -> Unit
) {
    private val TAG = "AdminHandler"
    private val BACKUP_TAG = "$TAG:Backup"

    private val admin get() = remoteHandler.adminRepo
    private val users get() = remoteHandler.userRepo

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Where a backup or restore upload is up to, null when none is running
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress = _backupProgress.asStateFlow()

    // A restore the server will apply when it next starts
    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore = _pendingRestore.asStateFlow()

    private val _stats = MutableStateFlow<AdminStatsResponse?>(null)
    val stats = _stats.asStateFlow()

    private val _dailyReport = MutableStateFlow<DailyReport?>(null)
    val dailyReport = _dailyReport.asStateFlow()

    private val _lowStockThreshold = MutableStateFlow(DEFAULT_LOW_STOCK_THRESHOLD)
    val lowStockThreshold = _lowStockThreshold.asStateFlow()

    private val _lowStock = MutableStateFlow<List<Inventory>>(emptyList())
    val lowStock = _lowStock.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingUserResponse>>(emptyList())
    val pending = _pending.asStateFlow()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations = _stations.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionResponse>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfoResponse?>(null)
    val serverInfo = _serverInfo.asStateFlow()

    private val _shopProfile = MutableStateFlow<ShopProfile?>(null)
    val shopProfile = _shopProfile.asStateFlow()

    private val _auditEntries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val auditEntries = _auditEntries.asStateFlow()

    private val _auditEndReached = MutableStateFlow(false)
    val auditEndReached = _auditEndReached.asStateFlow()

    // ------------------------------------------ Loading ------------------------------------------

    /** Reloads everything the admin screen shows */
    fun refreshAll() {
        scope.launch {
            _isLoading.value = true

            loadOverview()
            loadLowStock()
            _dailyReport.value = admin.getDailyReport(null)
            _pending.value = users.getPending()
            _stations.value = admin.getStations()
            _sessions.value = admin.getSessions()
            _serverInfo.value = admin.getServerInfo()
            _pendingRestore.value = admin.getPendingRestore()
            _shopProfile.value = admin.getShopProfile()
            loadAudit(reset = true)

            _isLoading.value = false
        }
    }

    fun refreshOverview() {
        scope.launch { loadOverview() }
    }

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
            val shop = _shopProfile.value ?: admin.getShopProfile() ?: ShopProfile()
            onResult(printDocument(PrintTemplates.dailyReport(shop, report), paper))
        }
    }

    fun setLowStockThreshold(threshold: Int) {
        _lowStockThreshold.value = threshold.coerceIn(LOW_STOCK_RANGE)

        scope.launch {
            loadLowStock()
            loadOverview()
        }
    }

    fun refreshSessions() {
        scope.launch { _sessions.value = admin.getSessions() }
    }

    fun refreshServerInfo() {
        scope.launch {
            _serverInfo.value = admin.getServerInfo()
            _pendingRestore.value = admin.getPendingRestore()
        }
    }

    fun refreshAudit() {
        scope.launch { loadAudit(reset = true) }
    }

    fun loadMoreAudit() {
        if (_auditEndReached.value) return

        scope.launch { loadAudit(reset = false) }
    }

    /** Clears out everything, used on log out so the next user doesn't see it */
    fun clear() {
        _stats.value = null
        _dailyReport.value = null
        _pending.value = emptyList()
        _stations.value = emptyList()
        _lowStock.value = emptyList()
        _sessions.value = emptyList()
        _serverInfo.value = null
        _pendingRestore.value = null
        _shopProfile.value = null
        _auditEntries.value = emptyList()
        _auditEndReached.value = false
    }

    private suspend fun loadOverview() {
        _stats.value = admin.getStats(_lowStockThreshold.value, RECENT_TICKET_COUNT)
    }

    private suspend fun loadLowStock() {
        _lowStock.value = admin.getLowStock(_lowStockThreshold.value, LOW_STOCK_LIMIT)
    }

    private suspend fun loadAudit(reset: Boolean) {
        val offset = if (reset) 0L else _auditEntries.value.size.toLong()
        val page = admin.fetchAudit(offset, AUDIT_PAGE_SIZE)

        _auditEntries.value = if (reset) page else _auditEntries.value + page
        _auditEndReached.value = page.size < AUDIT_PAGE_SIZE
    }

    // ------------------------------------------ Users ------------------------------------------

    /**
     * Adds or updates a user
     * @param password Only used for new users, existing ones change it through [resetPassword]
     * @param onSuccess Run once the server accepts it, used to close the dialog
     */
    fun saveUser(user: User, isNew: Boolean, password: String, onSuccess: () -> Unit) {
        scope.launch {
            val result = if (isNew) users.add(user.copy(password = password)) else users.update(user.withoutPassword())

            report(result, if (isNew) "Added ${user.username}" else "Saved ${user.username}") {
                onSuccess()
                onUsersChanged(user.identifier)
                loadOverview()
            }
        }
    }

    fun resetPassword(user: User, password: String, onSuccess: () -> Unit) {
        scope.launch {
            report(users.resetPassword(user.identifier, password), "Password changed for ${user.username}") {
                onSuccess()
                _sessions.value = admin.getSessions()
            }
        }
    }

    /**
     * Deactivating blocks logging in and ends their sessions, reactivating keeps the access level they had
     */
    fun setUserActive(user: User, active: Boolean) {
        val status = if (active) UserStatus.ACTIVE else UserStatus.DEACTIVATED
        val message = if (active) "Reactivated ${user.username}" else "Deactivated ${user.username}"

        scope.launch {
            report(users.update(user.withoutPassword().copy(status = status)), message) {
                onUsersChanged(user.identifier)
                _sessions.value = admin.getSessions()
                loadOverview()
            }
        }
    }

    fun deleteUser(user: User) {
        scope.launch {
            report(users.remove(user.identifier), "Deleted ${user.username}") {
                onUsersChanged(null)
                _sessions.value = admin.getSessions()
                loadOverview()
            }
        }
    }

    // ------------------------------------------ New accounts ------------------------------------------

    fun refreshPending() {
        scope.launch { _pending.value = users.getPending() }
    }

    /** Lets a requested account in. They finish setting up on their first login */
    fun approve(user: User, accessLevel: AccessLevel, onSuccess: () -> Unit) {
        scope.launch {
            report(users.approve(user.identifier, accessLevel), "Approved ${user.username}") {
                onSuccess()
                _pending.value = users.getPending()
                onUsersChanged(user.identifier)
                loadOverview()
            }
        }
    }

    /** Turns down a request or cancels an invite */
    fun reject(user: User) {
        scope.launch {
            report(users.reject(user.identifier), "Removed ${user.username}") {
                _pending.value = users.getPending()
                onUsersChanged(null)
                loadOverview()
            }
        }
    }

    /**
     * Makes an invite
     * @param onCreated Gets the invite with its code, which is only shown this once
     */
    fun invite(request: InviteRequest, onCreated: (InviteResponse) -> Unit) {
        scope.launch {
            when (val result = users.invite(request)) {
                is Result.Success -> {
                    onCreated(result.data)
                    _pending.value = users.getPending()
                    onUsersChanged(result.data.user.identifier)
                    loadOverview()
                    loadAudit(reset = true)
                }

                is Result.Error -> notify(result.error.message)
            }
        }
    }

    /** New code for an invite, the old one stops working */
    fun renewInvite(user: User, onCreated: (InviteResponse) -> Unit) {
        scope.launch {
            when (val result = users.renewInvite(user.identifier)) {
                is Result.Success -> {
                    onCreated(result.data)
                    _pending.value = users.getPending()
                    loadAudit(reset = true)
                }

                is Result.Error -> notify(result.error.message)
            }
        }
    }

    // ------------------------------------------ PINs and stations ------------------------------------------

    fun unlockPin(user: User) {
        scope.launch {
            report(users.unlockPin(user.identifier), "Unlocked ${user.username}'s PIN") {
                onUsersChanged(user.identifier)
            }
        }
    }

    /** Clears their PIN so they set a new one from their settings */
    fun resetPin(user: User) {
        scope.launch {
            report(users.resetPin(user.identifier), "Cleared ${user.username}'s PIN") {
                onUsersChanged(user.identifier)
            }
        }
    }

    fun refreshStations() {
        scope.launch { _stations.value = admin.getStations() }
    }

    fun approveStation(station: Station) {
        scope.launch {
            report(admin.approveStation(station.identifier), "Approved ${station.name}") {
                _stations.value = admin.getStations()
            }
        }
    }

    fun setKiosk(station: Station, settings: KioskSettings) {
        val message = if (settings.enabled) "Kiosk mode on for ${station.name}" else "Kiosk mode off for ${station.name}"

        scope.launch {
            report(admin.setKiosk(station.identifier, settings), message) {
                _stations.value = admin.getStations()
            }
        }
    }

    fun removeStation(station: Station) {
        scope.launch {
            report(admin.removeStation(station.identifier), "Removed ${station.name}") {
                _stations.value = admin.getStations()
                _sessions.value = admin.getSessions()
            }
        }
    }

    // ------------------------------------------ Sessions ------------------------------------------

    /** Session this device is using, so it can be labelled */
    fun currentSessionId(): String? = remoteHandler.currentSessionId()

    fun revokeSession(session: SessionResponse) {
        val device = session.deviceName.ifBlank { "a device" }

        scope.launch {
            report(admin.revokeSession(session.sessionId), "Logged out ${session.username} on $device") {
                _sessions.value = admin.getSessions()
                loadOverview()
            }
        }
    }

    fun revokeAllSessions(user: User) {
        scope.launch {
            report(admin.revokeUserSessions(user.identifier), "Logged ${user.username} out everywhere") {
                _sessions.value = admin.getSessions()
                loadOverview()
            }
        }
    }

    // ------------------------------------------ Server and data ------------------------------------------

    fun saveShopProfile(profile: ShopProfile) {
        scope.launch {
            report(admin.updateShopProfile(profile), "Shop profile saved") {
                _shopProfile.value = profile
            }
        }
    }

    /**
     * Has the server build a backup, then streams it to a file picked here (or hands it to the browser)
     * @param includeImages Photos too, off for a quick database only copy
     */
    fun downloadBackup(includeImages: Boolean) {
        scope.launch {
            _isLoading.value = true
            _backupProgress.value = BackupProgress("Building the backup on the server")

            when (val prepared = admin.prepareBackup(includeImages)) {
                is Result.Error -> notify("Backup failed: ${prepared.error.message}")

                is Result.Success -> {
                    val ticket = prepared.data
                    _backupProgress.value = BackupProgress("Downloading ${formatBytes(ticket.sizeBytes)}", 0f)

                    val outcome = saveDownload(
                        client = remoteHandler.httpClient,
                        url = admin.backupDownloadUrl(ticket),
                        fileName = ticket.fileName,
                        expectedBytes = ticket.sizeBytes
                    ) { received, total ->
                        val size = total ?: ticket.sizeBytes
                        _backupProgress.value = BackupProgress(
                            text = "Downloading ${formatBytes(received)} of ${formatBytes(size)}",
                            fraction = if (size > 0) received.toFloat() / size else null
                        )
                    }

                    when (outcome) {
                        is DownloadOutcome.Saved -> notify("Backup saved (${formatBytes(outcome.bytes)})")
                        DownloadOutcome.HandedToBrowser -> notify("Backup downloading in the browser")
                        DownloadOutcome.Cancelled -> {
                            AppLogger.i(BACKUP_TAG, "Not saved")
                            notify("Backup wasn't saved")
                        }
                        is DownloadOutcome.Failed -> notify("Backup download failed: ${outcome.error.message}")
                    }
                }
            }

            _backupProgress.value = null
            _isLoading.value = false
        }
    }

    /**
     * Sends a backup archive to the server to restore from. The server checks it and swaps it in the next time
     * it starts, keeping the current data aside
     */
    fun restoreFrom(file: PlatformFile) {
        scope.launch {
            _isLoading.value = true
            _backupProgress.value = BackupProgress("Uploading ${file.name}", 0f)

            val result = admin.uploadRestore(fileBody(file, ContentType.Application.Zip)) { sent, total ->
                _backupProgress.value = BackupProgress(
                    text = total?.let { "Uploading ${formatBytes(sent)} of ${formatBytes(it)}" } ?: "Uploading ${formatBytes(sent)}",
                    fraction = total?.takeIf { it > 0 }?.let { sent.toFloat() / it }
                )
            }

            when (result) {
                is Result.Success -> {
                    _pendingRestore.value = result.data
                    notify("Backup ready. Restart the server to restore it")
                }

                is Result.Error -> notify("Restore failed: ${result.error.message}")
            }

            _backupProgress.value = null
            _isLoading.value = false
        }
    }

    /** Drops the restore waiting for a restart */
    fun cancelRestore() {
        scope.launch {
            when (val result = admin.cancelRestore()) {
                is Result.Success -> {
                    _pendingRestore.value = null
                    notify("Restore cancelled")
                }

                is Result.Error -> notify(result.error.message)
            }
        }
    }

    /** Fills a development server with mock data */
    fun seed(request: SeedRequest, onSuccess: () -> Unit) {
        scope.launch {
            _isLoading.value = true

            report(admin.seed(request), "Added mock data") {
                onSuccess()
                onUsersChanged(null)
                loadOverview()
                loadLowStock()
            }

            _isLoading.value = false
        }
    }

    // Snackbars suspend until dismissed, so show them on the side
    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // Shows how a request went and runs onSuccess if it worked
    private suspend fun report(result: EmptyResult<ServerError>, successMessage: String, onSuccess: suspend () -> Unit) {
        when (result) {
            is Result.Success -> {
                onSuccess()
                notify(successMessage)
            }

            is Result.Error -> notify(result.error.message)
        }

        loadAudit(reset = true)
    }

    companion object {
        const val DEFAULT_LOW_STOCK_THRESHOLD = 5
        val LOW_STOCK_RANGE = 0..10_000
        private const val LOW_STOCK_LIMIT = 200
        private const val RECENT_TICKET_COUNT = 10
        private const val AUDIT_PAGE_SIZE = 50
    }
}

/**
 * A running backup, for the backup card
 * @param fraction 0 to 1 once the download size is known, null while the server builds it
 */
data class BackupProgress(
    val text: String,
    val fraction: Float? = null
)
