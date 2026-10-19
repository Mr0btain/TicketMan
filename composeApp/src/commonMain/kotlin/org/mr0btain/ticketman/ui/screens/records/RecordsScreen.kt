package org.mr0btain.ticketman.ui.screens.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerLoader
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDeleteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmTabs
import org.mr0btain.ticketman.ui.kit.images.TmImageView
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketPanel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.log_in
import ticketman.composeapp.generated.resources.refresh_cw
import kotlin.uuid.Uuid

@Composable
fun RecordsScreen(
    viewModel: MainViewModel
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()
    val loginState = viewModel.session.loginState.collectAsState()
    // Restricted users can look but not change anything, so no add, edit or delete
    val canEdit by viewModel.session.canEdit.collectAsState()

    // For customer panel
    val customerSeed = viewModel.customers.seed
    val customerPagedItems = viewModel.customers.pager.items.collectAsState()
    val customerLoadError by viewModel.customers.pager.loadError.collectAsState()
    val customerLazyListState = rememberLazyListState()
    val pendingCustomer by viewModel.customers.selection.pending.collectAsState()

    // Handler for the customer pager
    OffsetPagerLoader(viewModel.customers.pager, customerLazyListState)

    // For customer dialog
    var showCustomerDialog by remember { mutableStateOf(false) }

    // For ticket panel
    val ticketSeed = viewModel.tickets.seed
    val ticketPagedItems = viewModel.tickets.pager.items.collectAsState()
    val ticketLoadError by viewModel.tickets.pager.loadError.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    val pendingTicket by viewModel.tickets.selection.pending.collectAsState()
    val ticketScope by viewModel.tickets.listScope.collectAsState()
    val scope = rememberCoroutineScope()

    // Handler for ticket pager
    OffsetPagerLoader(viewModel.tickets.pager, ticketLazyListState)

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }

    // For Inventory panel
    val inventorySeed = viewModel.inventory.seed
    val inventoryPagedItems = viewModel.inventory.pager.items.collectAsState()
    val inventoryLoadError by viewModel.inventory.pager.loadError.collectAsState()
    val inventoryLazyListState = rememberLazyListState()
    val pendingInventory by viewModel.inventory.selection.pending.collectAsState()

    // Handler for inventory pager
    OffsetPagerLoader(viewModel.inventory.pager, inventoryLazyListState)

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }

    // For confirm delete dialog
    var dataType by remember { mutableStateOf(DataType.NONE) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    val imagePopupList = remember { mutableStateListOf<StoredImage>() }
    var imagePopupPage by remember { mutableIntStateOf(0) }

    fun showImages(images: List<StoredImage>, page: Int) {
        imagePopupList.clear()
        imagePopupList.addAll(images)
        imagePopupPage = page
        showImagePopup = true
    }

    // Opens the photos of the note with this identifier, at the one clicked
    fun showNoteImages(notes: List<Note>, noteIdentifier: Uuid, page: Int) {
        notes.firstOrNull { it.identifier == noteIdentifier }?.let { note -> showImages(note.images, page) }
    }

    // For customer adding or editing
    // Doing image click like this we won't be able to open newly added images that were added within this dialog.
    // Which I'm fine with but it may change later
    CustomerDialog(
        seed = customerSeed,
        isVisible = showCustomerDialog,
        onImageClick = { id, page -> showNoteImages(customerSeed.notes, id, page) },
        onDismiss = {
            showCustomerDialog = false
            // Reset seed data
            viewModel.customers.select(customerSeed.identifier)
        },
        onContinue = {
            viewModel.customers.save()
            showCustomerDialog = false
        }
    )

    // For ticket adding or editing
    TicketDialog(
        seed = ticketSeed,
        isVisible = showTicketDialog,
        onContinue = {
            viewModel.tickets.save()
            showTicketDialog = false
        },
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
    TmConfirmDeleteDialog(
        itemName = dataType.stringName,
        isTwoStep = true,
        isVisible = showConfirmDeleteDialog,
        onConfirm = {
            when (dataType) {
                DataType.CUSTOMER -> viewModel.customers.remove(customerSeed.identifier)
                DataType.TICKET -> viewModel.tickets.remove(ticketSeed.identifier)
                DataType.INVENTORY -> viewModel.inventory.remove(inventorySeed.identifier)
                // Users are deleted from the admin screen, nothing here asks to delete one
                else -> Unit
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
        startPage = imagePopupPage,
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
        }

        if (loginState.value != LoginState.LOGGED_IN) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TmNotice(
                    modifier = Modifier.weight(1f),
                    kind = TmNoticeKind.INFO,
                    text = "You're not logged in, so these lists can't load."
                )
                TmButton(
                    text = "Log in",
                    onClick = { viewModel.session.changeShowLogin(true) },
                    variant = TmButtonVariant.SECONDARY,
                    leadingIcon = Res.drawable.log_in
                )
            }
        }

        when (tabIndex.value) {
            0 -> CustomerPanel(
                onAddCustomer = {
                    viewModel.customers.clearSelection()
                    showCustomerDialog = true
                },
                onEditCustomer = { showCustomerDialog = true },
                canEdit = canEdit,
                onCustomerSelectionChange = { id -> viewModel.customers.select(id) },
                onSearchQuery = { query -> viewModel.customers.search(query) },
                onDeleteCustomer = {
                    dataType = DataType.CUSTOMER
                    showConfirmDeleteDialog = true
                },
                onTicketClick = { identifier ->
                    viewModel.tickets.select(identifier)
                    // The dialog is for editing, someone who can only look sees it on the tickets tab
                    if (canEdit) showTicketDialog = true else viewModel.changeMainTabIndex(1)
                },
                onImageClick = { id, page -> showNoteImages(customerSeed.notes, id, page) },
                seed = customerSeed,
                pendingId = pendingCustomer,
                customerList = customerPagedItems.value,
                customerLazyListState = customerLazyListState,
                loadError = customerLoadError
            )

            1 -> TicketPanel(
                ticketList = ticketPagedItems.value,
                seed = ticketSeed,
                pendingId = pendingTicket,
                onAddNewTicket = {
                    viewModel.tickets.clearSelection()
                    showTicketDialog = true
                },
                onEditTicket = { if (!ticketSeed.isNew) showTicketDialog = true },
                canEdit = canEdit,
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
                onImageClick = { id, page -> showNoteImages(ticketSeed.notes, id, page) },
                loadError = ticketLoadError
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
                canEdit = canEdit,
                onImageClick = { page -> showImages(inventorySeed.images, page) },
                onNoteImageClick = { id, page -> showNoteImages(inventorySeed.notes, id, page) },
                onDeleteInventory = {
                    dataType = DataType.INVENTORY
                    showConfirmDeleteDialog = true
                },
                inventoryListState = inventoryLazyListState,
                loadError = inventoryLoadError
            )
        }
    }
}