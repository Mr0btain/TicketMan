package org.mr0btain.ticketman.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.pagination.offset.offsetPagerHandler
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.screens.shared.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmTabs
import org.mr0btain.ticketman.ui.kit.data.TmImageView
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.dashboard.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.dashboard.components.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.dashboard.components.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.dashboard.components.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.dashboard.components.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.dashboard.components.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.dashboard.components.ticket.TicketPanel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.refresh_cw
import kotlin.uuid.Uuid

@Composable
fun DashboardScreen(
    viewModel: MainViewModel
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()
    val loginState = viewModel.session.loginState.collectAsState()

    // For customer panel
    val customerSeed = viewModel.customers.seed
    val customerPagedItems = viewModel.customers.pager.items.collectAsState()
    val customerLazyListState = rememberLazyListState()
    val pendingCustomer by viewModel.customers.selection.pending.collectAsState()

    // Handler for the customer pager
    offsetPagerHandler(viewModel.customers.pager, customerLazyListState)

    // For customer dialog
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // For ticket panel
    val ticketSeed = viewModel.tickets.seed
    val ticketPagedItems = viewModel.tickets.pager.items.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    val pendingTicket by viewModel.tickets.selection.pending.collectAsState()
    val ticketScope by viewModel.tickets.listScope.collectAsState()
    val scope = rememberCoroutineScope()

    // Handler for ticket pager
    offsetPagerHandler(viewModel.tickets.pager, ticketLazyListState)

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }

    // For Inventory panel
    val inventorySeed = viewModel.inventory.seed
    val inventoryPagedItems = viewModel.inventory.pager.items.collectAsState()
    val inventoryLazyListState = rememberLazyListState()
    val pendingInventory by viewModel.inventory.selection.pending.collectAsState()

    // Handler for inventory pager
    offsetPagerHandler(viewModel.inventory.pager, inventoryLazyListState)

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }

    // For confirm delete dialog
    var dataType by remember { mutableStateOf(DataType.NONE) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    val imagePopupList = remember { mutableStateListOf<StoredImage>() }

    fun showImages(images: List<StoredImage>) {
        imagePopupList.clear()
        imagePopupList.addAll(images)
        showImagePopup = true
    }

    // Opens the photos of the note with this identifier
    fun showNoteImages(notes: List<Note>, noteIdentifier: Uuid) {
        notes.firstOrNull { it.identifier == noteIdentifier }?.let { note ->
            imagePopupList.clear()
            imagePopupList.addAll(note.images)
            showImagePopup = true
        }
    }

    // For customer adding or editing
    // Doing image click like this we won't be able to open newly added images that were added within this dialog.
    // Which I'm fine with but it may change later
    CustomerDialog(
        seed = customerSeed,
        isVisible = showCustomerDialog,
        onImageClick = { id -> showNoteImages(customerSeed.notes, id) },
        onDismiss = {
            showCustomerDialog = false
            // Reset seed data
            viewModel.customers.select(customerSeed.identifier)
        },
        onContinue = { viewModel.customers.save() }
    )

    // For ticket adding or editing
    TicketDialog(
        seed = ticketSeed,
        isVisible = showTicketDialog,
        onContinue = { viewModel.tickets.save() },
        onDismiss = {
            showTicketDialog = false

            // Reset seed data
            viewModel.tickets.select(ticketSeed.identifier)
        },
        onCustomerSearchQuery = { query -> viewModel.customers.search(query) },
        onUserSearchQuery = { query -> viewModel.users.search(query) }
    )

    // For inventory adding or editing
    InventoryDialog(
        seed = viewModel.inventory.seed,
        isVisible = showInventoryDialog,
        onDismiss = {
            showInventoryDialog = false

            // Reset seed data
            viewModel.inventory.select(inventorySeed.identifier)
        },
        onContinue = {
            viewModel.inventory.save()
            showInventoryDialog = false
        },
    )

    // For confirming deleting an item
    ConfirmDeleteDialog(
        dataType = dataType,
        isTwoStep = true,
        isVisible = showConfirmDeleteDialog,
        onConfirm = {
            when (dataType) {
                DataType.CUSTOMER -> viewModel.customers.remove(customerSeed.identifier)
                DataType.TICKET -> viewModel.tickets.remove(ticketSeed.identifier)
                DataType.INVENTORY -> viewModel.inventory.remove(inventorySeed.identifier)
                DataType.USER -> TODO()
                else -> {
                    // Do Nothing
                }
            }

            dataType = DataType.NONE
            showConfirmDeleteDialog = false
        },
        onCancel = {
            // Reset data types
            dataType = DataType.NONE

            // Hide dialog
            showConfirmDeleteDialog = false
        }
    )

    // For image viewing
    TmImageView(
        images = imagePopupList,
        isVisible = showImagePopup,
        onDismiss = {
            imagePopupList.clear()
            showImagePopup = false
        }
    )

    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TmTabs(
                modifier = Modifier.weight(1f),
                tabs = listOf("Customers", "Tickets", "Inventory"),
                selectedIndex = tabIndex.value,
                onSelect = { viewModel.changeMainTabIndex(it) }
            )

            TmIconButton(
                icon = Res.drawable.refresh_cw,
                contentDescription = "Reload the lists",
                onClick = { viewModel.refreshPagers() }
            )
        }

        if (loginState.value != LoginState.LOGGED_IN) {
            TmNotice(
                kind = TmNoticeKind.INFO,
                text = "You're not logged in, so these lists can't load. Log in from the bottom of the sidebar."
            )
        }

        when (tabIndex.value) {
            0 -> CustomerPanel(
                onAddCustomer = {
                    viewModel.customers.clearSelection()
                    showCustomerDialog = true
                },
                onEditCustomer = { showCustomerDialog = true },
                onCustomerSelectionChange = { id -> viewModel.customers.select(id) },
                onSearchQuery = { query -> viewModel.customers.search(query) },
                onDeleteCustomer = {
                    dataType = DataType.CUSTOMER
                    showConfirmDeleteDialog = true
                },
                onTicketClick = { identifier ->
                    viewModel.tickets.select(identifier)
                    showTicketDialog = true
                },
                onImageClick = { id -> showNoteImages(customerSeed.notes, id) },
                seed = customerSeed,
                pendingId = pendingCustomer,
                customerList = customerPagedItems.value,
                customerLazyListState = customerLazyListState
            )

            // TODO: Finish user implementation
            1 -> TicketPanel(
                ticketList = ticketPagedItems.value,
                seed = ticketSeed,
                pendingId = pendingTicket,
                onAddNewTicket = {
                    viewModel.tickets.clearSelection()
                    showTicketDialog = true
                },
                onEditTicket = { if (!ticketSeed.isNew) showTicketDialog = true },
                onSelectedTicketChange = { id -> viewModel.tickets.select(id) },
                onTicketScopeChange = { newScope ->
                    viewModel.tickets.setListScope(newScope)
                    // The new list is a different set of tickets, so start it from the top
                    scope.launch { ticketLazyListState.scrollToItem(0) }
                },
                onSearchQuery = { viewModel.tickets.search(it) },
                onDeleteTicket = {
                    dataType = DataType.TICKET
                    showConfirmDeleteDialog = true
                },
                ticketScope = ticketScope,
                ticketListState = ticketLazyListState,
                onImageClick = { id -> showNoteImages(ticketSeed.notes, id) }
            )

            2 -> InventoryPanel(
                inventoryList = inventoryPagedItems.value,
                selectedInventory = inventorySeed,
                pendingId = pendingInventory,
                onSelectedInventoryChange = { id -> viewModel.inventory.select(id) },
                onSearchQuery = { viewModel.inventory.search(it) },
                onAddInventory = {
                    viewModel.inventory.clearSelection()
                    showInventoryDialog = true
                },
                onEditInventory = { showInventoryDialog = true },
                onImageClick = { showImages(inventorySeed.images) },
                onNoteImageClick = { id -> showNoteImages(inventorySeed.notes, id) },
                onDeleteInventory = {
                    dataType = DataType.INVENTORY
                    showConfirmDeleteDialog = true
                },
                inventoryListState = inventoryLazyListState
            )
        }
    }
}