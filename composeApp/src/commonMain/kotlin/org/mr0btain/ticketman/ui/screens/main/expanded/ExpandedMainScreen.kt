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
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.pagination.offset.offsetPagerHandler
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.composables.image.ImagePopup
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketPanel
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

// TODO: When hitting confirm on any edit we hit the database even if changes haven't occurred
// TODO: Maybe move allot of these states and their seed values to viewmodel and move dialogs to App.kt if we use them in many screens

@Composable
fun ExpandedMainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    snackBarState: SnackbarHostState
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()

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
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

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

    // For customer adding or editing
    // Doing image click like this we won't be able to open newly added images that were added within this dialog.
    // Which I'm fine with but it may change later
    CustomerDialog(
        seed = customerSeed,
        isVisible = showCustomerDialog,
        onImageClick = { id ->
            customerSeed.notes
                .firstOrNull { it.identifier == id }
                ?.let { note ->
                    imagePopupList.clear()
                    imagePopupList.addAll(note.images)
                    showImagePopup = true
                }
        },
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

    Surface(
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.background
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
                    Button(
                        onClick = {
                            // For testing
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
                        onAddCustomer = {
                            // Clear seed
                            customerSeed.clear()

                            // Trigger dialog
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
                        onImageClick = { id ->
                            customerSeed.notes
                                .firstOrNull{ it.identifier == id }
                                ?.let {
                                    imagePopupList.clear()
                                    imagePopupList.addAll(it.images)
                                    showImagePopup = true
                                }
                        },
                        seed = customerSeed,
                        customerList = customerPagedItems?.value ?: emptyList(),
                        customerLazyListState = customerLazyListState,
                        coroutineScope = coroutineScope
                    )
                }

                // Ticket Panel
                1 -> {
                    // TODO: Finish user implementation
                    TicketPanel(
                        ticketList = ticketPagedItems?.value ?: emptyList(),
                        seed = ticketSeed,
                        onAddNewTicket = {
                            // Set up seed data
                            ticketSeed.clear()

                            // Show the dialog
                            showTicketDialog = true
                        },
                        onEditTicket = { if (!ticketSeed.isNew) showTicketDialog = true },
                        onSelectedTicketChange = { id -> viewModel.setSelectedTicket(id) },
                        onSelectedPanelChange = { selectedTicketPanel = it },
                        onSearchQuery = { viewModel.searchTickets(it) },
                        onDeleteTicket = {
                            dataType = DataType.TICKET
                            showConfirmDeleteDialog = true
                        },
                        selectedPanel = selectedTicketPanel,
                        ticketListState = ticketLazyListState,
                        coroutineScope = coroutineScope,
                        onImageClick = { id ->
                            ticketSeed.notes
                                .firstOrNull{ it.identifier == id }
                                ?.let {
                                    imagePopupList.clear()
                                    imagePopupList.addAll(it.images)
                                    showImagePopup = true
                                }
                        }
                    )
                }

                // Inventory panel
                2 -> {
                    InventoryPanel(
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
                        coroutineScope = coroutineScope,
                        inventoryListState = inventoryLazyListState
                    )
                }
            }
        }
    }
}