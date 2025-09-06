package org.mr0btain.ticketman.ui.screens.main.expanded

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.inventory.InventoryData
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.shared.DialogDismissReason
import org.mr0btain.ticketman.data.shared.MainItemType
import org.mr0btain.ticketman.data.time.getTime
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.composables.image.ImagePopup
import org.mr0btain.ticketman.ui.dialog.SimpleConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketPanel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Maybe make this expect actual of some sort for different platforms or screen sizes
// TODO: When hitting confirm on any edit we hit the database even if changes haven't occurred

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class, FlowPreview::class)
@Composable
fun ExpandedMainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    scrollBarsOnBottom: Boolean,
    scrollBarsOnRight: Boolean,
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()

    // User stuff
    val userPagedItems = viewModel.userListFlow.collectAsLazyPagingItems()
    val currentUser = viewModel.selectedUserData.collectAsState()

    // For customer panel
    val customerPagedItems = viewModel.customerListFlow.collectAsLazyPagingItems()
    val selectedCustomer = viewModel.selectedCustomerData.collectAsState()
    val customerLazyListState = rememberLazyListState()


    // For customer dialog
    var customerSeedData: CustomerData by remember { mutableStateOf(CustomerData()) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // We need this outside of the customer dialog so we can modify this from other dialogs
    // and handle different scenarios
    val tempTicketHolder = remember { mutableStateListOf<Ticket>() }
    val customerTicketList = remember { mutableStateListOf<Uuid>() }

    // For ticket panel
    val ticketPagedItems =  viewModel.ticketListFlow.collectAsLazyPagingItems()
    val selectedTicket = viewModel.selectedTicketData.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }
    var ticketSeedData by remember { mutableStateOf(TicketData()) }

    // For Inventory panel
    val inventoryPagedItems = viewModel.inventoryListFlow.collectAsLazyPagingItems()
    val inventoryLazyListState = rememberLazyListState()
    val selectedInventory = viewModel.selectedInventoryData.collectAsState()

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }
    var inventorySeedData by remember { mutableStateOf(InventoryData()) }

    // For confirm delete dialog
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf(Uuid.random()) }
    var itemToDeleteType by remember { mutableStateOf(MainItemType.NONE) }
    var confirmDeleteDialogText by remember { mutableStateOf("") }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    val images by remember { mutableStateOf<List<StoredImage>>(emptyList()) }

    // For customer adding or editing
    if (showCustomerDialog) {
        CustomerDialog(
            onDismiss = { data, reason ->
                coroutineScope.launch {
                    // Check if we should update or add
                    when (reason) {
                        DialogDismissReason.CONTINUE -> {
                            // Add or update customer
                            data?.let { customerData ->
                                //viewModel.dataHandler.customerDataHandler.addOrUpdate(customerData)

                                // Check for new tickets and add
                                tempTicketHolder.forEach { ticket ->
                                    //if (customerData.tickets.contains(ticket.identifier)) viewModel.dataHandler.ticketDataHandler.addOrUpdate(ticket)
                                }
                            }
                        }

                        else -> {
                            // Do nothing
                        }
                    }

                    // Reset starter data
                    customerSeedData = CustomerData()

                    // Reset customer ticket stuff
                    tempTicketHolder.clear()
                    customerTicketList.clear()

                    // Hide dialog
                    showCustomerDialog = false
                }
            },
            onAddNewTicket = {
                coroutineScope.launch {
                    // Set is for new customer so ticket dialog knows wht to do with it
                    isTicketForNewCustomer = true

                    ticketSeedData = TicketData()

                    // Pop up the dialog
                    showTicketDialog = true
                }
            },
            isDarkMode = isDarkMode,
            seedData = customerSeedData,
            scrollBarsOnRight = scrollBarsOnRight
        )
    }

    // For ticket adding or editing
    if (showTicketDialog) {
        TicketDialog(
            onDismiss = { data, reason ->
                coroutineScope.launch {
                    // Check if it's an actual add else data will be null
                    when (reason) {
                        DialogDismissReason.CONTINUE -> {
                            // Check if this is for a new customer as we don't need to add them unless we fully finish adding
                            // customer itself
                            if (isTicketForNewCustomer) {
                                // Add to temp holder list before we finalize customer
                                tempTicketHolder.add(data!!)

                                // Add to customer ticket list
                                customerTicketList.add(data.identifier)

                                // Reset check
                                isTicketForNewCustomer = false
                            } else {
                                // Else add it to the database
                                data?.let { ticketInfo ->
                                    // viewModel.dataHandler.ticketDataHandler.addOrUpdate(ticketInfo)
                                }
                            }
                        }

                        DialogDismissReason.CANCEL -> {
                            // Do nothing
                        }
                    }

                    // Reset seed data
                    ticketSeedData = TicketData()

                    // Close dialog
                    showTicketDialog = false
                }
            },
            isDarkMode = isDarkMode,
            seedData = ticketSeedData,
            scrollBarsOnRight = scrollBarsOnRight,
            onAddNewCustomer = { name ->
                coroutineScope.launch {
                    // Add seeded data if there is any
                    customerSeedData = CustomerData(Customer(name = name, time = getTime()))

                    // Trigger dialog
                    showCustomerDialog = true
                }
            }
        )
    }

    // For inventory adding or editing
    if (showInventoryDialog) {
        InventoryDialog(
            seedData = inventorySeedData,
            onDismiss = { data, reason ->
                coroutineScope.launch {
                    when (reason) {
                        DialogDismissReason.CONTINUE -> {
                            data?.let { inventoryItem ->
                                //viewModel.dataHandler.inventoryDataHandler.addOrUpdate(inventoryItem)
                            }
                        }

                        else -> {
                            // Do nothing
                        }
                    }

                    // Reset seed data
                    inventorySeedData = InventoryData()

                    // Close dialog
                    showInventoryDialog = false
                }
            },
            isDarkMode = isDarkMode,
            scrollBarsOnRight = scrollBarsOnRight
        )
    }

    // For confirming deleting an item
    if (showConfirmDeleteDialog) {
        SimpleConfirmDeleteDialog(
            itemName = confirmDeleteDialogText,
            onDismissRequest = { confirmed ->
                coroutineScope.launch {
                    if (confirmed) {
                        // Delete item
                        when (itemToDeleteType) {
                            MainItemType.CUSTOMER -> {
                                /*
                                // Remove customer
                                viewModel.dataHandler.customerDataHandler.remove(itemToDelete)

                                // Reset selected
                                viewModel.dataHandler.customerDataHandler.clearSelectedId()
                                 */
                            }

                            MainItemType.TICKET -> {
                                /*
                                // Remove ticket
                                viewModel.dataHandler.ticketDataHandler.remove(itemToDelete)

                                // Reset selected
                                viewModel.dataHandler.ticketDataHandler.clearSelectedId()
                                 */
                            }
                            MainItemType.EMPLOYEE -> { /* We cant remove employees here */ }
                            MainItemType.INVENTORY -> {
                                /*
                                // Remove item
                                viewModel.dataHandler.inventoryDataHandler.remove(itemToDelete)

                                // Reset selected
                                viewModel.dataHandler.inventoryDataHandler.clearSelectedId()
                                 */
                            }
                            MainItemType.NONE -> { /* Do nothing */ }
                        }
                    }

                    // Reset values
                    itemToDelete = Uuid.random()
                    itemToDeleteType = MainItemType.NONE
                    confirmDeleteDialogText = ""

                    // Close the dialog
                    showConfirmDeleteDialog = false
                }
            },
            isDarkMode = isDarkMode
        )
    }

    // For image viewing
    if (showImagePopup) {
        ImagePopup(
            images = images,
            isDarkMode = isDarkMode,
            onDismiss = { showImagePopup = false }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier.width(390.dp)
                    ) {
                        SecondaryTabRow(
                            selectedTabIndex = tabIndex.value,
                            modifier = Modifier
                                .padding(top = 4.dp, start = 4.dp)
                                .wrapContentWidth(),
                            containerColor = Color.Transparent
                        ) {
                            // Customer Tab
                            Tab(
                                selected = tabIndex.value == 0,
                                onClick = { viewModel.changeMainTabIndex(0) },
                                modifier = Modifier.wrapContentWidth(),
                                text = {
                                    Text(
                                        text = "Customers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )

                            // Ticket Tab
                            Tab(
                                selected = tabIndex.value == 1,
                                onClick = { viewModel.changeMainTabIndex(1) },
                                modifier = Modifier.wrapContentWidth(),
                                text = {
                                    Text(
                                        text = "Tickets",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )

                            // Inventory Tab
                            Tab(
                                selected = tabIndex.value == 2,
                                onClick = { viewModel.changeMainTabIndex(2) },
                                modifier = Modifier.wrapContentWidth(),
                                text = {
                                    Text(
                                        text = "Inventory",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // TODO: ---------------------------------------------------------------------------------------
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // Nothing ATM
                                }
                            }
                        ) {
                            Text("Sync")
                        }

                        // TODO: Make this only work if not logged in or hide if logged in
                        Button(
                            onClick = { viewModel.updateShowLogin(true) }
                        ) {
                            Text("Log In")
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier,
                    thickness = DividerDefaults.Thickness,
                    color = if (isDarkMode) MaterialTheme.colorScheme.surface else DividerDefaults.color
                )

                when (tabIndex.value) {
                    0 -> {
                        CustomerPanel(
                            isDarkMode = isDarkMode,
                            onAddCustomer = { name ->
                                coroutineScope.launch {
                                    // Set seed data
                                    customerSeedData = CustomerData(customer = Customer(name = name, time = getTime()))

                                    // Trigger dialog
                                    showCustomerDialog = true
                                }
                            },
                            onEditCustomer = {
                                selectedCustomer.value?.let { customerData ->
                                    // Set seed data
                                    customerSeedData = customerData

                                    // Trigger dialog
                                    showCustomerDialog = true
                                }
                            },
                            onCustomerSelectionChange = { id -> viewModel.updateSelectedCustomer(id) },
                            onDeleteCustomer = { id ->
                                coroutineScope.launch {
                                    // Set values
                                        itemToDelete = id
                                        itemToDeleteType = MainItemType.CUSTOMER
                                        confirmDeleteDialogText = "Customer: $id"

                                        // Trigger the dialog
                                        showConfirmDeleteDialog = true
                                }
                            },
                            onTicketClick = { identifier ->
                                coroutineScope.launch {
                                    viewModel.updateSelectedTicket(identifier)

                                    viewModel.selectedTicketData.value?.let { ticketData ->
                                        // Get seed data
                                        ticketSeedData = ticketData

                                        // Pop up ticket dialog
                                        showTicketDialog = true
                                    }
                                }
                            },
                            selectedCustomer = selectedCustomer.value,
                            pagingItems = customerPagedItems,
                            customerLazyListState = customerLazyListState,
                            coroutineScope = coroutineScope,
                            scrollBarsOnRight = scrollBarsOnRight,
                            scrollBarsOnBottom = scrollBarsOnBottom
                        )
                    }

                    // Ticket Panel
                    1 -> {
                       TicketPanel(
                            isDarkMode = isDarkMode,
                            pagingItems = ticketPagedItems,
                            currentUser = currentUser.value,
                            scrollBarsOnRight = scrollBarsOnRight,
                            selectedTicket = selectedTicket.value,
                            onAddNewTicket = {
                                coroutineScope.launch {
                                    // Set up seed data
                                    ticketSeedData = TicketData()

                                    // Show the dialog
                                    showTicketDialog = true
                                }
                            },
                            onEditTicket = {
                                coroutineScope.launch {
                                    selectedTicket.value?.let { ticketData ->
                                        // Get seed data
                                        ticketSeedData = ticketData

                                        // Trigger dialog if we have an actual item selected
                                        showTicketDialog = true
                                    }
                                }
                            },
                            onSelectedTicketChange = { id -> viewModel.updateSelectedTicket(id) },
                            onSelectedPanelChange = { selectedTicketPanel = it },
                            onDeleteTicket = { id ->
                                coroutineScope.launch {
                                    // Set values
                                    itemToDelete = id
                                    itemToDeleteType = MainItemType.TICKET
                                    confirmDeleteDialogText = "Ticket: $id"

                                    // Trigger the dialog
                                    showConfirmDeleteDialog = true
                                }
                            },
                            selectedPanel = selectedTicketPanel,
                            ticketListState = ticketLazyListState,
                            coroutineScope = coroutineScope,
                            onImageClick = {
                                // Nothing yet
                            }
                        )
                    }

                    // Inventory panel
                    2 -> {
                        InventoryPanel(
                            isDarkMode = isDarkMode,
                            inventoryList = inventoryPagedItems,
                            selectedInventory = selectedInventory.value,
                            onSelectedInventoryChange = { id -> viewModel.updateSelectedInventory(id) },
                            onAddInventory = {
                                coroutineScope.launch {
                                    // Set seed data
                                    inventorySeedData = InventoryData()

                                    // Popup dialog
                                    showInventoryDialog = true
                                }
                            },
                            onEditInventory = {
                                coroutineScope.launch {
                                    selectedInventory.value?.let { inventoryData ->
                                        // Set seed data
                                        inventorySeedData = inventoryData

                                        // Popup dialog
                                        showInventoryDialog = true
                                    }
                                }
                            },
                            onDeleteInventory = {
                                coroutineScope.launch {
                                    selectedInventory.value?.let { inventoryData ->
                                        itemToDelete = inventoryData.inventory.identifier
                                        itemToDeleteType = MainItemType.INVENTORY
                                        confirmDeleteDialogText = inventoryData.inventory.name

                                        // Trigger the dialog
                                        showConfirmDeleteDialog = true
                                    }
                                }
                            },
                            coroutineScope = coroutineScope,
                            inventoryListState = inventoryLazyListState,
                            scrollBarsOnRight = scrollBarsOnRight
                        )
                    }
                }
            }
        }
    }
}