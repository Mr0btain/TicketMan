package org.mr0btain.ticketman.ui

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.image.ImageClient
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.ServerTrust
import org.mr0btain.ticketman.components.network.defaultServerAddress
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.ui.screens.admin.components.AdminHandler
import org.mr0btain.ticketman.ui.screens.counter.components.CounterHandler
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerRecords
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryRecords
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketRecords
import org.mr0btain.ticketman.ui.screens.records.components.user.UserRecords
import org.mr0btain.ticketman.ui.screens.kiosk.components.KioskHandler
import org.mr0btain.ticketman.ui.screens.login.components.SessionHandler
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsHandler

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app. Each part of the app has its own
 * handler here, this wires them together
 */
class MainViewModel(
    val snackBarHost: SnackbarHostState,
    private val localStorage: LocalStorage,
    /** Tests pass a mock engine, the app uses the platform's */
    engine: HttpClientEngine? = null
) : ViewModel() {
    /**
     * Starts the app once the view model is built: follows expired sessions, loads the saved settings and connects
     * to the saved server. Called by initMainViewModel, not by screens
     */
    fun init() {
        // When the session can't be refreshed, ask the user to log in again
        viewModelScope.launch {
            remoteHandler.authExpired.collect {
                // A kiosk goes back to its PIN screen instead of asking for a password
                if (kiosk.active.value) kiosk.lock() else session.expired()
            }
        }

        viewModelScope.launch {
            settings.load()
            tickets.load()
            _selectedMainTabIndex.value = localStorage.defaultTab

            // Set url in all our handlers and log in with any stored credentials. With nothing saved, the web
            // client tries the server it was loaded from and keeps that address if a server answers there
            val savedUrl = localStorage.serverUrl
            val url = savedUrl.ifBlank { defaultServerAddress() }
            session.connect(url)
            if (savedUrl.isBlank() && session.serverConnected.value) settings.rememberServerUrl(url)

            // Pagers don't load themselves, so do the first load now that we're connected
            refreshPagers()
        }
    }

    /** ----------------------------------------------REMOTE------------------------------------------------------------------- */

    private val trust = ServerTrust(localStorage)

    val remoteHandler = RemoteHandler(
        engine = engine ?: getHttpEngine(trust),
        localStorage = localStorage,
        trust = trust
    )

    /** Uploads photos and finds where they're fetched from, handed to screens through LocalImageClient */
    val imageClient = ImageClient(remoteHandler.imageRepo, remoteHandler.httpClient)

    /** ----------------------------------------------RECORDS------------------------------------------------------------------- */

    // Caches are built with the view model, so cache size changes apply on next start
    private val cacheSize = localStorage.cacheSize

    // Every pager is built once with the saved page size and lives as long as the view model, so screens can
    // collect their items right away and keep getting updates after logins, refreshes and page size changes
    private val pagerConfig = OffsetPagerConfig(pageSize = localStorage.pageSize)

    private val searchLimit = { settings.searchLimit.value }

    val customers = CustomerRecords(remoteHandler, viewModelScope, snackBarHost, { session.canEditNow() }, pagerConfig, cacheSize, searchLimit)

    val tickets = TicketRecords(remoteHandler, localStorage, viewModelScope, snackBarHost, { session.canEditNow() }, pagerConfig, cacheSize, searchLimit)

    val inventory = InventoryRecords(remoteHandler, viewModelScope, snackBarHost, { session.canEditNow() }, pagerConfig, cacheSize, searchLimit)

    val users = UserRecords(remoteHandler, viewModelScope, snackBarHost, { session.isAdminNow() }, pagerConfig, cacheSize, searchLimit)

    /** Reloads every record list from the start, after logging in or when the page size changes */
    fun refreshPagers() {
        viewModelScope.launch {
            customers.pager.refresh()
            tickets.pager.refresh()
            users.pager.refresh()
            inventory.pager.refresh()
        }
    }

    /** Loaded pages were fetched with the old size, so reload them with the new one */
    private fun changePageSize(size: Int) {
        customers.pager.setPageSize(size)
        tickets.pager.setPageSize(size)
        inventory.pager.setPageSize(size)
        users.pager.setPageSize(size)
        refreshPagers()
    }

    /** ----------------------------------------------SESSION------------------------------------------------------------------- */

    val session: SessionHandler = SessionHandler(
        remoteHandler = remoteHandler,
        localStorage = localStorage,
        scope = viewModelScope,
        onLoggedIn = { refreshPagers() },
        onLoggedOut = {
            admin.clear()
            counter.clear()
            customers.clear()
            tickets.clear()
            inventory.clear()
            users.clear()
        }
    )

    val settings: SettingsHandler = SettingsHandler(
        localStorage = localStorage,
        scope = viewModelScope,
        onServerUrlChanged = { url -> session.changeServer(url) },
        onPageSizeChanged = { size -> changePageSize(size) }
    )

    /** ----------------------------------------------COUNTER------------------------------------------------------------------- */

    val counter: CounterHandler = CounterHandler(
        remoteHandler = remoteHandler,
        scope = viewModelScope,
        snackBarHost = snackBarHost,
        canEdit = { session.canEditNow() }
    )

    /** ----------------------------------------------ADMIN------------------------------------------------------------------- */

    val admin: AdminHandler = AdminHandler(
        remoteHandler = remoteHandler,
        scope = viewModelScope,
        snackBarHost = snackBarHost,
        onUsersChanged = { identifier ->
            users.onChanged(identifier)

            // Admins can edit themselves, keep what we show for the current user in step
            if (identifier != null && identifier == session.currentUser.value?.identifier) {
                viewModelScope.launch { session.reloadCurrentUser() }
            }
        }
    )

    /** ----------------------------------------------KIOSK------------------------------------------------------------------- */

    val kiosk: KioskHandler = KioskHandler(
        remoteHandler = remoteHandler,
        scope = viewModelScope,
        station = session.station,
        onSignedIn = { session.signedIn() },
        onSignedOut = { session.signedOut() },
        isAdmin = { session.isAdminNow() }
    )

    /** ----------------------------------------------UI------------------------------------------------------------------- */

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    /** Switches the records screen to Customers (0), Tickets (1) or Inventory (2) */
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }
}
