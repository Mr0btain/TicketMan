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
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.utils.getNameForDataType
import org.mr0btain.ticketman.ui.composables.image.ImagePopup
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketPanel
import kotlin.uuid.ExperimentalUuidApi

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
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // For ticket panel
    val ticketPagedItems =  viewModel.ticketListFlow.collectAsLazyPagingItems()
    val selectedTicket = viewModel.selectedTicketData.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }

    // For Inventory panel
    val inventoryPagedItems = viewModel.inventoryListFlow.collectAsLazyPagingItems()
    val inventoryLazyListState = rememberLazyListState()
    val selectedInventory = viewModel.selectedInventoryData.collectAsState()

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }

    // For confirm delete dialog
    var dataType by remember { mutableStateOf(DataType.NONE) }
    var subDataType by remember { mutableStateOf(DataType.NONE) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    val images by remember { mutableStateOf<List<ImageData>>(emptyList()) }

    // For customer adding or editing
    if (showCustomerDialog) {
        selectedCustomer.value?.let { customerData ->
            CustomerDialog(
                onDismiss = { showCustomerDialog = false },
                onContinue = { result ->
                    // Check for a change, if so we need to update the data
                    if (result != customerData) viewModel.addOrUpdateCustomer(result)
                },
                isDarkMode = isDarkMode,
                seedData = customerData,
                scrollBarsOnRight = scrollBarsOnRight
            )
        }
    }

    // For ticket adding or editing
    if (showTicketDialog) {
        selectedTicket.value?.let { ticketData ->
            TicketDialog(
                onDismiss = { showTicketDialog = false },
                isDarkMode = isDarkMode,
                seedData = ticketData,
                scrollBarsOnRight = scrollBarsOnRight,
                onContinue = { result ->
                    // Check if its changed, if so update it
                    if (selectedTicket.value != result) viewModel.addOrUpdateTicket(result)
                },
            )
        }

    }

    // For inventory adding or editing
    if (showInventoryDialog) {
        selectedInventory.value?.let { inventoryData ->
            InventoryDialog(
                seedData = inventoryData,
                onDismiss = { showInventoryDialog = false },
                isDarkMode = isDarkMode,
                scrollBarsOnRight = scrollBarsOnRight,
                onContinue = {
                    // TODO: Note yet implemented
                }
            )
        }
    }

    // For confirming deleting an item
    if (showConfirmDeleteDialog) {
        ConfirmDeleteDialog(
            dataType = dataType,
            onContinue = {
                when (dataType) {
                    DataType.CUSTOMER -> {
                        when (subDataType) {
                            DataType.TICKET -> {

                            }
                            DataType.EMAIL -> TODO()
                            DataType.PHONE -> TODO()
                            DataType.NOTE -> TODO()
                            DataType.ADDRESS -> TODO()
                            else -> {
                                // Do nothing
                            }
                        }
                    }
                    DataType.TICKET -> TODO()
                    DataType.INVENTORY -> TODO()
                    DataType.USER -> TODO()
                    else -> {
                        // Do Nothing
                    }
                }
            },
            onDismiss = {
                // Reset data types
                dataType = DataType.NONE
                subDataType = DataType.NONE

                // Hide dialog
                showConfirmDeleteDialog = false
                        },
            name = getNameForDataType(dataType, subDataType)
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
                            onClick = { viewModel.changeShowLogin(true) }
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
                                    viewModel.setupNewCustomer(name)

                                    // Trigger dialog
                                    showCustomerDialog = true
                                }
                            },
                            onEditCustomer = {
                                if (selectedCustomer.value != null)
                                    showCustomerDialog = true
                            },
                            onCustomerSelectionChange = { id -> viewModel.changeSelectedCustomer(id) },
                            onDeleteCustomer = { id ->
                                coroutineScope.launch {
                                    /*
                                    // Set values
                                        itemToDelete = id
                                        itemToDeleteType = MainItemType.CUSTOMER
                                        confirmDeleteDialogText = "Customer: $id"

                                        // Trigger the dialog
                                        showConfirmDeleteDialog = true
                                     */
                                }
                            },
                            onTicketClick = { identifier ->
                                coroutineScope.launch {
                                    viewModel.changeSelectedTicket(identifier)

                                    showTicketDialog = true
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
                                    viewModel.setupNewTicket()

                                    // Show the dialog
                                    showTicketDialog = true
                                }
                            },
                            onEditTicket = {
                                coroutineScope.launch {
                                    if (selectedTicket.value != null)
                                        showTicketDialog = true
                                }
                            },
                            onSelectedTicketChange = { id -> viewModel.changeSelectedTicket(id) },
                            onSelectedPanelChange = { selectedTicketPanel = it },
                            onDeleteTicket = { id ->
                                coroutineScope.launch {
                                    /*
                                    // Set values
                                    itemToDelete = id
                                    itemToDeleteType = MainItemType.TICKET
                                    confirmDeleteDialogText = "Ticket: $id"

                                    // Trigger the dialog
                                    showConfirmDeleteDialog = true
                                     */
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
                            onSelectedInventoryChange = { id -> viewModel.changeSelectedInventory(id) },
                            onAddInventory = {
                                coroutineScope.launch {
                                    viewModel.setupNewInventory()

                                    // Popup dialog
                                    showInventoryDialog = true
                                }
                            },
                            onEditInventory = {
                                coroutineScope.launch {
                                    if (selectedInventory.value != null)
                                        showInventoryDialog = true
                                }
                            },
                            onDeleteInventory = {
                                coroutineScope.launch {
                                    selectedInventory.value?.let { inventoryData ->
                                       /*
                                        itemToDelete = inventoryData.inventory.identifier
                                        itemToDeleteType = MainItemType.INVENTORY
                                        confirmDeleteDialogText = inventoryData.inventory.name

                                        // Trigger the dialog
                                        showConfirmDeleteDialog = true
                                        */
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