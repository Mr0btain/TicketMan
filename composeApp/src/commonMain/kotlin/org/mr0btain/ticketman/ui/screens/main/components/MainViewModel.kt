package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.inventory.InventoryData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.components.data.user.UserData
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.components.database.SettingsDatabase
import org.mr0btain.ticketman.components.database.initDatabase
import org.mr0btain.ticketman.components.database.local.CustomerLocalDatabase
import org.mr0btain.ticketman.components.database.local.InventoryLocalDatabase
import org.mr0btain.ticketman.components.database.local.TicketLocalDatabase
import org.mr0btain.ticketman.components.database.local.UserLocalDatabase
import org.mr0btain.ticketman.generateMockCustomer
import org.mr0btain.ticketman.generateMockUser
import org.mr0btain.ticketman.generateRandomInventoryItem
import org.mr0btain.ticketman.generateRandomTicket
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
    companion object {
        private const val TAG = "MainViewModel"
        private const val INIT_TAG = "$TAG:Init"
        private const val INIT_SYNC_TAG = "$TAG:Init_Sync"
    }

    val shouldLog = false

    private val customerLocalDatabase = CustomerLocalDatabase(shouldLog)
    private val ticketLocalDatabase = TicketLocalDatabase(shouldLog)
    private val inventoryLocalDatabase = InventoryLocalDatabase(shouldLog)
    private val userLocalDatabase = UserLocalDatabase(shouldLog)
    private val settingsDatabase = SettingsDatabase(shouldLog)

    // TODO: Add a way to check if we have network connection, and once we do have it run a check and sync
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

            // Populate data lists from local
            //dataHandler.populate()

            /*
            if (dataHandler.loginCheck()) {
                if (dataHandler.syncData())
                    AppLogger.m(INIT_SYNC_TAG, SimpleError.SUCCESS)
                else
                    AppLogger.i(INIT_SYNC_TAG, SimpleError.FAIL)
            } else AppLogger.i(INIT_SYNC_TAG, "No Connection or Not Logged in")
             */
        }
    }

    /** Main data handler where most of the functions and data is held*/
    //val dataHandler = DataHandler(appDir, viewModelScope)

    private val _selectedCustomerData: MutableStateFlow<CustomerData> = MutableStateFlow(CustomerData())
    val selectedCustomerData = _selectedCustomerData.asStateFlow()

    fun updateSelectedCustomer(identifier: Uuid) {
        viewModelScope.launch {
            customerLocalDatabase.getById(identifier)?.let { customer ->
                val notes = customerLocalDatabase.getNotes(identifier)
                val tickets = customerLocalDatabase.getTickets(identifier)

                _selectedCustomerData.value = CustomerData(customer, notes,tickets)
            }
        }
    }

    fun getCustomers() = customerLocalDatabase.getAll()

    private val _selectedTicketData: MutableStateFlow<TicketData> = MutableStateFlow(TicketData())
    val selectedTicketData = _selectedTicketData.asStateFlow()

    fun updateSelectedTicket(identifier: Uuid) {
        viewModelScope.launch {
            ticketLocalDatabase.getById(identifier)?.let { ticket ->
                val notes = ticketLocalDatabase.getNotes(identifier)

                _selectedTicketData.value = TicketData(ticket, notes)
            }
        }
    }

    fun getTickets() = ticketLocalDatabase.getAll()

    private val _selectedInventoryData: MutableStateFlow<InventoryData> = MutableStateFlow(InventoryData())
    val selectedInventoryData = _selectedInventoryData.asStateFlow()

    fun updateSelectedInventory(identifier: Uuid) {
        viewModelScope.launch {
            inventoryLocalDatabase.getById(identifier)?.let { inventory ->
                val notes = inventoryLocalDatabase.getNotes(identifier)

                _selectedInventoryData.value = InventoryData(inventory, notes)
            }
        }
    }

    fun getInventory() = inventoryLocalDatabase.getAll()

    private val _selectedUserData: MutableStateFlow<UserData> = MutableStateFlow(UserData())
    val selectedUserData = _selectedUserData.asStateFlow()

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
    fun updateShowLogin(value: Boolean) {
        _showLogin.value = value
    }

    /** Settings section */
    // ----------------------------------------------------------------------------------------------------------------------

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

    fun fillDatabaseWithTestData() {
        val ticketIdentifiers = mutableListOf<Uuid>()
        val ticketNum = 1000
        val customerNum = 200
        val inventoryNum = 100
        val userNum = 25

        (0..ticketNum).forEach { i ->
            // Generate random ticket
            val (ticket, notes) = generateRandomTicket()

            // Add ticket to database
            ticketLocalDatabase.add(ticket)

            // Add notes
            ticketLocalDatabase.addNotes(ticket.identifier, notes.map { NoteData(it) })

            // Insert id into list
            ticketIdentifiers.add(ticket.identifier)
        }

        (0..customerNum).forEach { _ ->
            // Generate random customer
            val (customer, notes) = generateMockCustomer()

            // Add customer
            customerLocalDatabase.add(customer)

            // Add notes
            customerLocalDatabase.addNotes(customer.identifier, notes.map { NoteData(it) })

            // Add random tickets
            (0..Random.nextInt(1, 6)).forEach { _ ->
                customerLocalDatabase.addTicket(customer.identifier, ticketIdentifiers.random())
            }
        }

        (0..inventoryNum).forEach { _ ->
            val (inventory, notes) = generateRandomInventoryItem()

            println(inventory)

            inventoryLocalDatabase.add(inventory)

            inventoryLocalDatabase.addNotes(inventory.identifier, notes.map { NoteData(it) })
        }

        (0..userNum).forEach { _ ->
            // Generate random user
            val (user, notes) = generateMockUser()

            // Add user to database
            userLocalDatabase.add(user)

            //Add notes
            userLocalDatabase.addNotes(user.identifier, notes.map { NoteData(it) })

            // Add random tickets
            (0..Random.nextInt(4, 12)).forEach { _ ->
                userLocalDatabase.addTicket(user.identifier, ticketIdentifiers.random())
            }
        }
    }
}