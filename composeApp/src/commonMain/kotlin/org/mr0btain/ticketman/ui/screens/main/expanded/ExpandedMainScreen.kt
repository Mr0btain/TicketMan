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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.pagination.offset.handleOffsetPager
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
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()

    // User stuff
    val userPagedItems = viewModel.userPager?.items?.collectAsState()

    val currentUser = viewModel.selectedUserData.collectAsState()

    // For customer panel
    val customerPagedItems = viewModel.customerPager?.items?.collectAsState()
    val selectedCustomer = viewModel.selectedCustomerData.collectAsState()
    val customerLazyListState = rememberLazyListState()

    // Handle the customer pager
    handleOffsetPager(viewModel.customerPager, customerLazyListState)

    // For customer dialog
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // For ticket panel
    val ticketPagedItems =  viewModel.ticketPager?.items?.collectAsState()
    val selectedTicket = viewModel.selectedTicketData.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // Handle ticket pager
    handleOffsetPager(viewModel.ticketPager, ticketLazyListState)

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }

    // For Inventory panel
    val inventoryPagedItems = viewModel.inventoryPager?.items?.collectAsState()
    val inventoryLazyListState = rememberLazyListState()
    val selectedInventory = viewModel.selectedInventoryData.collectAsState()

    // Handle inventory pager
    handleOffsetPager(viewModel.inventoryPager, inventoryLazyListState)

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }

    // For confirm delete dialog
    var dataType by remember { mutableStateOf(DataType.NONE) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    var imagePopupList by remember { mutableStateOf<List<ImageData>?>(null) }

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
                seedData = customerData
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
            isTwoStep = true,
            onConfirm = {
                when (dataType) {
                    DataType.CUSTOMER -> { viewModel.removeSelectedCustomer() }
                    DataType.TICKET -> TODO()
                    DataType.INVENTORY -> TODO()
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
    }

    // For image viewing
    if (showImagePopup) {
        imagePopupList?.let { imageData ->
            ImagePopup(
                images = imageData,
                onDismiss = {
                    imagePopupList = null
                    showImagePopup = false
                }
            )
        }
    }

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
                            // TODO
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

            HorizontalDivider()

            when (tabIndex.value) {
                0 -> {
                    CustomerPanel(
                        isDarkMode = isDarkMode,
                        onAddCustomer = {
                            // Set seed data
                            viewModel.setupNewCustomer()

                            // Trigger dialog
                            showCustomerDialog = true
                        },
                        onEditCustomer = {
                            if (selectedCustomer.value != null)
                                showCustomerDialog = true
                        },
                        onCustomerSelectionChange = { id -> viewModel.changeSelectedCustomer(id) },
                        onSearchQuery = { query -> viewModel.searchCustomers(query) },
                        onDeleteCustomer = {
                            dataType = DataType.CUSTOMER
                            showConfirmDeleteDialog = true
                        },
                        onTicketClick = { identifier ->
                            viewModel.changeSelectedTicket(identifier)

                            showTicketDialog = true
                        },
                        onImageClick = { imageList ->
                            imagePopupList = imageList
                            showImagePopup = true
                        },
                        selectedCustomer = selectedCustomer.value,
                        customerList = customerPagedItems?.value ?: emptyList(),
                        customerLazyListState = customerLazyListState,
                        coroutineScope = coroutineScope
                    )
                }

                // Ticket Panel
                1 -> {
                    TicketPanel(
                        isDarkMode = isDarkMode,
                        ticketList = ticketPagedItems?.value ?: emptyList(),
                        currentUser = currentUser.value,
                        selectedTicket = selectedTicket.value,
                        onAddNewTicket = {
                            // Set up seed data
                            viewModel.setupNewTicket()

                            // Show the dialog
                            showTicketDialog = true
                        },
                        onEditTicket = { if (selectedTicket.value != null) showTicketDialog = true },
                        onSelectedTicketChange = { id -> viewModel.changeSelectedTicket(id) },
                        onSelectedPanelChange = { selectedTicketPanel = it },
                        onDeleteTicket = {
                            dataType = DataType.TICKET
                            showConfirmDeleteDialog = true
                        },
                        selectedPanel = selectedTicketPanel,
                        ticketListState = ticketLazyListState,
                        coroutineScope = coroutineScope,
                        onImageClick = { imageList ->
                            imagePopupList = imageList
                            showImagePopup = true
                        }
                    )
                }

                // Inventory panel
                2 -> {
                    InventoryPanel(
                        isDarkMode = isDarkMode,
                        inventoryList = inventoryPagedItems?.value ?: emptyList(),
                        selectedInventory = selectedInventory.value,
                        onSelectedInventoryChange = { id -> viewModel.changeSelectedInventory(id) },
                        onAddInventory = {
                            viewModel.setupNewInventory()
                            showInventoryDialog = true
                        },
                        onEditInventory = { if (selectedInventory.value != null) showInventoryDialog = true },
                        onDeleteInventory = {
                            dataType = DataType.INVENTORY
                            showConfirmDeleteDialog = true
                        },
                        coroutineScope = coroutineScope,
                        inventoryListState = inventoryLazyListState
                    )
                }
            }
        }
    }
}