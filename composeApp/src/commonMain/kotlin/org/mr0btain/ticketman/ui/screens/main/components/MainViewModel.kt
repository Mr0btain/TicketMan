package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerCache
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.inventory.InventoryCache
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.ticket.TicketCache
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.data.user.UserCache
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.components.network.ConnectionResult
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.screens.admin.components.AdminHandler
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import kotlin.uuid.Uuid

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app
 */

@Suppress("PrivatePropertyName")
class MainViewModel(
    val snackBarHost: SnackbarHostState,
    private val localStorage: LocalStorage
) : ViewModel() {
    private val TAG = "MainViewModel"
    private val INIT_TAG = "$TAG:Init"
    private val CUSTOMER_PAGER_TAG = "CustomerPager"
    private val TICKET_PAGER_TAG = "TicketPager"
    private val INVENTORY_PAGER_TAG = "InventoryPagerTag"
    private val USER_PAGER_TAG = "UserPagerTag"

    fun init() {
        // When the session can't be refreshed, ask the user to log in again
        viewModelScope.launch {
            remoteHandler.authExpired.collect {
                _loginState.value = LoginState.LOGGED_OUT
                _currentUser.value = null
                _showLogin.value = true
            }
        }

        viewModelScope.launch {
            // Pull saved settings
            _darkMode.value = localStorage.darkMode
            _serverUrl.value = localStorage.serverUrl
            _rememberLogin.value = localStorage.rememberLogin
            _defaultTab.value = localStorage.defaultTab
            _pageSize.value = localStorage.pageSize
            _searchLimit.value = localStorage.searchLimit
            _cacheSize.value = localStorage.cacheSize
            _selectedMainTabIndex.value = localStorage.defaultTab

            // Set url in all our handlers and log in with any stored credentials
            handleConnectionResult(remoteHandler.setUrl(localStorage.serverUrl))

            // Initialize pagers
            buildPagers()

            // Pagers don't load themselves, so do the first load now that we're connected
            refreshPagers()
        }
    }

    /** ----------------------------------------------OPERATIONS------------------------------------------------------------------- */

    // Whether we should log exposed transactions or not
    val shouldLog = false

    // Max size of cache. Caches are built with the view model, so changes apply on next start
    val maxCacheSize = localStorage.cacheSize

    /** (Re)builds all the pagers using the current page size */
    private fun buildPagers() {
        val config = OffsetPagerConfig(pageSize = _pageSize.value)

        customerPager = OffsetPager(
            tag = CUSTOMER_PAGER_TAG,
            coroutineScope = viewModelScope,
            config = config,
            fetch = { page, pageSize ->
                remoteHandler.customerRepo.fetch(
                    offset = (page * pageSize).toLong(),
                    limit = pageSize
                )
            }
        )

        ticketPager = OffsetPager(
            tag = TICKET_PAGER_TAG,
            coroutineScope = viewModelScope,
            config = config,
            fetch = { page, pageSize ->
                remoteHandler.ticketRepo.fetch(
                    offset = (page * pageSize).toLong(),
                    limit = pageSize
                )
            }
        )

        inventoryPager = OffsetPager(
            tag = INVENTORY_PAGER_TAG,
            coroutineScope = viewModelScope,
            config = config,
            fetch = { page, pageSize ->
                remoteHandler.inventoryRepo.fetch(
                    offset = (page * pageSize).toLong(),
                    limit = pageSize
                )
            }
        )

        userPager = OffsetPager(
            tag = USER_PAGER_TAG,
            coroutineScope = viewModelScope,
            config = config,
            fetch = { page, pageSize ->
                remoteHandler.userRepo.fetch(
                    offset = (page * pageSize).toLong(),
                    limit = pageSize
                )
            }
        )
    }

    fun refreshPagers() {
        viewModelScope.launch {
            customerPager?.refresh()
            ticketPager?.refresh()
            userPager?.refresh()
            inventoryPager?.refresh()
        }
    }

    /** ----------------------------------------------REMOTE------------------------------------------------------------------- */

    val remoteHandler = RemoteHandler(
        engine = getHttpEngine(),
        localStorage = localStorage
    )

    /** ----------------------------------------------CUSTOMER------------------------------------------------------------------- */

    val customerCache: CustomerCache = CustomerCache(maxCacheSize)

    var customerPager: OffsetPager<Customer>? = null

    val customerSeed: CustomerSeed = CustomerSeed()

    fun handleCustomerChanges() {
        // If we haven't made any changes, then just return
        if (!customerSeed.isDirty) return

        viewModelScope.launch {
            // Check if it's new or not
            if (customerSeed.isNew) {
                // Insert customer
                remoteHandler.customerRepo.add(customerSeed.customer)

                // Add notes
                customerSeed.notes.forEach { note ->
                    remoteHandler.customerRepo.addNote( customerSeed.identifier, note)
                }


                // TODO: Setup way to check if these tickets are new or not.
                // Right now we assume these tickets have already been inserted into the database. Here we just link them
                // in the customer side
                // Link tickets
                customerSeed.tickets.forEach { ticket ->
                    remoteHandler.customerRepo.addTicket(customerSeed.identifier, ticket.identifier)
                }
            } else { // Else handle changes
                customerSeed.getChangedFields().forEach { changedField ->
                    when (changedField) {
                        ChangedField.NOTE -> {} // TODO
                        ChangedField.TICKET -> {} // TODO

                        else -> {
                            // TODO: setup update
                        }
                    }
                }
            }
        }
    }

    /** Sets the selected customer and inserts the data into the cache */
    fun setSelectedCustomer(identifier: Uuid) {
        viewModelScope.launch {
            // Get customer from cache if we can and set the values
            val cached = customerCache.get(identifier)?.let { (customer, notes, tickets) ->
                customerSeed.setCustomer(
                    customer = customer,
                    notes = notes,
                    tickets = tickets
                )
            }

            // Else if we didn't have it in cache, pull it and add it to cache
            if (cached == null) {
                remoteHandler.customerRepo.get(identifier)?.let { customer ->
                    val notes = remoteHandler.customerRepo.getNotes(identifier)
                    val tickets = remoteHandler.customerRepo.getTickets(identifier)

                    // Set selected
                    customerSeed.setCustomer(customer, notes, tickets)

                    // Add to cache
                    customerCache.put(customer, notes, tickets)
                }
            }
        }
    }

    suspend fun getCustomer(identifier: Uuid): Customer? =
        customerCache.get(identifier)?.customer
            ?: remoteHandler.customerRepo.get(identifier)

    suspend fun searchCustomers(query: String) = coroutineScope {
        remoteHandler.customerRepo.search(query, _searchLimit.value)
    }

    fun removeCustomer(identifier: Uuid) {
        viewModelScope.launch {
            if (customerSeed.identifier == identifier) {
                customerSeed.clear()
                remoteHandler.customerRepo.remove(identifier)

                // Refresh pager data
                customerPager?.refreshCurrentData()
            }
        }
    }

    /** ----------------------------------------------TICKET------------------------------------------------------------------- */

    val ticketCache = TicketCache(maxCacheSize)

    var ticketPager: OffsetPager<Ticket>? = null

    val ticketSeed: TicketSeed = TicketSeed()

    /** Sets selected ticket and adds it to the cache */
    fun setSelectedTicket(identifier: Uuid) {
        viewModelScope.launch {
            val cached = ticketCache.get(identifier)?.let { (ticket, notes, customer, user) ->
                ticketSeed.setTicket(
                    ticket = ticket,
                    notes = notes,
                    customerId = customer,
                    userId = user
                )
            }

            // If cache didn't have ticket, pull it and add to cache
            if (cached == null) {
                remoteHandler.ticketRepo.get(identifier)?.let { ticket ->
                    // Get notes
                    val notes = remoteHandler.ticketRepo.getNotes(identifier)
                    val customer = remoteHandler.ticketRepo.getCustomer(identifier)
                    val user = remoteHandler.ticketRepo.getUser(identifier)

                    // Set seed data
                    ticketSeed.setTicket(ticket, notes, customer, user)

                    // Insert into cache
                    ticketCache.put(ticket, notes, customer, user)
                }
            }
        }
    }

    fun handleTicketChange() {
        // If no changes, then return
        if (!ticketSeed.isDirty) return

        viewModelScope.launch {
            if (ticketSeed.isNew) {
                remoteHandler.ticketRepo.add(ticketSeed.ticket)

                ticketSeed.notes.forEach { note ->
                    remoteHandler.ticketRepo.addNote(ticketSeed.identifier, note)
                }
            }

            ticketSeed.getChangedFields().forEach { changedField ->
                when (changedField) {
                    ChangedField.NOTE -> {}
                    ChangedField.CUSTOMER -> {
                       ticketSeed.customer?.identifier?.let { customerId ->
                           remoteHandler.ticketRepo.setUser(ticketSeed.identifier, customerId)
                       }
                    }
                    ChangedField.USER -> {
                        ticketSeed.user?.identifier?.let { userId ->
                            remoteHandler.ticketRepo.setUser(ticketSeed.identifier, userId)
                        }
                    }

                    else -> {
                        // TODO: Prolly just update?
                    }
                }
            }
        }
    }

    suspend fun searchTickets(query: String) = coroutineScope {
        remoteHandler.ticketRepo.search(query, _searchLimit.value)
    }

    fun removeTicket(identifier: Uuid) {
        viewModelScope.launch {
            if (ticketSeed.identifier == identifier) {
                ticketSeed.clear()
                remoteHandler.ticketRepo.remove(identifier)
                ticketPager?.refreshCurrentData()
            }
        }
    }

    /** ----------------------------------------------INVENTORY------------------------------------------------------------------- */

    val inventoryCache = InventoryCache(maxCacheSize)

    var inventoryPager: OffsetPager<Inventory>? = null

    val inventorySeed: InventorySeed = InventorySeed()

    fun setSelectedInventory(identifier: Uuid) {
        viewModelScope.launch {
           val cached = inventoryCache.get(identifier)?.let { (inventory, notes) ->
               inventorySeed.setInitial(inventory, notes)
           }

            if (cached == null) {
                remoteHandler.inventoryRepo.get(identifier)?.let { inventory ->
                    val notes = remoteHandler.inventoryRepo.getNotes(identifier)

                    inventorySeed.setInitial(inventory, notes)

                    inventoryCache.put(inventory, notes)
                }
            }
        }
    }

    fun handleInventoryChange() {
        if (!inventorySeed.isDirty) return

        viewModelScope.launch {
            if (inventorySeed.isNew) {
                remoteHandler.inventoryRepo.add(inventorySeed.inventory)

                inventorySeed.images.forEach { image ->
                    remoteHandler.inventoryRepo.addImage(inventorySeed.identifier, image)
                }

                inventorySeed.notes.forEach { note ->
                    remoteHandler.inventoryRepo.addNote(inventorySeed.identifier, note)
                }
            } else {
                inventorySeed.getChangedFields().forEach { changedField ->
                    when (changedField) {
                        ChangedField.NOTE -> {
                            // TODO: Setup way to see which one has changed
                            inventorySeed.notes.forEach { note ->
                                TODO("Not yet implemented")
                            }
                        }
                        ChangedField.IMAGE -> {
                            inventorySeed.images.forEach { image ->
                                TODO("Not yet implemented")
                            }
                        }
                        else -> {
                            // TODO()
                        }
                    }
                }
            }
        }
    }

    suspend fun searchInventory(query: String) = coroutineScope {
        remoteHandler.inventoryRepo.search(query, _searchLimit.value)
    }

    fun removeInventory(identifier: Uuid) {
        viewModelScope.launch {
            if (inventorySeed.identifier == identifier) {
                inventorySeed.clear()
                remoteHandler.inventoryRepo.remove(identifier)
                inventoryPager?.refreshCurrentData()
            }
        }
    }

    /** ----------------------------------------------USER------------------------------------------------------------------- */

    val userCache: UserCache = UserCache(maxCacheSize)

    var userPager: OffsetPager<User>? = null

    val userSeed: UserSeed = UserSeed()

    /**
     * Sets selected user and adds it to the cache
     * @param force Reload even if it's already selected, used after the user was changed
     */
    fun setSelectedUser(identifier: Uuid, force: Boolean = false) {
        if (!force && userSeed.identifier == identifier) return

        viewModelScope.launch {
            val cached = userCache.get(identifier)?.let { (user, notes) ->
                userSeed.setUser(user, notes)
            }

            if (cached == null) {
                remoteHandler.userRepo.get(identifier)?.let { user ->
                    val notes = remoteHandler.userRepo.getNotes(identifier)

                    userSeed.setUser(user, notes)

                    userCache.put(user, notes)
                }
            }
        }
    }

    suspend fun searchUsers(query: String) = coroutineScope {
        remoteHandler.userRepo.search(query, _searchLimit.value)
    }

    /** Drops stale copies of a changed user and reloads the list, [identifier] is null when the user is gone */
    private fun onUsersChanged(identifier: Uuid?) {
        viewModelScope.launch {
            if (identifier != null) {
                userCache.remove(identifier)
                if (userSeed.identifier == identifier) setSelectedUser(identifier, force = true)

                // Admins can edit themselves, keep what we show for the current user in step
                if (identifier == _currentUser.value?.identifier) loadCurrentUser()
            } else {
                userSeed.clear()
            }

            userPager?.refreshCurrentData()
        }
    }

    /** ----------------------------------------------ADMIN------------------------------------------------------------------- */

    val admin = AdminHandler(
        remoteHandler = remoteHandler,
        scope = viewModelScope,
        snackBarHost = snackBarHost,
        onUsersChanged = { identifier -> onUsersChanged(identifier) }
    )

    /** ----------------------------------------------UI------------------------------------------------------------------- */

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }

    private val _showLogin: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    /** Update whether we should show the login popup or not*/
    fun changeShowLogin(value: Boolean) {
        _showLogin.value = value
    }

    /** ----------------------------------------------LOGIN------------------------------------------------------------------- */

    private val _loginState: MutableStateFlow<LoginState> = MutableStateFlow(LoginState.LOGGED_OUT)
    val loginState = _loginState.asStateFlow()

    // Who is logged in, null when logged out
    private val _currentUser: MutableStateFlow<User?> = MutableStateFlow(null)
    val currentUser = _currentUser.asStateFlow()

    /** Whether the logged in user can use the admin screen. The server checks this too, this just hides things */
    val isAdmin: StateFlow<Boolean> = _currentUser
        .map { user -> user?.accessLevel?.meets(AccessLevel.ADMIN) == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private suspend fun loadCurrentUser() {
        _currentUser.value = remoteHandler.userRepo.getMe()
    }

    // Whether the server at the current url could be reached
    private val _serverConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val serverConnected = _serverConnected.asStateFlow()

    /** Username saved from the last login, blank unless remember login is on */
    val savedUsername: String
        get() = localStorage.username

    /** Logs in with the given credentials. Credentials are only saved if remember login is on */
    fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            _loginState.value = LoginState.FAILED
            return
        }

        _loginState.value = LoginState.LOADING

        viewModelScope.launch {
            if (remoteHandler.login(user.trim(), pass)) {
                _loginState.value = LoginState.LOGGED_IN
                _showLogin.value = false
                loadCurrentUser()
                refreshPagers()
            } else {
                _loginState.value = LoginState.FAILED
            }
        }
    }

    /** Ends the session and clears out any loaded data */
    fun logout() {
        viewModelScope.launch {
            remoteHandler.logout()
            _loginState.value = LoginState.LOGGED_OUT
            _currentUser.value = null
            admin.clear()

            // Drop anything pulled while logged in
            customerSeed.clear()
            ticketSeed.clear()
            inventorySeed.clear()
            userSeed.clear()
            buildPagers()
        }
    }

    private suspend fun handleConnectionResult(result: ConnectionResult) {
        _serverConnected.value = result != ConnectionResult.UNREACHABLE

        if (result == ConnectionResult.LOGGED_IN) {
            _loginState.value = LoginState.LOGGED_IN
            _showLogin.value = false
            loadCurrentUser()
        } else {
            _loginState.value = LoginState.LOGGED_OUT
            _currentUser.value = null
        }
    }

    /** ----------------------------------------------SETTINGS------------------------------------------------------------------- */

    private val _darkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val darkMode = _darkMode.asStateFlow()

    private val _serverUrl: MutableStateFlow<String> = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()

    private val _rememberLogin: MutableStateFlow<Boolean> = MutableStateFlow(LocalStorage.DEFAULT_REMEMBER_LOGIN)
    val rememberLogin = _rememberLogin.asStateFlow()

    private val _defaultTab: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_TAB)
    val defaultTab = _defaultTab.asStateFlow()

    private val _pageSize: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_PAGE_SIZE)
    val pageSize = _pageSize.asStateFlow()

    private val _searchLimit: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_SEARCH_LIMIT)
    val searchLimit = _searchLimit.asStateFlow()

    private val _cacheSize: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_CACHE_SIZE)
    val cacheSize = _cacheSize.asStateFlow()

    private val _scrollBarsOnBottom: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnBottom = _scrollBarsOnBottom.asStateFlow()

    private val _scrollBarsOnRight: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnRight = _scrollBarsOnRight.asStateFlow()

    fun toggleDarkMode() {
        val value = !_darkMode.value
        _darkMode.value = value
        localStorage.darkMode = value
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url
        localStorage.serverUrl = url

        _loginState.value = LoginState.LOADING

        viewModelScope.launch {
            handleConnectionResult(remoteHandler.setUrl(url))
            // Reload lists from the new server
            refreshPagers()
        }
    }

    fun toggleRememberLogin() {
        val value = !_rememberLogin.value
        _rememberLogin.value = value
        localStorage.rememberLogin = value

        // Forget anything we've already stored
        if (!value) {
            viewModelScope.launch { localStorage.clearCredentials() }
        }
    }

    /** Sets which main tab is shown on start, wraps around past the last tab */
    fun setDefaultTab(index: Int) {
        val value = index.mod(MAIN_TAB_COUNT)
        _defaultTab.value = value
        localStorage.defaultTab = value
    }

    fun setPageSize(size: Int) {
        val value = size.coerceIn(PAGE_SIZE_RANGE)
        _pageSize.value = value
        localStorage.pageSize = value

        // Pagers hold their config, so rebuild them with the new size
        buildPagers()
        refreshPagers()
    }

    fun setSearchLimit(limit: Int) {
        val value = limit.coerceIn(SEARCH_LIMIT_RANGE)
        _searchLimit.value = value
        localStorage.searchLimit = value
    }

    /** Cache size is only read when the view model is built, so this applies on next start */
    fun setCacheSize(size: Int) {
        val value = size.coerceIn(CACHE_SIZE_RANGE)
        _cacheSize.value = value
        localStorage.cacheSize = value
    }

    fun toggleScrollBarsOnBottom() {
        val value = !_scrollBarsOnBottom.value
        _scrollBarsOnBottom.value = value
    }

    fun toggleScrollBarsOnRight() {
        val value = !_scrollBarsOnRight.value
        _scrollBarsOnRight.value = value
    }

    /** ----------------------------------------------TESTING------------------------------------------------------------------- */

    companion object {
        const val MAIN_TAB_COUNT = 3
        val PAGE_SIZE_RANGE = 5..100
        val SEARCH_LIMIT_RANGE = 1..100
        val CACHE_SIZE_RANGE = 1..200
    }

}