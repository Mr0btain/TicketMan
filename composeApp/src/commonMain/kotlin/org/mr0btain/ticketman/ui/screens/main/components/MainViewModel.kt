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
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.components.data.ticket.TicketCache
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.data.user.UserData
import org.mr0btain.ticketman.components.database.SettingsDatabase
import org.mr0btain.ticketman.components.database.initDatabase
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.database.TicketDatabaseImpl
import org.mr0btain.ticketman.database.UserDatabaseImpl
import org.mr0btain.ticketman.generateMockCustomer
import org.mr0btain.ticketman.generateMockUser
import org.mr0btain.ticketman.generateRandomInventoryItem
import org.mr0btain.ticketman.generateRandomTicket
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.util.*
import kotlin.random.Random

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
    private val INIT_SYNC_TAG = "$TAG:Init_Sync"
    private val REMOVE_CUSTOMER_SUB_ITEM_TAG = "$TAG:RemoveCustomerSubItem"
    private val CUSTOMER_PAGER_TAG = "CustomerPager"
    private val TICKET_PAGER_TAG = "TicketPager"
    private val INVENTORY_PAGER_TAG = "InventoryPagerTag"
    private val USER_PAGER_TAG = "UserPagerTag"

    fun init() {
        viewModelScope.launch {
            // Initialize databases
            initDatabase()

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

            // Initialize pagers
            customerPager = OffsetPager(
                tag = CUSTOMER_PAGER_TAG,
                coroutineScope = viewModelScope,
                fetch = { page, pageSize ->
                    customerDatabase.getPagedItems(
                        offset = (page * pageSize).toLong(),
                        pageSize = pageSize
                    ) ?: emptyList()
                }
            )

            ticketPager = OffsetPager(
                tag = TICKET_PAGER_TAG,
                coroutineScope = viewModelScope,
                fetch = { page, pageSize ->
                    ticketDatabase.getPagedItems(
                        offset = (page * pageSize).toLong(),
                        pageSize = pageSize
                    ) ?: emptyList()
                }
            )

            inventoryPager = OffsetPager(
                tag = INVENTORY_PAGER_TAG,
                coroutineScope = viewModelScope,
                fetch = { page, pageSize ->
                    inventoryDatabase.getPagedItems(
                        offset = (page * pageSize).toLong(),
                        pageSize = pageSize
                    ) ?: emptyList()
                }
            )

            userPager = OffsetPager(
                tag = USER_PAGER_TAG,
                coroutineScope = viewModelScope,
                fetch = { page, pageSize ->
                    userDatabase.getPagedItems(
                        offset = (page * pageSize).toLong(),
                        pageSize = pageSize
                    ) ?: emptyList()
                }
            )
        }
    }

    /** ----------------------------------------------OPERATIONS------------------------------------------------------------------- */

    // Whether we should log exposed transactions or not
    val shouldLog = false

    // Max size of cache
    val maxCacheSize = 20

    /** ----------------------------------------------CUSTOMER------------------------------------------------------------------- */

    val customerCache: CustomerCache = CustomerCache()

    private val customerDatabase: CustomerDatabaseImpl = CustomerDatabaseImpl()

    var customerPager: OffsetPager<Customer>? = null

    val customerSeed: CustomerSeed = CustomerSeed()

    fun handleCustomerChanges() {
        // If we haven't made any changes, then just return
        if (!customerSeed.isDirty) return

        viewModelScope.launch {
            // Check if it's new or not
            if (customerSeed.isNew) {
                // Insert customer
                customerDatabase.add(customerSeed.customer)

                // Add notes
                customerSeed.notes.forEach { note ->
                    customerDatabase.addNote(customerSeed.identifier, note)
                }

                // TODO: Setup way to check if these tickets are new or not.
                // Right now we assume these tickets have already been inserted into the database. Here we just link them
                // in the customer side
                // Link tickets
                customerSeed.tickets.forEach { ticket ->
                    customerDatabase.addTicket(customerSeed.identifier, ticket.identifier)
                }
            } else { // Else handle changes
                customerSeed.getChangedFields().forEach { changedField ->
                    when (changedField) {
                        ChangedField.NAME -> customerDatabase.updateName(customerSeed.identifier, customerSeed.name)
                        ChangedField.EMAIL -> customerDatabase.updateEmail(customerSeed.identifier, customerSeed.email)
                        ChangedField.PHONE -> customerDatabase.updatePhone(customerSeed.identifier, customerSeed.phone)
                        ChangedField.NOTE -> {} // TODO
                        ChangedField.TICKET -> {} // TODO

                        else -> {} // Do nothing
                    }
                }
            }
        }
    }

    /** Sets the selected customer and inserts the data into the cache */
    fun setSelectedCustomer(identifier: UUID) {
        if (customerSeed.identifier == identifier) return

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
                customerDatabase.getById(identifier)?.let { customer ->
                    val notes = customerDatabase.getNotes(identifier) ?: emptyList()

                    val tickets = customerDatabase.getTickets(identifier)?.also { ticketList ->
                        ticketList.forEach { ticketCache.put(it) }
                    } ?: emptyList()

                    // Set selected
                    customerSeed.setCustomer(customer, notes, tickets)

                    // Add to cache
                    customerCache.put(customer, notes, tickets)
                }
            }
        }
    }

    suspend fun getCustomer(identifier: UUID): Customer? =
        customerCache.get(identifier)?.customer
            ?: customerDatabase.getById(identifier)

    suspend fun searchCustomers(query: String) = coroutineScope {
        customerDatabase.search(query, 20) ?: emptyList()
    }

    /** ----------------------------------------------TICKET------------------------------------------------------------------- */

    val ticketCache = TicketCache(maxCacheSize)

    private val ticketDatabase = TicketDatabaseImpl()

    var ticketPager: OffsetPager<Ticket>? = null

    val ticketSeed: TicketSeed = TicketSeed()

    /** Sets selected ticket and adds it to the cache */
    fun setSelectedTicket(identifier: UUID) {
        if (ticketSeed.identifier == identifier) return

        viewModelScope.launch {
            val cached = ticketCache.get(identifier)?.let { (ticket, notes) ->
                ticketSeed.setTicket(
                    ticket = ticket,
                    notes = notes
                )
            }

            // If cache didn't have ticket, pull it and add to cache
            if (cached == null) {
                ticketDatabase.getById(identifier)?.let { ticket ->
                    // Get notes
                    val notes = ticketDatabase.getNotes(identifier) ?: emptyList()

                    // Set seed data
                    ticketSeed.setTicket(ticket, notes)

                    // Insert into cache
                    ticketCache.put(ticket, notes)
                }
            }
        }
    }

    fun handleTicketChange() {
        // If no changes, then return
        if (!ticketSeed.isDirty) return

        viewModelScope.launch {
            if (ticketSeed.isNew) {
                ticketDatabase.add(ticketSeed.ticket)

                ticketSeed.notes.forEach { note ->
                    ticketDatabase.addNote(ticketSeed.identifier, note)
                }
            }

            ticketSeed.getChangedFields().forEach { changedField ->
                when (changedField) {
                    ChangedField.NOTE -> {}
                    ChangedField.STATUS -> {}
                    ChangedField.PRIORITY -> {}
                    ChangedField.DEVICE -> {}
                    ChangedField.CUSTOMER -> {}
                    ChangedField.USER -> {}

                    else -> {} // Do nothing
                }
            }
        }
    }

    suspend fun searchTickets(query: String) = coroutineScope {
        ticketDatabase.search(query, 20) ?: emptyList()
    }

    fun removeTicket(identifier: UUID) {
        viewModelScope.launch {
            if (ticketSeed.identifier == identifier) ticketSeed.clear()

            ticketDatabase.remove(identifier)
        }
    }

    /** ----------------------------------------------INVENTORY------------------------------------------------------------------- */

    val inventoryCache = InventoryCache()

    private val inventoryDatabase = InventoryDatabaseImpl()

    var inventoryPager: OffsetPager<Inventory>? = null

    val inventorySeed: InventorySeed = InventorySeed()

    fun setSelectedInventory(identifier: UUID) {
        if (inventorySeed.identifier == identifier) return

        viewModelScope.launch {
           val cached = inventoryCache.get(identifier)?.let { (inventory, notes) ->
               inventorySeed.setInitial(inventory, notes)
           }

            if (cached == null) {
                inventoryDatabase.getById(identifier)?.let { inventory ->
                    val notes = inventoryDatabase.getNotes(identifier) ?: emptyList()

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
                inventoryDatabase.add(inventorySeed.inventory)

                inventoryDatabase.addImages(inventorySeed.identifier, inventorySeed.images)

                inventorySeed.notes.forEach { note ->
                    inventoryDatabase.addNote(inventorySeed.identifier, note)
                }
            } else {
                inventorySeed.getChangedFields().forEach { changedField ->
                    when (changedField) {
                        ChangedField.NAME -> inventoryDatabase.updateName(inventorySeed.identifier, inventorySeed.name)
                        ChangedField.DESCRIPTION -> inventoryDatabase.updateDescription(inventorySeed.identifier, inventorySeed.description)
                        ChangedField.STOCK -> inventoryDatabase.updateStock(inventorySeed.identifier, inventorySeed.stock)
                        ChangedField.COST -> inventoryDatabase.updateCost(inventorySeed.identifier, inventorySeed.cost.toString())
                        ChangedField.PRICE -> inventoryDatabase.updatePrice(inventorySeed.identifier, inventorySeed.price.toString())
                        ChangedField.NOTE -> {
                            // TODO: Setup way to see which one has changed
                            inventorySeed.notes.forEach { note ->
                                inventoryDatabase.updateNote(note)
                            }
                        }
                        ChangedField.IMAGE -> {
                            inventorySeed.images.forEach { image ->
                                inventoryDatabase.updateImage(image)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun removeInventory(identifier: UUID) {
        viewModelScope.launch {
            if (inventorySeed.identifier == identifier) inventorySeed.clear()

            inventoryDatabase.remove(identifier)
        }
    }

    suspend fun searchInventory(query: String) = coroutineScope {
        inventoryDatabase.search(query, 20) ?: emptyList()
    }

    /** ----------------------------------------------USER------------------------------------------------------------------- */

    val userCache = LruCache<UUID, UserData>(maxCacheSize, "UserCache")

    private val userDatabase = UserDatabaseImpl()

    var userPager: OffsetPager<User>? = null

    /** Sets selected user and adds it to the cache */
    private suspend fun setSelectedUser(identifier: UUID) {
        // TODO
    }

    suspend fun searchUsers(query: String) = coroutineScope {
        userDatabase.search(query, 20) ?: emptyList()
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
            //dataHandler.updateServerUrl(url)
            saveSettings()
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

    fun fillDatabaseWithTestData() {
        val ticketIdentifiers = mutableListOf<UUID>()
        val ticketNum = 1000
        val customerNum = 400
        val inventoryNum = 100
        val userNum = 25

        (0..ticketNum).forEach { i ->
            // Generate random ticket
            val (ticket, notes) = generateRandomTicket()

            // Add ticket to database
            ticketDatabase.add(ticket)

            // Add notes
            notes.forEach { note ->
                ticketDatabase.addNote(ticket.identifier, note)
            }

            // Insert id into list
            ticketIdentifiers.add(ticket.identifier)
        }

        (0..customerNum).forEach { _ ->
            // Generate random customer
            val (customer, notes) = generateMockCustomer()

            // Add customer
            customerDatabase.add(customer)

            // Add notes
            notes.forEach { note ->
                customerDatabase.addNote(customer.identifier, note)
            }

            // Add random tickets
            (0..Random.nextInt(1, 6)).forEach { _ ->
                customerDatabase.addTicket(customer.identifier, ticketIdentifiers.random())
            }
        }

        (0..inventoryNum).forEach { _ ->
            val (inventory, notes) = generateRandomInventoryItem()

            println(inventory)

            inventoryDatabase.add(inventory)

            notes.forEach { note ->
                inventoryDatabase.addNote(inventory.identifier, note)
            }
        }

        (0..userNum).forEach { _ ->
            // Generate random user
            val (user, notes) = generateMockUser()

            // Add user to database
            userDatabase.add(user)

            //Add notes
            notes.forEach { note ->
                userDatabase.addNote(user.identifier, note)
            }

            // Add random tickets
            (0..Random.nextInt(4, 12)).forEach { _ ->
                userDatabase.addTicket(user.identifier, ticketIdentifiers.random())
            }
        }
    }
}