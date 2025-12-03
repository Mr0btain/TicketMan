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
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.components.pagination.offset.handleOffsetPager
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
import kotlin.uuid.ExperimentalUuidApi

// TODO: When hitting confirm on any edit we hit the database even if changes haven't occurred
// TODO: Maybe move allot of these states and their seed values to viewmodel and move dialogs to App.kt if we use them in many screens

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
    val selectedUser = viewModel.selectedUserData.collectAsState()
    val userLazyListState = rememberLazyListState()

    val currentUser = viewModel.selectedUserData.collectAsState()

    // For customer panel
    val selectedCustomer = viewModel.selectedCustomerData.collectAsState()
    val customerPagedItems = viewModel.customerPager?.items?.collectAsState()
    val customerLazyListState = rememberLazyListState()

    // Handle the customer pager
    handleOffsetPager(viewModel.customerPager, customerLazyListState)

    // For customer dialog
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // For ticket panel
    val selectedTicket = viewModel.selectedTicketData.collectAsState()
    val ticketSeed = viewModel.ticketSeed
    val ticketPagedItems =  viewModel.ticketPager?.items?.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // Handle ticket pager
    handleOffsetPager(viewModel.ticketPager, ticketLazyListState)

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }

    // For Inventory panel
    val selectedInventory = viewModel.selectedInventoryData.collectAsState()
    val inventorySeed = viewModel.inventorySeed
    val inventoryPagedItems = viewModel.inventoryPager?.items?.collectAsState()
    val inventoryLazyListState = rememberLazyListState()

    // Handle inventory pager
    handleOffsetPager(viewModel.inventoryPager, inventoryLazyListState)

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }

    // For confirm delete dialog
    var dataType by remember { mutableStateOf(DataType.NONE) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // For image viewing
    var showImagePopup by remember { mutableStateOf(false) }
    var imagePopupList by remember { mutableStateOf<List<StoredImage>?>(null) }

    // For customer adding or editing
    // Doing image click like this we won't be able to open newly added images that were added within this dialog.
    // Which im fine with but it may change later
    CustomerDialog(
        seed = selectedCustomer.value,
        isVisible = showCustomerDialog,
        onImageClick = { id ->
            selectedCustomer.value
                ?.notes
                ?.find { it.note.identifier == id }
                ?.let { (_, images) ->
                    imagePopupList = images
                    showImagePopup = true
                }
        },
        onDismiss = { showCustomerDialog = false },
        onContinue = { result ->
            // Check for a change, if so we need to update the data
            if (result != selectedCustomer.value) viewModel.addOrUpdateCustomer(result)
        },
        isDarkMode = isDarkMode
    )

    // For ticket adding or editing
    if (showTicketDialog) {
        TicketDialog(
            onDismiss = {
                // Clear seed
                ticketSeed.clear()

                // Hide dialog
                showTicketDialog = false
            },
            seed = ticketSeed,
            onContinue = {
                // TODO: Not sure if we should do this here or not.
                try {
                    // Check if there's been a change
                    if (ticketSeed.isDirty) {
                        // Update changes
                        ticketSeed.getChangedFields().forEach { change ->
                            when (change) {
                                ChangedField.TICKET -> {
                                    // Check if ticket is not null, then add or update it
                                    if (ticketSeed.ticket != null) {
                                        viewModel.addOrUpdateTicket(
                                            TicketData(
                                                ticketSeed.ticket!!,
                                                ticketSeed.notes
                                            )
                                        )
                                    }
                                }

                                else -> {
                                    // Something is really wrong if we hit this
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            },
            onCustomerSearchQuery = { query -> viewModel.searchCustomers(query) },
            onUserSearchQuery = { query -> viewModel.searchUsers(query) }
        )
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
    ConfirmDeleteDialog(
        dataType = dataType,
        isTwoStep = true,
        isVisible = showConfirmDeleteDialog,
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

    // For image viewing
    ImagePopup(
        images = imagePopupList,
        isVisible = showImagePopup,
        onDismiss = {
            imagePopupList = null
            showImagePopup = false
        }
    )

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
                        onImageClick = { id ->
                            selectedCustomer.value
                                ?.notes
                                ?.find { it.note.identifier == id }
                                ?.let { (_, images) ->
                                    imagePopupList = images
                                    showImagePopup = true
                                }
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
                        onSearchQuery = { viewModel.searchTickets(it) },
                        onDeleteTicket = {
                            dataType = DataType.TICKET
                            showConfirmDeleteDialog = true
                        },
                        selectedPanel = selectedTicketPanel,
                        ticketListState = ticketLazyListState,
                        coroutineScope = coroutineScope,
                        onImageClick = { id ->
                            selectedTicket.value
                                ?.notes
                                ?.find { it.note.identifier == id }
                                ?.let { (_, images) ->
                                    imagePopupList = images
                                    showImagePopup = true
                                }

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
                        onSearchQuery = { viewModel.searchInventory(it) },
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