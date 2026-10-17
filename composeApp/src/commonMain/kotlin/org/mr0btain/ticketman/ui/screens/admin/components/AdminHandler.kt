package org.mr0btain.ticketman.ui.screens.admin.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.saveFile
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.AdminStatsResponse
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

    private val _stats = MutableStateFlow<AdminStatsResponse?>(null)
    val stats = _stats.asStateFlow()

    private val _lowStockThreshold = MutableStateFlow(DEFAULT_LOW_STOCK_THRESHOLD)
    val lowStockThreshold = _lowStockThreshold.asStateFlow()

    private val _lowStock = MutableStateFlow<List<Inventory>>(emptyList())
    val lowStock = _lowStock.asStateFlow()

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
            _sessions.value = admin.getSessions()
            _serverInfo.value = admin.getServerInfo()
            _shopProfile.value = admin.getShopProfile()
            loadAudit(reset = true)

            _isLoading.value = false
        }
    }

    fun refreshOverview() {
        scope.launch { loadOverview() }
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
        scope.launch { _serverInfo.value = admin.getServerInfo() }
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
        _lowStock.value = emptyList()
        _sessions.value = emptyList()
        _serverInfo.value = null
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
     * Deactivating sets access to NONE which blocks logging in and ends their session.
     * Reactivating brings them back as RESTRICTED, edit them after to give more access.
     */
    fun setUserActive(user: User, active: Boolean) {
        val accessLevel = if (active) AccessLevel.RESTRICTED else AccessLevel.NONE
        val message = if (active) "Reactivated ${user.username} as restricted" else "Deactivated ${user.username}"

        scope.launch {
            report(users.update(user.withoutPassword().copy(accessLevel = accessLevel)), message) {
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

    // ------------------------------------------ Sessions ------------------------------------------

    fun revokeSession(session: SessionResponse) {
        scope.launch {
            report(admin.revokeSession(session.userIdentifier), "Logged out ${session.username}") {
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

    /** Pulls a backup from the server and asks where to save it */
    fun downloadBackup() {
        scope.launch {
            _isLoading.value = true

            when (val result = admin.downloadBackup()) {
                is Result.Success -> {
                    val name = "ticketman-backup-${Clock.System.now().toEpochMilliseconds()}"

                    if (saveFile(result.data, name, "zip")) {
                        notify("Backup saved")
                    } else {
                        AppLogger.i(BACKUP_TAG, "Not saved")
                        notify("Backup wasn't saved")
                    }
                }

                is Result.Error -> notify("Backup failed: ${result.error.message}")
            }

            _isLoading.value = false
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
