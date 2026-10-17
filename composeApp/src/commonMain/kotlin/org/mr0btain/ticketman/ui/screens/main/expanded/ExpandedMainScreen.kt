package org.mr0btain.ticketman.ui.screens.main.expanded

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
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmTabs
import org.mr0btain.ticketman.ui.kit.data.ImagePopup
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketPanel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.refresh_cw
import kotlin.uuid.Uuid

// TODO: When hitting confirm on any edit we hit the database even if changes haven't occurred
// TODO: Maybe move allot of these states and their seed values to viewmodel and move dialogs to App.kt if we use them in many screens

@Composable
fun ExpandedMainScreen(
    viewModel: MainViewModel
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()
    val loginState = viewModel.loginState.collectAsState()

    // User stuff
    //val userPagedItems = viewModel.userPager?.items?.collectAsState()
    //val userLazyListState = rememberLazyListState()

    // For customer panel
    val customerSeed = viewModel.customerSeed
    val customerPagedItems = viewModel.customerPager?.items?.collectAsState()
    val customerLazyListState = rememberLazyListState()

    // Handler for the customer pager
    offsetPagerHandler(viewModel.customerPager, customerLazyListState)

    // For customer dialog
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // For ticket panel
    val ticketSeed = viewModel.ticketSeed
    val ticketPagedItems =  viewModel.ticketPager?.items?.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    val ticketScope by viewModel.ticketScope.collectAsState()
    val scope = rememberCoroutineScope()

    // Handler for ticket pager
    offsetPagerHandler(viewModel.ticketPager, ticketLazyListState)

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }

    // For Inventory panel
    val inventorySeed = viewModel.inventorySeed
    val inventoryPagedItems = viewModel.inventoryPager?.items?.collectAsState()
    val inventoryLazyListState = rememberLazyListState()

    // Handler for inventory pager
    offsetPagerHandler(viewModel.inventoryPager, inventoryLazyListState)

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }

    // For confirm delete dialog
    var dataType by remember { mutableStateOf(DataType.NONE) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    val imagePopupList = remember { mutableStateListOf<StoredImage>() }

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
            viewModel.setSelectedCustomer(customerSeed.identifier)
                    },
        onContinue = { viewModel.handleCustomerChanges() }
    )

    // For ticket adding or editing
    TicketDialog(
        seed = ticketSeed,
        isVisible = showTicketDialog,
        onContinue = { viewModel.handleTicketChange() },
        onDismiss = {
            showTicketDialog = false

            // Reset seed data
            viewModel.setSelectedTicket(ticketSeed.identifier)
                    },
        onCustomerSearchQuery = { query -> viewModel.searchCustomers(query) },
        onUserSearchQuery = { query -> viewModel.searchUsers(query) }
    )

    // For inventory adding or editing
    InventoryDialog(
        seed = viewModel.inventorySeed,
        isVisible = showInventoryDialog,
        onDismiss = {
            showInventoryDialog = false

            // Reset seed data
            viewModel.setSelectedInventory(inventorySeed.identifier)
                    },
        onContinue = {
            // TODO: Not yet implemented
        },
    )

    // For confirming deleting an item
    ConfirmDeleteDialog(
        dataType = dataType,
        isTwoStep = true,
        isVisible = showConfirmDeleteDialog,
        onConfirm = {
            when (dataType) {
                DataType.CUSTOMER -> viewModel.removeCustomer(customerSeed.identifier)
                DataType.TICKET -> viewModel.removeTicket(ticketSeed.identifier)
                DataType.INVENTORY -> viewModel.removeInventory(inventorySeed.identifier)
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
    ImagePopup(
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
                    customerSeed.clear()
                    showCustomerDialog = true
                },
                onEditCustomer = { showCustomerDialog = true },
                onCustomerSelectionChange = { id -> viewModel.setSelectedCustomer(id) },
                onSearchQuery = { query -> viewModel.searchCustomers(query) },
                onDeleteCustomer = {
                    dataType = DataType.CUSTOMER
                    showConfirmDeleteDialog = true
                },
                onTicketClick = { identifier ->
                    viewModel.setSelectedTicket(identifier)
                    showTicketDialog = true
                },
                onImageClick = { id -> showNoteImages(customerSeed.notes, id) },
                seed = customerSeed,
                customerList = customerPagedItems?.value ?: emptyList(),
                customerLazyListState = customerLazyListState
            )

            // TODO: Finish user implementation
            1 -> TicketPanel(
                ticketList = ticketPagedItems?.value ?: emptyList(),
                seed = ticketSeed,
                onAddNewTicket = {
                    ticketSeed.clear()
                    showTicketDialog = true
                },
                onEditTicket = { if (!ticketSeed.isNew) showTicketDialog = true },
                onSelectedTicketChange = { id -> viewModel.setSelectedTicket(id) },
                onTicketScopeChange = { newScope ->
                    viewModel.setTicketScope(newScope)
                    // The new list is a different set of tickets, so start it from the top
                    scope.launch { ticketLazyListState.scrollToItem(0) }
                },
                onSearchQuery = { viewModel.searchTickets(it) },
                onDeleteTicket = {
                    dataType = DataType.TICKET
                    showConfirmDeleteDialog = true
                },
                ticketScope = ticketScope,
                ticketListState = ticketLazyListState,
                onImageClick = { id -> showNoteImages(ticketSeed.notes, id) }
            )

            2 -> InventoryPanel(
                inventoryList = inventoryPagedItems?.value ?: emptyList(),
                selectedInventory = inventorySeed,
                onSelectedInventoryChange = { id -> viewModel.setSelectedInventory(id) },
                onSearchQuery = { viewModel.searchInventory(it) },
                onAddInventory = {
                    inventorySeed.clear()
                    showInventoryDialog = true
                },
                onEditInventory = { showInventoryDialog = true },
                onDeleteInventory = {
                    dataType = DataType.INVENTORY
                    showConfirmDeleteDialog = true
                },
                inventoryListState = inventoryLazyListState
            )
        }
    }
}