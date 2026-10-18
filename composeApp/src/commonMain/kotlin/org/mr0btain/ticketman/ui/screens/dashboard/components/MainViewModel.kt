package org.mr0btain.ticketman.ui.screens.dashboard.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerCache
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.inventory.InventoryCache
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.RecordSelection
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.ticket.TicketCache
import org.mr0btain.ticketman.components.data.ticket.TicketScope
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.data.user.UserCache
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.components.image.ImageClient
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.database.RemoteRepository
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.ui.screens.admin.components.AdminHandler
import org.mr0btain.ticketman.ui.screens.counter.components.CounterHandler
import org.mr0btain.ticketman.ui.screens.kiosk.components.KioskHandler
import org.mr0btain.ticketman.ui.screens.login.components.SessionHandler
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsHandler
import kotlin.uuid.Uuid

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app. Each part of the app has its own
 * handler here, this wires them together
 */

@Suppress("PrivatePropertyName")
class MainViewModel(
    val snackBarHost: SnackbarHostState,
    private val localStorage: LocalStorage,
    engine: HttpClientEngine = getHttpEngine()
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
                // A kiosk goes back to its PIN screen instead of asking for a password
                if (kiosk.active.value) kiosk.lock() else session.expired()
            }
        }

        viewModelScope.launch {
            settings.load()
            _ticketScope.value = TicketScope.byName(localStorage.ticketScope)
            _selectedMainTabIndex.value = localStorage.defaultTab

            // Set url in all our handlers and log in with any stored credentials
            session.connect(localStorage.serverUrl)

            // Pagers don't load themselves, so do the first load now that we're connected
            refreshPagers()
        }
    }

    /** ----------------------------------------------OPERATIONS------------------------------------------------------------------- */

    // Max size of cache. Caches are built with the view model, so changes apply on next start
    val maxCacheSize = localStorage.cacheSize

    // Every pager is built once with the saved page size and lives as long as the view model, so screens can
    // collect their items right away and keep getting updates after logins, refreshes and page size changes
    private val pagerConfig = OffsetPagerConfig(pageSize = localStorage.pageSize)

    fun refreshPagers() {
        viewModelScope.launch {
            customerPager.refresh()
            ticketPager.refresh()
            userPager.refresh()
            inventoryPager.refresh()
        }
    }

    /** Loaded pages were fetched with the old size, so reload them with the new one */
    private fun changePageSize(size: Int) {
        customerPager.setPageSize(size)
        ticketPager.setPageSize(size)
        inventoryPager.setPageSize(size)
        userPager.setPageSize(size)
        refreshPagers()
    }

    /** Drops every loaded record, for when whoever loaded them is gone */
    private fun clearRecords() {
        clearSelectedCustomer()
        clearSelectedTicket()
        clearSelectedInventory()
        clearSelectedUser()
        customerPager.clear()
        ticketPager.clear()
        inventoryPager.clear()
        userPager.clear()
    }

    /** ----------------------------------------------REMOTE------------------------------------------------------------------- */

    val remoteHandler = RemoteHandler(
        engine = engine,
        localStorage = localStorage
    )

    /** Uploads photos and finds where they're fetched from, handed to screens through LocalImageClient */
    val imageClient = ImageClient(remoteHandler.imageRepo, remoteHandler.httpClient)

    /** ----------------------------------------------SESSION------------------------------------------------------------------- */

    val session: SessionHandler = SessionHandler(
        remoteHandler = remoteHandler,
        localStorage = localStorage,
        scope = viewModelScope,
        onLoggedIn = { refreshPagers() },
        onLoggedOut = {
            admin.clear()
            counter.clear()
            clearRecords()
        }
    )

    val settings: SettingsHandler = SettingsHandler(
        localStorage = localStorage,
        scope = viewModelScope,
        onServerUrlChanged = { url -> session.changeServer(url) },
        onPageSizeChanged = { size -> changePageSize(size) }
    )

    /** ----------------------------------------------CUSTOMER------------------------------------------------------------------- */

    val customerCache: CustomerCache = CustomerCache(maxCacheSize)

    val customerPager = OffsetPager<Customer>(
        tag = CUSTOMER_PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            remoteHandler.customerRepo.fetch(
                offset = (page * pageSize).toLong(),
                limit = pageSize
            )
        }
    )

    val customerSeed: CustomerSeed = CustomerSeed()

    /** Saves the customer seed: the customer, its notes, and any tickets linked to it */
    fun handleCustomerChanges() {
        // If we haven't made any changes, then just return
        if (!customerSeed.isDirty) return

        val customer = customerSeed.customer
        val notes = customerSeed.notes.toList()
        val tickets = customerSeed.tickets.toList()
        val changed = customerSeed.getChangedFields().toSet()
        val isNew = customerSeed.isNew

        viewModelScope.launch {
            val repo = remoteHandler.customerRepo

            val saved = if (isNew) repo.add(customer) else {
                // Notes and tickets are saved on their own, only send the customer if something else changed
                if ((changed - setOf(ChangedField.NOTE, ChangedField.TICKET)).isNotEmpty()) repo.update(customer)
                else Result.Success(Unit)
            }

            if (saved is Result.Error) {
                snackBarHost.showSnackbar("Customer not saved: ${saved.error.message}")
                return@launch
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(customer.identifier, notes, repo)

            // Tickets can be linked here, unlinking happens by giving the ticket to another customer
            if (isNew || ChangedField.TICKET in changed) {
                val linked = repo.getTickets(customer.identifier).map { it.identifier }.toSet()
                val failed = tickets
                    .filter { it.identifier !in linked }
                    .count { ticket -> repo.addTicket(customer.identifier, ticket.identifier) is Result.Error }

                if (failed > 0) snackBarHost.showSnackbar("$failed ${if (failed == 1) "ticket wasn't" else "tickets weren't"} linked")
            }

            customerCache.remove(customer.identifier)
            setSelectedCustomer(customer.identifier)
            customerPager.refreshCurrentData()
        }
    }

    /**
     * Makes the server's notes match [notes]: adds new ones, updates edited ones and removes ones that were deleted.
     * Shows a snackbar if any of that failed
     */
    private suspend fun syncNotes(identifier: Uuid, notes: List<Note>, repo: RemoteRepository<*>) {
        val existing = repo.getNotes(identifier)
        val existingById = existing.associateBy { it.identifier }
        val keptIds = notes.map { it.identifier }.toSet()

        val saves = notes.mapNotNull { note ->
            val saved = existingById[note.identifier]

            when {
                saved == null -> repo.addNote(identifier, note)
                saved != note -> repo.updateNote(note)
                else -> null
            }
        }
        val removals = existing.filter { it.identifier !in keptIds }.map { note -> repo.removeNote(note.identifier) }

        val failed = (saves + removals).count { it is Result.Error }
        if (failed > 0) snackBarHost.showSnackbar("$failed ${if (failed == 1) "note change wasn't" else "note changes weren't"} saved")
    }

    /** Customer being loaded after a click, the list highlights it until it's in the seed */
    val customerSelection = RecordSelection(viewModelScope)

    /** Sets the selected customer and inserts the data into the cache */
    fun setSelectedCustomer(identifier: Uuid) {
        if (customerSelection.pending.value == identifier) return

        customerSelection.select(identifier) {
            // Get customer from cache if we can, else pull it and add it to cache
            val (customer, notes, tickets) = customerCache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.customerRepo.getNotes(identifier) }
                val tickets = async { remoteHandler.customerRepo.getTickets(identifier) }
                val customer = remoteHandler.customerRepo.get(identifier) ?: return@coroutineScope null

                CustomerCache.CustomersData(customer, notes.await(), tickets.await())
                    .also { customerCache.put(it.customer, it.notes, it.tickets) }
            } ?: return@select null

            return@select { customerSeed.setCustomer(customer, notes, tickets) }
        }
    }

    /** Empties the customer seed for a new one, dropping any customer still loading */
    fun clearSelectedCustomer() {
        customerSelection.cancel()
        customerSeed.clear()
    }

    suspend fun getCustomer(identifier: Uuid): Customer? =
        customerCache.get(identifier)?.customer
            ?: remoteHandler.customerRepo.get(identifier)

    suspend fun searchCustomers(query: String) = coroutineScope {
        remoteHandler.customerRepo.search(query, settings.searchLimit.value)
    }

    fun removeCustomer(identifier: Uuid) {
        viewModelScope.launch {
            if (customerSeed.identifier == identifier) {
                clearSelectedCustomer()
                remoteHandler.customerRepo.remove(identifier)

                // Refresh pager data
                customerPager.refreshCurrentData()
            }
        }
    }

    /** ----------------------------------------------TICKET------------------------------------------------------------------- */

    val ticketCache = TicketCache(maxCacheSize)

    val ticketPager = OffsetPager<Ticket>(
        tag = TICKET_PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            val offset = (page * pageSize).toLong()
            when (_ticketScope.value) {
                TicketScope.MINE -> remoteHandler.ticketRepo.fetchMine(offset = offset, limit = pageSize)
                TicketScope.ALL -> remoteHandler.ticketRepo.fetch(offset = offset, limit = pageSize)
            }
        }
    )

    private val _ticketScope = MutableStateFlow(TicketScope.DEFAULT)
    val ticketScope = _ticketScope.asStateFlow()

    /** Switches the ticket list between the user's tickets and everyone's, and reloads it */
    fun setTicketScope(scope: TicketScope) {
        if (_ticketScope.value == scope) return

        _ticketScope.value = scope
        localStorage.ticketScope = scope.name
        viewModelScope.launch { ticketPager.refresh() }
    }

    val ticketSeed: TicketSeed = TicketSeed()

    /** Sets selected ticket and adds it to the cache */
    /** Ticket being loaded after a click, the list highlights it until it's in the seed */
    val ticketSelection = RecordSelection(viewModelScope)

    fun setSelectedTicket(identifier: Uuid) {
        if (ticketSelection.pending.value == identifier) return

        ticketSelection.select(identifier) {
            // If cache didn't have ticket, pull it and add to cache
            val (ticket, notes, customer, user) = ticketCache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.ticketRepo.getNotes(identifier) }
                val customer = async { remoteHandler.ticketRepo.getCustomer(identifier) }
                val user = async { remoteHandler.ticketRepo.getUser(identifier) }
                val ticket = remoteHandler.ticketRepo.get(identifier) ?: return@coroutineScope null

                TicketCache.TicketData(ticket, notes.await(), customer.await(), user.await())
                    .also { ticketCache.put(it.ticket, it.notes, it.customer, it.user) }
            } ?: return@select null

            return@select { ticketSeed.setTicket(ticket, notes, customer, user) }
        }
    }

    /** Empties the ticket seed for a new one, dropping any ticket still loading */
    fun clearSelectedTicket() {
        ticketSelection.cancel()
        ticketSeed.clear()
    }

    /** Saves the ticket seed: the ticket, its notes, and who it belongs to and is assigned to */
    fun handleTicketChange() {
        // If no changes, then return
        if (!ticketSeed.isDirty) return

        val ticket = ticketSeed.ticket
        val notes = ticketSeed.notes.toList()
        val customer = ticketSeed.customer
        val user = ticketSeed.user
        val changed = ticketSeed.getChangedFields().toSet()
        val isNew = ticketSeed.isNew

        viewModelScope.launch {
            val repo = remoteHandler.ticketRepo

            if (isNew) {
                // Created through create so the seed picks up the number the server gave it
                when (val created = repo.create(ticket)) {
                    is Result.Error -> {
                        snackBarHost.showSnackbar("Ticket not saved: ${created.error.message}")
                        return@launch
                    }

                    is Result.Success -> ticketSeed.setTicket(created.data, notes, customer, user)
                }
            } else {
                val linkFields = setOf(ChangedField.NOTE, ChangedField.CUSTOMER, ChangedField.USER)

                if ((changed - linkFields).isNotEmpty()) {
                    val updated = repo.update(ticket)

                    if (updated is Result.Error) {
                        snackBarHost.showSnackbar("Ticket not saved: ${updated.error.message}")
                        return@launch
                    }
                }
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(ticket.identifier, notes, repo)
            if (customer != null && (isNew || ChangedField.CUSTOMER in changed)) {
                val linked = repo.setCustomer(ticket.identifier, customer.identifier)
                if (linked is Result.Error) snackBarHost.showSnackbar("Customer not linked: ${linked.error.message}")
            }

            if (user != null && (isNew || ChangedField.USER in changed)) {
                val assigned = repo.setUser(ticket.identifier, user.identifier)
                if (assigned is Result.Error) snackBarHost.showSnackbar("Technician not assigned: ${assigned.error.message}")
            }

            ticketCache.remove(ticket.identifier)
            setSelectedTicket(ticket.identifier)
            ticketPager.refreshCurrentData()
        }
    }

    suspend fun searchTickets(query: String) = coroutineScope {
        remoteHandler.ticketRepo.search(query, settings.searchLimit.value)
    }

    fun removeTicket(identifier: Uuid) {
        viewModelScope.launch {
            if (ticketSeed.identifier == identifier) {
                clearSelectedTicket()
                remoteHandler.ticketRepo.remove(identifier)
                ticketPager.refreshCurrentData()
            }
        }
    }

    /** ----------------------------------------------INVENTORY------------------------------------------------------------------- */

    val inventoryCache = InventoryCache(maxCacheSize)

    val inventoryPager = OffsetPager<Inventory>(
        tag = INVENTORY_PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            remoteHandler.inventoryRepo.fetch(
                offset = (page * pageSize).toLong(),
                limit = pageSize
            )
        }
    )

    val inventorySeed: InventorySeed = InventorySeed()

    /** Inventory being loaded after a click, the list highlights it until it's in the seed */
    val inventorySelection = RecordSelection(viewModelScope)

    fun setSelectedInventory(identifier: Uuid) {
        if (inventorySelection.pending.value == identifier) return

        inventorySelection.select(identifier) {
            val (inventory, notes, images) = inventoryCache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.inventoryRepo.getNotes(identifier) }
                val images = async { remoteHandler.inventoryRepo.getImages(identifier) }
                val inventory = remoteHandler.inventoryRepo.get(identifier) ?: return@coroutineScope null

                InventoryCache.InventoryData(inventory, notes.await(), images.await())
                    .also { inventoryCache.put(it.inventory, it.notes, it.images) }
            } ?: return@select null

            return@select { inventorySeed.setInitial(inventory, notes, images) }
        }
    }

    /** Empties the inventory seed for a new item, dropping any item still loading */
    fun clearSelectedInventory() {
        inventorySelection.cancel()
        inventorySeed.clear()
    }

    /** Saves the inventory seed: the item, its notes and its photos */
    fun handleInventoryChange() {
        if (!inventorySeed.isDirty) return

        val inventory = inventorySeed.inventory
        val notes = inventorySeed.notes.toList()
        val images = inventorySeed.images.toList()
        val changed = inventorySeed.getChangedFields().toSet()
        val isNew = inventorySeed.isNew

        viewModelScope.launch {
            val repo = remoteHandler.inventoryRepo
            val linkFields = setOf(ChangedField.NOTE, ChangedField.IMAGE)

            if (isNew || (changed - linkFields).isNotEmpty()) {
                val saved = if (isNew) repo.add(inventory) else repo.update(inventory)

                if (saved is Result.Error) {
                    snackBarHost.showSnackbar("Item not saved: ${saved.error.message}")
                    return@launch
                }
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(inventory.identifier, notes, repo)

            if (isNew || ChangedField.IMAGE in changed) {
                val photos = repo.setImages(inventory.identifier, images)
                if (photos is Result.Error) snackBarHost.showSnackbar("Photos not saved: ${photos.error.message}")
            }

            inventoryCache.remove(inventory.identifier)
            setSelectedInventory(inventory.identifier)
            inventoryPager.refreshCurrentData()
        }
    }

    suspend fun searchInventory(query: String) = coroutineScope {
        remoteHandler.inventoryRepo.search(query, settings.searchLimit.value)
    }

    fun removeInventory(identifier: Uuid) {
        viewModelScope.launch {
            if (inventorySeed.identifier == identifier) {
                clearSelectedInventory()
                remoteHandler.inventoryRepo.remove(identifier)
                inventoryPager.refreshCurrentData()
            }
        }
    }

    /** ----------------------------------------------USER------------------------------------------------------------------- */

    val userCache: UserCache = UserCache(maxCacheSize)

    val userPager = OffsetPager<User>(
        tag = USER_PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            remoteHandler.userRepo.fetch(
                offset = (page * pageSize).toLong(),
                limit = pageSize
            )
        }
    )

    val userSeed: UserSeed = UserSeed()

    /**
     * Sets selected user and adds it to the cache
     * @param force Reload even if it's already selected, used after the user was changed
     */
    /** User being loaded after a click, the list highlights it until it's in the seed */
    val userSelection = RecordSelection(viewModelScope)

    fun setSelectedUser(identifier: Uuid, force: Boolean = false) {
        if (!force && (userSeed.identifier == identifier || userSelection.pending.value == identifier)) return

        userSelection.select(identifier) {
            val (user, notes) = userCache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.userRepo.getNotes(identifier) }
                val user = remoteHandler.userRepo.get(identifier) ?: return@coroutineScope null

                UserCache.UserData(user, notes.await(), emptyList())
                    .also { userCache.put(it.user, it.notes) }
            } ?: return@select null

            return@select { userSeed.setUser(user, notes) }
        }
    }

    /** Empties the user seed, dropping any user still loading */
    fun clearSelectedUser() {
        userSelection.cancel()
        userSeed.clear()
    }

    suspend fun searchUsers(query: String) = coroutineScope {
        remoteHandler.userRepo.search(query, settings.searchLimit.value)
    }

    /** Drops stale copies of a changed user and reloads the list, [identifier] is null when the user is gone */
    private fun onUsersChanged(identifier: Uuid?) {
        viewModelScope.launch {
            if (identifier != null) {
                userCache.remove(identifier)
                if (userSeed.identifier == identifier) setSelectedUser(identifier, force = true)

                // Admins can edit themselves, keep what we show for the current user in step
                if (identifier == session.currentUser.value?.identifier) session.reloadCurrentUser()
            } else {
                clearSelectedUser()
            }

            userPager.refreshCurrentData()
        }
    }

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
        onUsersChanged = { identifier -> onUsersChanged(identifier) }
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

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }
}
