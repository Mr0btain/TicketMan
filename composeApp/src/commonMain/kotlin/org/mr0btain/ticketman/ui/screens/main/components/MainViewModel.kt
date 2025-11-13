package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.*
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.inventory.InventoryData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.components.data.user.UserData
import org.mr0btain.ticketman.components.database.SettingsDatabase
import org.mr0btain.ticketman.components.database.initDatabase
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.utils.writeImagesToDisk
import org.mr0btain.ticketman.components.utils.pullImagesFromDisk
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.database.TicketDatabaseImpl
import org.mr0btain.ticketman.database.UserDatabaseImpl
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.generateMockCustomer
import org.mr0btain.ticketman.generateMockUser
import org.mr0btain.ticketman.generateRandomInventoryItem
import org.mr0btain.ticketman.generateRandomTicket
import org.mr0btain.ticketman.logging.AppLogger
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

    // TODO: We could add an option to change this. I also think image cache should have a different value and
    // possibly have a better way of dealing with relevant and irrelevant photos. Maybe a cleanup function
    // Max size of cache
    val maxCacheSize = 20

    /** ----------------------------------------------IMAGE------------------------------------------------------------------- */

    val imageCache = LruCache<UUID, ImageData>(maxCacheSize, "ImageCache")

    /** ----------------------------------------------CUSTOMER------------------------------------------------------------------- */

    val customerCache = LruCache<UUID, CustomerData>(maxCacheSize, "CustomerCache")

    private val customerDatabase = CustomerDatabaseImpl()

    var customerPager: OffsetPager<Customer>? = null

    private val _selectedCustomerData: MutableStateFlow<CustomerData?> = MutableStateFlow(null)
    val selectedCustomerData = _selectedCustomerData.asStateFlow()



    /** Sets the selected customer and inserts the data into the cache */
    private suspend fun setSelectedCustomer(customerData: CustomerData) {
        // Set selected
        _selectedCustomerData.value = customerData

        // Add to cache
        customerCache.put(customerData.customer.identifier, customerData)

    }

    suspend fun findCustomer(identifier: UUID): Customer? =
        customerCache.get(identifier)?.customer
            ?: customerDatabase.getById(identifier)

    fun changeSelectedCustomer(identifier: UUID) {
        viewModelScope.launch {
            if (selectedCustomerData.value?.customer?.identifier != identifier) {
                // Check if it exists in cache
                val cached = customerCache.get(identifier)?.also { customerData ->
                    _selectedCustomerData.value = customerData
                }

                // If not pull from database and add to cache
                if (cached == null) {
                    customerDatabase.getById(identifier)?.let { customer ->
                        // Get notes
                        val notes = customerDatabase.getNotes(identifier)
                            ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                            ?: emptyList()

                        // Get tickets
                        val tickets = customerDatabase.getTickets(identifier)?.map { ticket ->
                            TicketData(
                                ticket = ticket,
                                notes = ticketDatabase.getNotes(ticket.identifier)
                                    ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                                    ?: emptyList()
                            )
                        } ?: emptyList()

                        // Update selected
                        setSelectedCustomer(CustomerData(customer, notes, tickets))
                    }
                }
            }
        }
    }

    fun setupNewCustomer(name: PersonName = PersonName()) {
        _selectedCustomerData.value = CustomerData(
            customer = Customer(
                name = name
            )
        )
    }

    fun resetSelectedCustomer() {
        _selectedCustomerData.value = null
    }

    /** Updates a Customer and its data in the database */
    fun addOrUpdateCustomer(customerData: CustomerData) {
        viewModelScope.launch {
            val existingCustomer = customerDatabase.getById(customerData.customer.identifier)?.also { customer ->
                // Update if its changed
                if (customerData.customer != customer) customerDatabase.update(customerData.customer)

                // Existing notes for comparison
                val existingNotes = customerDatabase.getNotes(customerData.customer.identifier)?.map {
                    NoteData(it.key, pullImagesFromDisk(it.value))
                } ?: emptyList()

                // Check if the note exists already and update if it needs to or add new
                customerData.notes.forEach { noteData ->
                    existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }?.let { existingNote ->
                        if (noteData != existingNote) {
                            customerDatabase.updateNote(noteData.note, writeImagesToDisk(noteData.images))
                        }
                    } ?: customerDatabase.addNote(
                        customerData.customer.identifier,
                        noteData.note,
                        writeImagesToDisk(noteData.images)
                    )
                }

                // Existing tickets for comparison
                val existingTickets = customerDatabase.getTickets(customerData.customer.identifier)

                // Add tickets if they're new or update if they've changed
                customerData.tickets.forEach { ticketData ->
                    // See if it exists
                    val ticket = existingTickets?.firstOrNull { it.identifier == ticketData.ticket.identifier }
                        ?.also { existingTicket ->
                            // check if ticket has changed, if so update it
                            // if (ticketData.ticket != existingTicket) ticketDatabase.update(ticketData.ticket)

                            // TODO
                            // Existing notes for comparison
                            val existingTicketNotes = ticketDatabase.getNotes(existingTicket.identifier)
                                ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }

                            // Check ticket notes if they exist and have changed, if so update else add
                            ticketData.notes.forEach { noteData ->
                                existingTicketNotes?.firstOrNull { it.note.identifier == noteData.note.identifier }
                                    ?.let { existingNote ->
                                        if (noteData != existingNote) {
                                            ticketDatabase.updateNote(noteData.note, writeImagesToDisk(noteData.images))
                                        }
                                    } ?: ticketDatabase.addNote(
                                    existingTicket.identifier,
                                    noteData.note,
                                    writeImagesToDisk(noteData.images)
                                )
                            }
                        }

                    // Check if it was null, then we need to add a whole new ticket
                    if (ticket == null) {
                        // Add to ticket database
                        ticketDatabase.add(ticketData.ticket)

                        // Add notes to ticket
                        ticketData.notes.forEach { (note, images) ->
                            ticketDatabase.addNote(ticketData.ticket.identifier, note, writeImagesToDisk(images))
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
                    customerDatabase.addNote(customerData.customer.identifier, note, writeImagesToDisk(images))
                }

                // Add tickets
                customerData.tickets.forEach { (ticket, notes) ->
                    // Add ticket
                    ticketDatabase.add(ticket)

                    // Add notes
                    notes.forEach { (note, images) ->
                        ticketDatabase.addNote(ticket.identifier, note, writeImagesToDisk(images))
                    }

                    // link to customer
                    customerDatabase.addTicket(customerData.customer.identifier, ticket.identifier)
                }
            }

            // update selected
            setSelectedCustomer(customerData)
        }
    }

    fun removeSelectedCustomer() {
        viewModelScope.launch {
            selectedCustomerData.value?.customer?.identifier?.let { identifier ->
                customerDatabase.remove(identifier)

                resetSelectedCustomer()

                customerCache.remove(identifier)
            }
        }
    }

    suspend fun searchCustomers(query: String) = coroutineScope {
        customerDatabase.search(query, 20) ?: emptyList()
    }


    /**
     *  Removes an item from a customer. NOTE: This DOES NOT delete the item if it is a main item.
     *  it simply removes the link from the customer. Item is removed from SELECTED Customer. So
     *  make sure you set it as selected first.
     *  @param subItemType The type of the item to remove
     *  @param subItem The actual item.
     */
    fun removeCustomerSubItem(subItemType: DataType, subItem: Any?) {
        viewModelScope.launch {
            _selectedCustomerData.value?.let { customerData ->
                // Check what were removing from the customer
                when (subItemType) {
                    DataType.TICKET -> {
                        (subItem as? Ticket)?.let { ticket ->
                            setSelectedCustomer(
                                customerData.removeTicket(ticket.identifier)
                            )

                            customerDatabase.removeTicket(customerData.customer.identifier, ticket.identifier)

                            AppLogger.m(REMOVE_CUSTOMER_SUB_ITEM_TAG, SimpleResult.SUCCESS)
                        } ?: AppLogger.i(
                            REMOVE_CUSTOMER_SUB_ITEM_TAG,
                            "SubItem type set as ticket, but item was NOT a Ticket!"
                        )
                    }

                    DataType.EMAIL -> {
                        (subItem as? String)?.let { email ->
                            setSelectedCustomer(
                                customerData.removeEmail(email)
                            )

                            AppLogger.m(REMOVE_CUSTOMER_SUB_ITEM_TAG, SimpleResult.SUCCESS)
                        } ?: AppLogger.i(
                            REMOVE_CUSTOMER_SUB_ITEM_TAG,
                            "SubItem type set as email, but was NOT a string!"
                        )
                    }

                    DataType.PHONE -> {
                        (subItem as? String)?.let { phone ->
                            setSelectedCustomer(
                                customerData.removePhoneNumber(phone)
                            )

                            AppLogger.m(REMOVE_CUSTOMER_SUB_ITEM_TAG, SimpleResult.SUCCESS)
                        } ?: AppLogger.i(
                            REMOVE_CUSTOMER_SUB_ITEM_TAG,
                            "SubItem type set as phone, but was NOT a string!"
                        )
                    }

                    DataType.NOTE -> {
                        (subItem as? Note)?.let { note ->
                            setSelectedCustomer(
                                customerData.removeNote(note.identifier)
                            )

                            AppLogger.m(REMOVE_CUSTOMER_SUB_ITEM_TAG, SimpleResult.SUCCESS)
                        } ?: AppLogger.i(
                            REMOVE_CUSTOMER_SUB_ITEM_TAG,
                            "SubItem type set as Note, but item was NOT a Note!"
                        )
                    }

                    DataType.ADDRESS -> {
                        (subItem as? Address)?.let {
                            setSelectedCustomer(
                                customerData.clearAddress()
                            )

                            AppLogger.m(REMOVE_CUSTOMER_SUB_ITEM_TAG, SimpleResult.SUCCESS)
                        } ?: AppLogger.i(
                            REMOVE_CUSTOMER_SUB_ITEM_TAG,
                            "SubItem type set as Address, but item was NOT Address!"
                        )
                    }

                    else -> {
                        AppLogger.i(REMOVE_CUSTOMER_SUB_ITEM_TAG, "Not a valid SubItem DataType or not set!")
                    }
                }
            } ?: AppLogger.i(REMOVE_CUSTOMER_SUB_ITEM_TAG, "No customer selected!")
        }
    }

    /** ----------------------------------------------TICKET------------------------------------------------------------------- */

    val ticketCache = LruCache<UUID, TicketData>(maxCacheSize, "TicketCache")

    private val ticketDatabase = TicketDatabaseImpl()

    var ticketPager: OffsetPager<Ticket>? = null

    private val _selectedTicketData: MutableStateFlow<TicketData?> = MutableStateFlow(null)
    val selectedTicketData = _selectedTicketData.asStateFlow()

    /** Sets selected ticket and adds it to the cache */
    private suspend fun setSelectedTicket(ticketData: TicketData) {
        // Set selected ticket
        _selectedTicketData.value = ticketData

        // Add to cache
        ticketCache.put(ticketData.ticket.identifier, ticketData)
    }

    fun changeSelectedTicket(identifier: UUID) {
        viewModelScope.launch {
            if (selectedTicketData.value?.ticket?.identifier != identifier) {
                val cachedTicket = ticketCache.get(identifier)?.also { ticketData ->
                    _selectedTicketData.value = ticketData
                }

                if (cachedTicket == null) {
                    ticketDatabase.getById(identifier)?.let { ticket ->
                        val notes = ticketDatabase.getNotes(identifier)
                            ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                            ?: emptyList()

                        setSelectedTicket(TicketData(ticket, notes))
                    }
                }
            }
        }
    }

    fun setupNewTicket() {
        _selectedTicketData.value = TicketData()
    }

    fun resetSelectedTicket() {
        _selectedTicketData.value = null
    }

    fun addOrUpdateTicket(ticketData: TicketData) {
        viewModelScope.launch {
            val existingTicket = ticketDatabase.getById(ticketData.ticket.identifier)
                ?.also { ticket ->
                    // Update if its changed
                    if (ticketData.ticket != ticket) ticketDatabase.update(ticketData.ticket)

                    // Existing notes for comparison
                    val existingNotes = ticketDatabase.getNotes(ticketData.ticket.identifier)
                        ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                        ?: emptyList()

                    // Check if the note exists already and update if it needs to or add new
                    ticketData.notes.forEach { noteData ->
                        existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }
                            ?.let { existingNote ->
                                if (noteData != existingNote) {
                                    ticketDatabase.updateNote(noteData.note, writeImagesToDisk(noteData.images))
                                }
                            } ?: ticketDatabase.addNote(
                            ticketData.ticket.identifier,
                            noteData.note,
                            writeImagesToDisk(noteData.images)
                        )
                    }
                }

            // If it doesn't exist, then add new
            if (existingTicket == null) {
                // Add item
                ticketDatabase.add(ticketData.ticket)

                // Add notes
                ticketData.notes.forEach { (note, images) ->
                    ticketDatabase.addNote(ticketData.ticket.identifier, note, writeImagesToDisk(images))
                }
            }

            // update selected
            setSelectedTicket(ticketData)
        }
    }

    suspend fun searchTickets(query: String) = coroutineScope {
        ticketDatabase.search(query, 20) ?: emptyList()
    }

    // TODO:
    fun removeSelectedTicket() {
        viewModelScope.launch {

        }
    }

    /** ----------------------------------------------INVENTORY------------------------------------------------------------------- */

    val inventoryCache = LruCache<UUID, InventoryData>(maxCacheSize, "InventoryCache")

    private val inventoryDatabase = InventoryDatabaseImpl()

    var inventoryPager: OffsetPager<Inventory>? = null

    private val _selectedInventoryData: MutableStateFlow<InventoryData?> = MutableStateFlow(null)
    val selectedInventoryData = _selectedInventoryData.asStateFlow()

    /** Sets selected inventory and adds it to the cache */
    private suspend fun setSelectedInventory(inventoryData: InventoryData) {
        // Set selected inventory
        _selectedInventoryData.value = inventoryData

        // Add to cache
        inventoryCache.put(inventoryData.inventory.identifier, inventoryData)
    }

    fun changeSelectedInventory(identifier: UUID) {
        viewModelScope.launch {
            if (selectedInventoryData.value?.inventory?.identifier != identifier) {
                val cachedInventory = inventoryCache.get(identifier)?.also { inventoryData ->
                    _selectedInventoryData.value = inventoryData
                }

                if (cachedInventory == null) {
                    inventoryDatabase.getById(identifier)?.let { inventory ->
                        val notes = inventoryDatabase.getNotes(identifier)
                            ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                            ?: emptyList()

                        val images = pullImagesFromDisk(
                            storedImages = inventoryDatabase.getImages(identifier)
                                ?: emptyList()
                        )

                        setSelectedInventory(
                            InventoryData(
                                inventory = inventory,
                                notes = notes,
                                images = images
                            )
                        )
                    }
                }
            }
        }
    }

    fun setupNewInventory() {
        _selectedInventoryData.value = InventoryData()
    }

    fun resetSelectedInventory() {
        _selectedInventoryData.value = null
    }

    fun addOrUpdateInventory(inventoryData: InventoryData) {
        viewModelScope.launch {
            val existingInventory = inventoryDatabase.getById(inventoryData.inventory.identifier)
                ?.also { inventory ->
                    // Update if its changed
                    if (inventoryData.inventory != inventory) inventoryDatabase.update(inventoryData.inventory)

                    // Existing notes for comparison
                    val existingNotes = inventoryDatabase.getNotes(inventoryData.inventory.identifier)
                        ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                        ?: emptyList()

                    // Check if the note exists already and update if it needs to or add new
                    inventoryData.notes.forEach { noteData ->
                        existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }
                            ?.let { existingNote ->
                                if (noteData != existingNote) {
                                    inventoryDatabase.updateNote(noteData.note, writeImagesToDisk(noteData.images))
                                }
                            } ?: inventoryDatabase.addNote(
                            inventoryData.inventory.identifier,
                            noteData.note,
                            writeImagesToDisk(noteData.images)
                        )
                    }
                }

            // If it doesn't exist, then add new
            if (existingInventory == null) {
                // Add item
                inventoryDatabase.add(inventoryData.inventory)

                // Add notes
                inventoryData.notes.forEach { (note, images) ->
                    inventoryDatabase.addNote(inventoryData.inventory.identifier, note, writeImagesToDisk(images))
                }
            }

            // Set selected
            setSelectedInventory(inventoryData)
        }
    }

    suspend fun searchInventory(query: String) = coroutineScope {
        inventoryDatabase.search(query, 20) ?: emptyList()
    }

    /** ----------------------------------------------USER------------------------------------------------------------------- */

    val userCache = LruCache<UUID, UserData>(maxCacheSize, "UserCache")

    private val userDatabase = UserDatabaseImpl()

    var userPager: OffsetPager<User>? = null

    private val _selectedUserData: MutableStateFlow<UserData?> = MutableStateFlow(null)
    val selectedUserData = _selectedUserData.asStateFlow()

    /** Sets selected user and adds it to the cache */
    private suspend fun setSelectedUser(userData: UserData) {
        // Set selected
        _selectedUserData.value = userData

        // Add to cache
        userCache.put(userData.user.identifier, userData)
    }

    fun changeSelectedUser(identifier: UUID) {
        viewModelScope.launch {
            if (selectedUserData.value?.user?.identifier != identifier) {
                val cachedUser = userCache.get(identifier)?.also { userData ->
                    _selectedUserData.value = userData
                }

                if (cachedUser == null) {
                    userDatabase.getById(identifier)?.let { user ->
                        val notes = userDatabase.getNotes(identifier)
                            ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                            ?: emptyList()

                        _selectedUserData.value = UserData(user, notes)
                    }
                }
            }
        }
    }

    fun setupNewUser() {
        _selectedUserData.value = UserData()
    }

    fun resetSelectedUser() {
        _selectedUserData.value = null
    }

    fun addOrUpdateUser(userData: UserData) {
        viewModelScope.launch {
            val existingUser = userDatabase.getById(userData.user.identifier)
                ?.also { user ->
                    // Update if its changed
                    if (userData.user != user) userDatabase.update(userData.user)

                    // Existing notes for comparison
                    val existingNotes = userDatabase.getNotes(userData.user.identifier)
                        ?.map { NoteData(it.key, pullImagesFromDisk(it.value)) }
                        ?: emptyList()

                    // Check if the note exists already and update if it needs to or add new
                    userData.notes.forEach { noteData ->
                        existingNotes.firstOrNull { it.note.identifier == noteData.note.identifier }
                            ?.let { existingNote ->
                                if (noteData != existingNote) {
                                    userDatabase.updateNote(noteData.note, writeImagesToDisk(noteData.images))
                                }
                            } ?: userDatabase.addNote(
                            userData.user.identifier,
                            noteData.note,
                            writeImagesToDisk(noteData.images)
                        )
                    }
                }

            // If it doesn't exist, then add new
            if (existingUser == null) {
                // Add item
                userDatabase.add(userData.user)

                // Add notes
                userData.notes.forEach { (note, images) ->
                    userDatabase.addNote(userData.user.identifier, note, writeImagesToDisk(images))
                }
            }

            // Set selected
            setSelectedUser(userData)
        }
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