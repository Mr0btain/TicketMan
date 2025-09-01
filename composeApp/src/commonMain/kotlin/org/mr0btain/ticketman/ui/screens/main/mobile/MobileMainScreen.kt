package org.mr0btain.ticketman.ui.screens.main.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.shared.DialogDismissReason
import org.mr0btain.ticketman.data.shared.MainItemType
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.dialog.SimpleConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.main.expanded.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.mobile.customer.CustomerPanelMobile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMainScreen(
    modifier: Modifier = Modifier,
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
    val userList = viewModel.dataHandler.userDataHandler.dataList.collectAsState()
    val currentUser = viewModel.dataHandler.userDataHandler.current.collectAsState()

    // For customer panel
    val customerData = viewModel.dataHandler.customerDataHandler.dataList.collectAsState()
    val selectedCustomer = viewModel.dataHandler.customerDataHandler.selectedId.collectAsState()
    val customerLazyListState = rememberLazyListState()

    // For customer dialog
    var customerSeedData by remember { mutableStateOf(Customer()) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // We need this outside of the customer dialog so we can modify this from other dialogs
    // and handle different scenarios
    val tempTicketHolder = remember { mutableStateListOf<Ticket>() }
    val customerTicketList = remember { mutableStateListOf<String>() }

    // For ticket panel
    val ticketList = viewModel.dataHandler.ticketDataHandler.dataList.collectAsState()
    val selectedTicket = viewModel.dataHandler.ticketDataHandler.selectedId.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }
    var ticketSeedData by remember { mutableStateOf(Ticket()) }

    // For Inventory panel
    val inventoryList = viewModel.dataHandler.inventoryDataHandler.dataList.collectAsState()
    val inventoryLazyListState = rememberLazyListState()
    val selectedInventoryId = viewModel.dataHandler.inventoryDataHandler.selectedId.collectAsState()

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }
    var inventorySeedData by remember { mutableStateOf(Inventory()) }

    // For confirm delete dialog
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf("") }
    var itemToDeleteType by remember { mutableStateOf(MainItemType.NONE) }
    var confirmDeleteDialogText by remember { mutableStateOf("") }

    val columnState = rememberScrollState()

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
                                viewModel.dataHandler.customerDataHandler.addOrUpdate(customerData)

                                // Check for new tickets and add
                                tempTicketHolder.forEach { ticket ->
                                    if (customerData.tickets.contains(ticket.identifier)) viewModel.dataHandler.ticketDataHandler.addOrUpdate(ticket)
                                }
                            }
                        }

                        else -> {
                            // Do nothing
                        }
                    }

                    // Reset starter data
                    customerSeedData = Customer()

                    // Reset customer ticket stuff
                    tempTicketHolder.clear()
                    customerTicketList.clear()

                    // Hide dialog
                    showCustomerDialog = false
                }
            },
            onAddNewTicket = { customerId ->
                coroutineScope.launch {
                    // Set is for new customer so ticket dialog knows wht to do with it
                    isTicketForNewCustomer = true

                    // Seed data with customer id
                    val newIdentifier = viewModel.dataHandler.ticketDataHandler.getNextId()

                    // Check if our identifier is not null
                    if (newIdentifier != null) {
                        ticketSeedData = Ticket(
                            customerId = customerId,
                            time = getTime(),
                            identifier = newIdentifier
                        )

                        // Pop up the dialog
                        showTicketDialog = true
                    } else {
                        AppLogger.m("ExpandedMainScreen:onAddNewTicket", "Error adding new ticket ID was null!")
                        snackBarState.showSnackbar("Error getting new ticket id!")
                    }
                }
            },
            isDarkMode = isDarkMode,
            seedData = customerSeedData,
            ticketList = customerTicketList,
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
                                    viewModel.dataHandler.ticketDataHandler.addOrUpdate(ticketInfo)
                                }
                            }
                        }

                        DialogDismissReason.CANCEL -> {
                            // Do nothing
                        }
                    }

                    // Reset seed data
                    ticketSeedData = Ticket()

                    // Close dialog
                    showTicketDialog = false
                }
            },
            isDarkMode = isDarkMode,
            seedData = ticketSeedData,
            customerData = customerData.value.map { it },
            users = userList.value,
            scrollBarsOnRight = scrollBarsOnRight,
            onAddNewCustomer = { name ->
                coroutineScope.launch {
                    val newIdentifier = viewModel.dataHandler.customerDataHandler.getNextId()

                    // Check if we can get a new identifier
                    if (newIdentifier != null) {
                        // Add seeded data if there is any
                        customerSeedData = Customer(name = name, time = getTime(), identifier = newIdentifier)

                        // Trigger dialog
                        showCustomerDialog = true
                    } else {
                        AppLogger.m("ExpandedMainScreen:onAddNewCustomer", "Error adding new customer ID was null!")
                        snackBarState.showSnackbar("Error getting new customer id!")
                    }
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
                                viewModel.dataHandler.inventoryDataHandler.addOrUpdate(inventoryItem)
                            }
                        }

                        else -> {
                            // Do nothing
                        }
                    }

                    // Reset seed data
                    inventorySeedData = Inventory()

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
                                // Remove customer
                                viewModel.dataHandler.customerDataHandler.remove(itemToDelete)

                                // Reset selected
                                viewModel.dataHandler.customerDataHandler.clearSelectedId()
                            }

                            MainItemType.TICKET -> {
                                // Remove ticket
                                viewModel.dataHandler.ticketDataHandler.remove(itemToDelete)

                                // Reset selected
                                viewModel.dataHandler.ticketDataHandler.clearSelectedId()
                            }
                            MainItemType.EMPLOYEE -> { /* We cant remove employees here */ }
                            MainItemType.INVENTORY -> {
                                // Remove item
                                viewModel.dataHandler.inventoryDataHandler.remove(itemToDelete)

                                // Reset selected
                                viewModel.dataHandler.inventoryDataHandler.clearSelectedId()
                            }
                            MainItemType.NONE -> { /* Do nothing */ }
                        }
                    }

                    // Reset values
                    itemToDelete = ""
                    itemToDeleteType = MainItemType.NONE
                    confirmDeleteDialogText = ""

                    // Close the dialog
                    showConfirmDeleteDialog = false
                }
            },
            isDarkMode = isDarkMode
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        SecondaryTabRow(
            selectedTabIndex = tabIndex.value,
            modifier = Modifier
                .padding(4.dp),
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

        Spacer(Modifier.padding(4.dp))

        when (tabIndex.value) {
            // Customer
            0 -> {
                CustomerPanelMobile(
                    isDarkMode = isDarkMode,
                    onAddCustomer = { name ->
                        coroutineScope.launch {
                            val newIdentifier = viewModel.dataHandler.customerDataHandler.getNextId()

                            // Check if we can et a new identifier
                            if (newIdentifier != null) {
                                // Set seed data
                                customerSeedData = Customer(
                                    name = name,
                                    time = getTime(),
                                    identifier = newIdentifier
                                )

                                // Trigger dialog
                                showCustomerDialog = true
                            } else {
                                AppLogger.m("ExpandedMainScreen:onAddCustomer", "Error adding customer ID was null!")
                                snackBarState.showSnackbar("Error getting new customer id!")
                            }
                        }
                    },
                    onEditCustomer = {
                        coroutineScope.launch {
                            val customer = viewModel.dataHandler.customerDataHandler.get(selectedCustomer.value)

                            if (customer != null) {
                                // Set seed data
                                customerSeedData = customer

                                // Trigger dialog
                                showCustomerDialog = true
                            } else {
                                AppLogger.m("ExpandedMainScreen:onEditCustomer", "Error getting customer data. value was null!")
                                snackBarState.showSnackbar("Error getting customer data!")
                            }
                        }
                    },
                    onCustomerSelectionChange = { id -> viewModel.dataHandler.customerDataHandler.setSelectedId(id)},
                    onDeleteCustomer = { id ->
                        coroutineScope.launch {
                            // TODO: Maybe add name of customer to this as well?
                            // TODO: Add ability to delete tickets for customer as well
                            // Check if item exists
                            if (customerData.value.firstOrNull { it.identifier == id } != null) {
                                // Set values
                                itemToDelete = id
                                itemToDeleteType = MainItemType.CUSTOMER
                                confirmDeleteDialogText = "Customer: $id"

                                // Trigger the dialog
                                showConfirmDeleteDialog = true
                            }
                        }
                    },
                    onTicketClick = { identifier ->
                        coroutineScope.launch {
                            val ticket = viewModel.dataHandler.ticketDataHandler.get(identifier)

                            if (ticket != null) {
                                // Set selected ticket
                                viewModel.dataHandler.ticketDataHandler.setSelectedId(identifier)

                                // Get seed data
                                ticketSeedData = ticket

                                // Pop up ticket dialog
                                showTicketDialog = true
                            } else {
                                AppLogger.m("ExpandedMainScreen:onTicketClick", "Error getting ticket info, data was null!")
                                snackBarState.showSnackbar("Error getting ticket info!")
                            }
                        }
                    },
                    selectedCustomerId = selectedCustomer.value,
                    customerList = customerData.value,
                    ticketList = ticketList.value,
                    customerLazyListState = customerLazyListState,
                    coroutineScope = coroutineScope,
                    scrollBarsOnRight = scrollBarsOnRight
                )
            }

            // Ticket
            1 -> {

            }

            // Inventory
            2 -> {

            }
        }
    }
}