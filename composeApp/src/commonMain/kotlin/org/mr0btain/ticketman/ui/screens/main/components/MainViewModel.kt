package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.inventory.InventoryData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.components.data.user.UserData
import org.mr0btain.ticketman.components.database.SettingsDatabase
import org.mr0btain.ticketman.components.database.initDatabase
import org.mr0btain.ticketman.components.pagination.CustomPagingSource
import org.mr0btain.ticketman.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.database.TicketDatabaseImpl
import org.mr0btain.ticketman.database.UserDatabaseImpl
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.generateMockCustomer
import org.mr0btain.ticketman.generateMockUser
import org.mr0btain.ticketman.generateRandomInventoryItem
import org.mr0btain.ticketman.generateRandomTicket
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Setup log in button and log in status

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app
 */

@OptIn(ExperimentalUuidApi::class)
class MainViewModel(
    val snackBarHost: SnackbarHostState
) : ViewModel() {
    private val TAG = "MainViewModel"
    private val INIT_TAG = "$TAG:Init"
    private val INIT_SYNC_TAG = "$TAG:Init_Sync"

    val shouldLog = false
    val pagingConfig = PagingConfig(pageSize = 40, maxSize = 400)

    fun init() {
        viewModelScope.launch {
            initDatabase()

            settingsDatabase.initialize()

            // Pull saved settings
            settingsDatabase.getSettings()?.let { result ->
                _darkMode.value = result.darkMode
                _serverAddress.value = result.serverUrl
                _scrollBarsOnRight.value = result.scrollBarsOnRight
                _scrollBarsOnBottom.value = result.scrollBarsOnBottom
                _autoSyncDatabase.value = result.autoSync
            } ?: AppLogger.i(INIT_TAG, SimpleError.FAIL)
        }
    }

    /** ----------------------------------------------CUSTOMER------------------------------------------------------------------- */

    private val customerDatabase = CustomerDatabaseImpl(shouldLog = shouldLog)

    private val customerPager = Pager(pagingConfig) { CustomPagingSource(customerDatabase) }
    val customerListFlow = customerPager.flow.cachedIn(viewModelScope)

    private val _selectedCustomerData: MutableStateFlow<CustomerData?> = MutableStateFlow(null)
    val selectedCustomerData = _selectedCustomerData.asStateFlow()

    fun updateSelectedCustomer(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedCustomerData.value?.customer?.identifier != identifier) {
                customerDatabase.getById(identifier)?.let { customer ->
                    val notes = customerDatabase.notes(identifier).map { NoteData(it.key, it.value) }
                    val tickets = customerDatabase.getTickets(identifier).map { ticket ->
                        TicketData(
                            ticket = ticket,
                            notes = ticketDatabase.notes(ticket.identifier).map { NoteData(it.key, it.value) }
                        )
                    }

                    _selectedCustomerData.value = CustomerData(customer, notes, tickets)
                }
            }
        }
    }

    /** ----------------------------------------------TICKET------------------------------------------------------------------- */

    private val ticketDatabase = TicketDatabaseImpl(shouldLog = shouldLog)

    private val ticketPager = Pager(pagingConfig) { CustomPagingSource(ticketDatabase) }
    val ticketListFlow = ticketPager.flow.cachedIn(viewModelScope)

    private val _selectedTicketData: MutableStateFlow<TicketData?> = MutableStateFlow(null)
    val selectedTicketData = _selectedTicketData.asStateFlow()

    fun updateSelectedTicket(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedTicketData.value?.ticket?.identifier != identifier)
            ticketDatabase.getById(identifier)?.let { ticket ->
                val notes = ticketDatabase.notes(identifier).map { NoteData(it.key, it.value) }

                _selectedTicketData.value = TicketData(ticket, notes)
            }
        }
    }

    /** ----------------------------------------------INVENTORY------------------------------------------------------------------- */

    private val inventoryDatabase = InventoryDatabaseImpl(shouldLog = shouldLog)

    private val inventoryPager = Pager(pagingConfig) { CustomPagingSource(inventoryDatabase) }
    val inventoryListFlow = inventoryPager.flow.cachedIn(viewModelScope)

    private val _selectedInventoryData: MutableStateFlow<InventoryData?> = MutableStateFlow(null)
    val selectedInventoryData = _selectedInventoryData.asStateFlow()

    fun updateSelectedInventory(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedInventoryData.value?.inventory?.identifier != identifier) {
                inventoryDatabase.getById(identifier)?.let { inventory ->
                    val notes = inventoryDatabase.notes(identifier).map { NoteData(it.key, it.value) }

                    _selectedInventoryData.value = InventoryData(inventory, notes)
                }
            }
        }
    }

    /** ----------------------------------------------USER------------------------------------------------------------------- */

    private val userDatabase = UserDatabaseImpl(shouldLog = shouldLog)

    private val userPager = Pager(pagingConfig) { CustomPagingSource(userDatabase) }
    val userListFlow = userPager.flow.cachedIn(viewModelScope)

    private val _selectedUserData: MutableStateFlow<UserData> = MutableStateFlow(UserData())
    val selectedUserData = _selectedUserData.asStateFlow()

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    /** ----------------------------------------------UI------------------------------------------------------------------- */

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }

    private val _showLogin: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    /** Update whether we should show the login popup or not*/
    fun updateShowLogin(value: Boolean) {
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
        val ticketIdentifiers = mutableListOf<Uuid>()
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
                ticketDatabase.addNote(ticket.identifier, note, emptyList())
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
                customerDatabase.addNote(customer.identifier, note, emptyList())
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
                inventoryDatabase.addNote(inventory.identifier, note, emptyList())
            }
        }

        (0..userNum).forEach { _ ->
            // Generate random user
            val (user, notes) = generateMockUser()

            // Add user to database
            userDatabase.add(user)

            //Add notes
            notes.forEach { note ->
                userDatabase.addNote(user.identifier, note, emptyList())
            }

            // Add random tickets
            (0..Random.nextInt(4, 12)).forEach { _ ->
                userDatabase.addTicket(user.identifier, ticketIdentifiers.random())
            }
        }
    }
}