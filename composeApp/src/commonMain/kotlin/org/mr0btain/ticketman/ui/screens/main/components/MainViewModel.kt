package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerCache
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.inventory.InventoryCache
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.components.data.ticket.TicketCache
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.data.user.UserCache
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.components.database.SettingsDatabase
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import kotlin.uuid.Uuid

// TODO: Setup log in button and log in status

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app
 */

@Suppress("PrivatePropertyName")
class MainViewModel(
    val snackBarHost: SnackbarHostState
) : ViewModel() {
    private val TAG = "MainViewModel"
    private val INIT_TAG = "$TAG:Init"
    private val CUSTOMER_PAGER_TAG = "CustomerPager"
    private val TICKET_PAGER_TAG = "TicketPager"
    private val INVENTORY_PAGER_TAG = "InventoryPagerTag"
    private val USER_PAGER_TAG = "UserPagerTag"

    fun init() {
        viewModelScope.launch {
            // Initialize settings
            settingsDatabase.initialize()

            // Pull saved settings
            settingsDatabase.getSettings()?.let { result ->
                _darkMode.value = result.darkMode
                _serverAddress.value = result.serverUrl
                _scrollBarsOnRight.value = result.scrollBarsOnRight
                _scrollBarsOnBottom.value = result.scrollBarsOnBottom
                _autoSyncDatabase.value = result.autoSync
            } ?: AppLogger.i(INIT_TAG, SimpleResult.FAIL)

            // Connect and log in before the pagers do their first fetch
            remoteHandler.setUrl(_serverAddress.value)

            // Initialize pagers
            customerPager = OffsetPager(
                tag = CUSTOMER_PAGER_TAG,
                coroutineScope = viewModelScope,
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
                fetch = { page, pageSize ->
                    remoteHandler.userRepo.fetch(
                        offset = (page * pageSize).toLong(),
                        limit = pageSize
                    )
                }
            )

            // Pagers don't load themselves, so do the first load now that we're connected
            refreshPagers()
        }
    }

    /** ----------------------------------------------OPERATIONS------------------------------------------------------------------- */

    // Whether we should log exposed transactions or not
    val shouldLog = false

    // Max size of cache
    val maxCacheSize = 20

    val localStorage = LocalStorage()

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
        remoteHandler.customerRepo.search(query, 20)
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
        remoteHandler.ticketRepo.search(query, 20)
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
        remoteHandler.inventoryRepo.search(query, 20)
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

    /** Sets selected user and adds it to the cache */
    fun setSelectedUser(identifier: Uuid) {
        if (userSeed.identifier == identifier) return

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
        remoteHandler.userRepo.search(query, 20) ?: emptyList()
    }

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

    /** ----------------------------------------------SETTINGS------------------------------------------------------------------- */

    private val settingsDatabase = SettingsDatabase(shouldLog)

    private val _darkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val darkMode = _darkMode.asStateFlow()

    private val _serverAddress: MutableStateFlow<String> = MutableStateFlow("")
    val serverAddress = _serverAddress.asStateFlow()

    private val _scrollBarsOnBottom: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnBottom = _scrollBarsOnBottom.asStateFlow()

    private val _scrollBarsOnRight: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnRight = _scrollBarsOnRight.asStateFlow()

    private val _autoSyncDatabase: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val autoSyncDatabase = _autoSyncDatabase.asStateFlow()

    fun toggleAutoSync() {
        val value = !_autoSyncDatabase.value
        _autoSyncDatabase.value = value
        saveSettings()
    }

    fun toggleDarkMode() {
        val value = !_darkMode.value
        _darkMode.value = value
        saveSettings()
    }

    fun updateServerUrl(url: String) {
        _serverAddress.value = url

        viewModelScope.launch {
            remoteHandler.setUrl(url)
            saveSettings()

            // Reload lists from the new server
            refreshPagers()
        }
    }

    fun toggleScrollBarsOnBottom() {
        val value = !_scrollBarsOnBottom.value
        _scrollBarsOnBottom.value = value
        saveSettings()
    }

    fun toggleScrollBarsOnRight() {
        val value = !_scrollBarsOnRight.value
        _scrollBarsOnRight.value = value
        saveSettings()
    }

    private fun saveSettings() {
        settingsDatabase.saveSettings(
            UserSettings(
                darkMode = _darkMode.value,
                serverUrl = _serverAddress.value,
                autoSync = _autoSyncDatabase.value,
                scrollBarsOnRight = _scrollBarsOnRight.value,
                scrollBarsOnBottom = _scrollBarsOnBottom.value
            )
        )
    }

    /** ----------------------------------------------TESTING------------------------------------------------------------------- */

}