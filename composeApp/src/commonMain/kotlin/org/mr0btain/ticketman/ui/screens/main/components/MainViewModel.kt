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
import org.mr0btain.ticketman.components.utils.flushImagesToDisk
import org.mr0btain.ticketman.components.utils.getImages
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

    /** ----------------------------------------------OPERATIONS------------------------------------------------------------------- */



    /** ----------------------------------------------CUSTOMER------------------------------------------------------------------- */

    private val customerDatabase = CustomerDatabaseImpl(shouldLog = shouldLog)

    private val customerPager = Pager(pagingConfig) { CustomPagingSource(customerDatabase) }
    val customerListFlow = customerPager.flow.cachedIn(viewModelScope)

    private val _selectedCustomerData: MutableStateFlow<CustomerData?> = MutableStateFlow(null)
    val selectedCustomerData = _selectedCustomerData.asStateFlow()

    fun changeSelectedCustomer(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedCustomerData.value?.customer?.identifier != identifier) {
                customerDatabase.getById(identifier)?.let { customer ->
                    val notes = customerDatabase.getNotes(identifier).map { NoteData(it.key, getImages(it.value)) }
                    val tickets = customerDatabase.getTickets(identifier).map { ticket ->
                        TicketData(
                            ticket = ticket,
                            notes = ticketDatabase.getNotes(ticket.identifier).map { NoteData(it.key, getImages(it.value)) }
                        )
                    }

                    _selectedCustomerData.value = CustomerData(customer, notes, tickets)
                }
            }
        }
    }

    fun resetSelectedCustomer() { _selectedCustomerData.value = null }

    /** Updates a Customer and its data in the database */
    fun addOrUpdateCustomer(customerData: CustomerData) {
        viewModelScope.launch {
            val existingCustomer = customerDatabase.getById(customerData.customer.identifier)?.also { customer ->
                // Update if its changed
                if (customerData.customer != customer) customerDatabase.update(customerData.customer)

                // Existing notes for comparison
                val existingNotes = customerDatabase.getNotes(customerData.customer.identifier).map {
                    NoteData(it.key, getImages(it.value))
                }

                // Check if the note exists already and update if it needs to or add new
                customerData.notes.forEach { noteData ->
                    existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }?.let { existingNote ->
                        if (noteData != existingNote) {
                            customerDatabase.updateNote(noteData.note, flushImagesToDisk(noteData.images))
                        }
                    } ?: customerDatabase.addNote(customerData.customer.identifier, noteData.note, flushImagesToDisk(noteData.images))
                }

                // Existing tickets for comparison
                val existingTickets = customerDatabase.getTickets(customerData.customer.identifier)

                // Add tickets if they're new or update if they've changed
                customerData.tickets.forEach { ticketData ->
                    // See if it exists
                    val ticket = existingTickets.firstOrNull { it.identifier == ticketData.ticket.identifier }?.also { existingTicket ->
                        // check if ticket has changed, if so update it
                        if (ticketData.ticket != existingTicket) ticketDatabase.update(ticketData.ticket)

                        // Existing notes for comparison
                        val existingTicketNotes = ticketDatabase.getNotes(existingTicket.identifier).map { NoteData(it.key, getImages(it.value)) }

                        // Check ticket notes if they exist and have changed, if so update else add
                        ticketData.notes.forEach { noteData ->
                            existingTicketNotes.firstOrNull { it.note.identifier == noteData.note.identifier }?.let { existingNote ->
                                if (noteData != existingNote) {
                                    ticketDatabase.updateNote(noteData.note, flushImagesToDisk(noteData.images))
                                }
                            } ?: ticketDatabase.addNote(existingTicket.identifier, noteData.note, flushImagesToDisk(noteData.images))
                        }
                    }

                    // Check if it was null, then we need to add a whole new ticket
                    if (ticket == null) {
                        // Add to ticket database
                        ticketDatabase.add(ticketData.ticket)

                        // Add notes to ticket
                        ticketData.notes.forEach { (note, images) ->
                            ticketDatabase.addNote(ticketData.ticket.identifier, note, flushImagesToDisk(images))
                        }

                        // Link to customer
                        customerDatabase.addTicket(customerData.customer.identifier, ticketData.ticket.identifier)
                    }
                }
            }

            // If it doesn't exist, then add new
            if (existingCustomer == null) {
                // Add item
                customerDatabase.add(customerData.customer)

                // Add notes
                customerData.notes.forEach { (note, images) ->
                    customerDatabase.addNote(customerData.customer.identifier, note, flushImagesToDisk(images))
                }

                // Add tickets
                customerData.tickets.forEach { (ticket, notes) ->
                    // Add ticket
                    ticketDatabase.add(ticket)

                    // Add notes
                    notes.forEach { (note, images) ->
                        ticketDatabase.addNote(ticket.identifier, note, flushImagesToDisk(images))
                    }

                    // link to customer
                    customerDatabase.addTicket(customerData.customer.identifier, ticket.identifier)
                }
            }

            // update selected
            _selectedCustomerData.value = customerData
        }
    }

    /** ----------------------------------------------TICKET------------------------------------------------------------------- */

    private val ticketDatabase = TicketDatabaseImpl(shouldLog = shouldLog)

    private val ticketPager = Pager(pagingConfig) { CustomPagingSource(ticketDatabase) }
    val ticketListFlow = ticketPager.flow.cachedIn(viewModelScope)

    private val _selectedTicketData: MutableStateFlow<TicketData?> = MutableStateFlow(null)
    val selectedTicketData = _selectedTicketData.asStateFlow()

    fun changeSelectedTicket(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedTicketData.value?.ticket?.identifier != identifier)
            ticketDatabase.getById(identifier)?.let { ticket ->
                val notes = ticketDatabase.getNotes(identifier).map { NoteData(it.key, getImages(it.value)) }

                _selectedTicketData.value = TicketData(ticket, notes)
            }
        }
    }

    fun resetSelectedTicket() { _selectedTicketData.value = null }

    fun addOrUpdateTicket(ticketData: TicketData) {
        viewModelScope.launch {
            val existingTicket = ticketDatabase.getById(ticketData.ticket.identifier)?.also { ticket ->
                // Update if its changed
                if (ticketData.ticket != ticket) ticketDatabase.update(ticketData.ticket)

                // Existing notes for comparison
                val existingNotes = ticketDatabase.getNotes(ticketData.ticket.identifier).map { NoteData(it.key, getImages(it.value)) }

                // Check if the note exists already and update if it needs to or add new
                ticketData.notes.forEach { noteData ->
                    existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }?.let { existingNote ->
                        if (noteData != existingNote) {
                            ticketDatabase.updateNote(noteData.note, flushImagesToDisk(noteData.images))
                        }
                    } ?: ticketDatabase.addNote(ticketData.ticket.identifier, noteData.note, flushImagesToDisk(noteData.images))
                }
            }

            // If it doesn't exist, then add new
            if (existingTicket == null) {
                // Add item
                ticketDatabase.add(ticketData.ticket)

                // Add notes
                ticketData.notes.forEach { (note, images) ->
                    ticketDatabase.addNote(ticketData.ticket.identifier, note, flushImagesToDisk(images))
                }
            }

            // update selected
            _selectedTicketData.value = ticketData
        }
    }

    /** ----------------------------------------------INVENTORY------------------------------------------------------------------- */

    private val inventoryDatabase = InventoryDatabaseImpl(shouldLog = shouldLog)

    private val inventoryPager = Pager(pagingConfig) { CustomPagingSource(inventoryDatabase) }
    val inventoryListFlow = inventoryPager.flow.cachedIn(viewModelScope)

    private val _selectedInventoryData: MutableStateFlow<InventoryData?> = MutableStateFlow(null)
    val selectedInventoryData = _selectedInventoryData.asStateFlow()

    fun changeSelectedInventory(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedInventoryData.value?.inventory?.identifier != identifier) {
                inventoryDatabase.getById(identifier)?.let { inventory ->
                    val notes = inventoryDatabase.getNotes(identifier).map { NoteData(it.key, getImages(it.value)) }

                    _selectedInventoryData.value = InventoryData(inventory, notes)
                }
            }
        }
    }

    fun resetSelectedInventory() { _selectedInventoryData.value = null }

    fun addOrUpdateInventory(inventoryData: InventoryData) {
        viewModelScope.launch {
            val existingInventory = inventoryDatabase.getById(inventoryData.inventory.identifier)?.also { inventory ->
                // Update if its changed
                if (inventoryData.inventory != inventory) inventoryDatabase.update(inventoryData.inventory)

                // Existing notes for comparison
                val existingNotes = inventoryDatabase.getNotes(inventoryData.inventory.identifier).map { NoteData(it.key, getImages(it.value)) }

                // Check if the note exists already and update if it needs to or add new
                inventoryData.notes.forEach { noteData ->
                    existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }?.let { existingNote ->
                        if (noteData != existingNote) {
                            inventoryDatabase.updateNote(noteData.note, flushImagesToDisk(noteData.images))
                        }
                    } ?: inventoryDatabase.addNote(inventoryData.inventory.identifier, noteData.note, flushImagesToDisk(noteData.images))
                }
            }

            // If it doesn't exist, then add new
            if (existingInventory == null) {
                // Add item
                inventoryDatabase.add(inventoryData.inventory)

                // Add notes
                inventoryData.notes.forEach { (note, images) ->
                    inventoryDatabase.addNote(inventoryData.inventory.identifier, note, flushImagesToDisk(images))
                }
            }

            _selectedInventoryData.value = inventoryData
        }
    }

    /** ----------------------------------------------USER------------------------------------------------------------------- */

    private val userDatabase = UserDatabaseImpl(shouldLog = shouldLog)

    private val userPager = Pager(pagingConfig) { CustomPagingSource(userDatabase) }
    val userListFlow = userPager.flow.cachedIn(viewModelScope)

    private val _selectedUserData: MutableStateFlow<UserData?> = MutableStateFlow(null)
    val selectedUserData = _selectedUserData.asStateFlow()

    fun changeSelectedUser(identifier: Uuid) {
        viewModelScope.launch {
            if (selectedUserData.value?.user?.identifier != identifier) {
                userDatabase.getById(identifier)?.let { user ->
                    val notes = userDatabase.getNotes(identifier).map { NoteData(it.key, getImages(it.value)) }

                    _selectedUserData.value = UserData(user, notes)
                }
            }
        }
    }

    fun resetSelectedUser() { _selectedUserData.value = null }

    fun addOrUpdateUser(userData: UserData) {
        viewModelScope.launch {
            val existingUser = userDatabase.getById(userData.user.identifier)?.also { user ->
                // Update if its changed
                if (userData.user != user) userDatabase.update(userData.user)

                // Existing notes for comparison
                val existingNotes = userDatabase.getNotes(userData.user.identifier).map { NoteData(it.key, getImages(it.value)) }

                // Check if the note exists already and update if it needs to or add new
                userData.notes.forEach { noteData ->
                    existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }?.let { existingNote ->
                        if (noteData != existingNote) {
                            userDatabase.updateNote(noteData.note, flushImagesToDisk(noteData.images))
                        }
                    } ?: userDatabase.addNote(userData.user.identifier, noteData.note, flushImagesToDisk(noteData.images))
                }
            }

            // If it doesn't exist, then add new
            if (existingUser == null) {
                // Add item
                userDatabase.add(userData.user)

                // Add notes
                userData.notes.forEach { (note, images) ->
                    userDatabase.addNote(userData.user.identifier, note, flushImagesToDisk(images))
                }
            }

            _selectedUserData.value = userData
        }
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