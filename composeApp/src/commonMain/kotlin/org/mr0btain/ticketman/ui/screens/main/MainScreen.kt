package org.mr0btain.ticketman.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.components.customer.CustomerData
import org.mr0btain.ticketman.components.data.components.customer.CustomerInfo
import org.mr0btain.ticketman.components.data.components.inventory.InventoryItem
import org.mr0btain.ticketman.components.data.components.shared.DialogDismissReason
import org.mr0btain.ticketman.components.data.components.shared.MainItemType
import org.mr0btain.ticketman.components.data.components.ticket.TicketInfo
import org.mr0btain.ticketman.components.data.components.shared.getTime
import org.mr0btain.ticketman.ui.dialog.SimpleConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.main.components.inventory.InventoryPanel
import org.mr0btain.ticketman.ui.screens.main.components.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.components.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.components.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.components.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.main.components.ticket.TicketPanel

/**
 * The main landing screen
 */
// Probably don't need some of these actions in a coroutine.
// But the less we have to do on the main thread the better
// I guess.

// TODO: Something with snackbar? maybe errors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    scrollBarsOnBottom: Boolean,
    scrollBarsOnRight: Boolean,
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    // Tab for switching between panels
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()

    // Employee stuff
    val employeeList = viewModel.employeeList.collectAsState()
    val currentEmployee = viewModel.employee.collectAsState()

    // For customer panel
    val customerData = viewModel.customerDataList.collectAsState()
    val selectedCustomer = viewModel.selectedCustomerId.collectAsState()
    val customerLazyListState = rememberLazyListState()

    // For customer dialog
    var customerSeedData by remember { mutableStateOf(CustomerData()) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var isTicketForNewCustomer by remember { mutableStateOf(false) }

    // We need this outside of the customer dialog so we can modify this from other dialogs
    // and handle different scenarios
    val tempTicketHolder = remember { mutableStateListOf<TicketInfo>() }
    val customerTicketList = remember { mutableStateListOf<Int>() }

    // For ticket panel
    val ticketList = viewModel.ticketDataList.collectAsState()
    val selectedTicket = viewModel.selectedTicketId.collectAsState()
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }
    var ticketSeedData by remember { mutableStateOf(TicketInfo()) }

    // For Inventory panel
    val inventoryList = viewModel.inventoryList.collectAsState()
    val inventoryLazyListState = rememberLazyListState()
    val selectedInventoryId = viewModel.selectedInventoryId.collectAsState()

    // For inventory dialog
    var showInventoryDialog by remember { mutableStateOf(false) }
    var inventorySeedData by remember { mutableStateOf(InventoryItem()) }

    // For confirm delete dialog
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var itemToDeleteType by remember { mutableStateOf(MainItemType.NONE) }
    var confirmDeleteDialogText by remember { mutableStateOf("") }

    // For customer adding or editing
    if (showCustomerDialog) {
        CustomerDialog(
            onDismiss = { data, reason ->
                coroutineScope.launch {
                    // Check if we should update or add
                    when (reason) {
                        DialogDismissReason.CONTINUE -> {
                            // Add or update customer
                            viewModel.updateOrAddCustomer(data!!)

                            // Check for new tickets and add
                            tempTicketHolder.forEach { ticket ->
                                if (data.tickets.contains(ticket.identifier)) viewModel.updateOrAddTicket(ticket)
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
            onAddNewTicket = { customerId ->
                coroutineScope.launch {
                    // Set is for new customer so ticket dialog knows wht to do with it
                    isTicketForNewCustomer = true

                    // Seed data with customer id
                    ticketSeedData = TicketInfo(
                        customerId = customerId,
                        time = getTime(),
                        identifier = viewModel.getNextTicketId()
                    )

                    // Show dialog
                    showTicketDialog = true
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
                                viewModel.updateOrAddTicket(data!!)
                            }
                        }

                        DialogDismissReason.CANCEL -> {
                            // Do nothing
                        }
                    }

                    // Reset seed data
                    ticketSeedData = TicketInfo()

                    // Close dialog
                    showTicketDialog = false
                }
                        },
            isDarkMode = isDarkMode,
            seedData = ticketSeedData,
            customerInfo = customerData.value.map { it.info },
            employees = employeeList.value,
            scrollBarsOnRight = scrollBarsOnRight,
            onAddNewCustomer = { name ->
                coroutineScope.launch {
                    // Add seeded data if there is any
                    customerSeedData = CustomerData(info = CustomerInfo(name = name, time = getTime(), identifier = viewModel.getNextCustomerId()))

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
            onDismiss = { item, reason ->
                coroutineScope.launch {
                    when (reason) {
                        DialogDismissReason.CONTINUE -> {
                            viewModel.updateOrAddInventoryItem(item!!)
                        }

                        else -> {
                            // Do nothing
                        }
                    }

                    // Reset seed data
                    inventorySeedData = InventoryItem()

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
                                viewModel.removeCustomer(itemToDelete)

                                // Reset selected
                                viewModel.changeSelectedCustomerId(Int.MAX_VALUE)
                            }

                            MainItemType.TICKET -> {
                                // Remove ticket
                                viewModel.removeTicket(itemToDelete)

                                // Reset selected
                                viewModel.changeSelectedTicketId(Int.MAX_VALUE)
                            }
                            MainItemType.EMPLOYEE -> { /* We cant remove employees here */ }
                            MainItemType.INVENTORY -> {
                                // Remove item
                                viewModel.removeInventoryItem(itemToDelete)

                                // Reset selected
                                viewModel.changeSelectedInventoryId(Int.MAX_VALUE)
                            }
                            MainItemType.NONE -> { /* Do nothing */ }
                        }
                    }

                    // Reset values
                    itemToDelete = Int.MAX_VALUE
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

                    Spacer(Modifier.padding(8.dp))
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
                                    customerSeedData = CustomerData(
                                        info = CustomerInfo(
                                            name = name,
                                            time = getTime(),
                                            identifier = viewModel.getNextCustomerId()
                                        )
                                    )

                                    // Trigger dialog
                                    showCustomerDialog = true
                                }
                            },
                            onEditCustomer = {
                                coroutineScope.launch {
                                    // Set seed data
                                    customerSeedData = viewModel.getCustomerForId(selectedCustomer.value)

                                    // Trigger dialog if we actually have an item selected
                                    if (customerSeedData != CustomerData()) { showCustomerDialog = true }
                                }
                            },
                            onCustomerSelectionChange = { id -> viewModel.changeSelectedCustomerId(id)},
                            onDeleteCustomer = { id ->
                                coroutineScope.launch {
                                    // TODO: Maybe add name of customer to this as well?
                                    // TODO: Add ability to delete tickets for customer as well
                                    // Check if item exists
                                    if (customerData.value.firstOrNull { it.info.identifier == id } != null) {
                                        // Set values
                                        itemToDelete = id
                                        itemToDeleteType = MainItemType.CUSTOMER
                                        confirmDeleteDialogText = "Customer: $id"

                                        // Trigger the dialog
                                        showConfirmDeleteDialog = true
                                    }
                                }
                            },
                            onTicketClick = { id ->
                                coroutineScope.launch {
                                    // Set selected ticket
                                    viewModel.changeSelectedTicketId(id)

                                    // Get seed data
                                    ticketSeedData = viewModel.getTicketForId(id)

                                    // Pop up ticket dialog
                                    showTicketDialog = true
                                }
                            },
                            selectedCustomerId = selectedCustomer.value,
                            customerDataList = customerData.value,
                            ticketList = ticketList.value,
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
                            selectedTicketId = selectedTicket.value,
                            ticketList = ticketList.value,
                            customerList = customerData.value,
                            currentEmployee = currentEmployee.value,
                            employeeList = employeeList.value,
                            scrollBarsOnRight = scrollBarsOnRight,
                            onAddNewTicket = {
                                coroutineScope.launch {
                                    // Set up seed data
                                    ticketSeedData = TicketInfo(
                                        identifier = viewModel.getNextTicketId(),
                                        assignedEmployee = currentEmployee.value.identifier,
                                        time = getTime()
                                    )

                                    // Show the dialog
                                    showTicketDialog = true
                                }
                            },
                            onEditTicket = {
                                coroutineScope.launch {
                                    // Get seed data
                                    ticketSeedData = viewModel.getTicketForId(selectedTicket.value)

                                    // Trigger dialog if we have an actual item selected
                                    if (ticketSeedData != TicketInfo()) { showTicketDialog = true }
                                }
                            },
                            onSelectedTicketChange = { id -> viewModel.changeSelectedTicketId(id) },
                            onSelectedPanelChange = { selectedTicketPanel = it },
                            onDeleteTicket = { id ->
                                coroutineScope.launch {
                                    // Make sure item exists
                                    if (ticketList.value.firstOrNull { it.identifier == id } != null) {
                                        // Set values
                                        itemToDelete = id
                                        itemToDeleteType = MainItemType.TICKET
                                        confirmDeleteDialogText = "Ticket: $id"

                                        // Trigger the dialog
                                        showConfirmDeleteDialog = true
                                    }
                                }
                            },
                            selectedPanel = selectedTicketPanel,
                            ticketListState = ticketLazyListState,
                            coroutineScope = coroutineScope
                        )
                    }

                    // Inventory panel
                    2 -> {
                        InventoryPanel(
                            isDarkMode = isDarkMode,
                            inventoryList = inventoryList.value,
                            selectedInventoryItemId = selectedInventoryId.value,
                            onSelectedInventoryItemChange = { viewModel.changeSelectedInventoryId(it) },
                            onAddInventoryItem = {
                                coroutineScope.launch {
                                    // Set seed data
                                    inventorySeedData = InventoryItem(
                                        cost = "000",
                                        price = "000",
                                        identifier = viewModel.getNextInventoryId(),
                                        time = getTime()
                                    )

                                    // Popup dialog
                                    showInventoryDialog = true
                                }
                            },
                            onEditInventoryItem = { item ->
                                coroutineScope.launch {
                                    // Set seed data
                                    inventorySeedData = viewModel.getInventoryItemForId(item)

                                    // Popup dialog if we have an actual item selected
                                    if (inventorySeedData != InventoryItem()) { showInventoryDialog = true }
                                }
                            },
                            onDeleteInventoryItem = { id ->
                                coroutineScope.launch {
                                    // Get item so we can get the name
                                    val item = inventoryList.value.firstOrNull { it.identifier == id }

                                    // Check if its valid
                                    if (item != null) {
                                        // Set values
                                        itemToDelete = id
                                        itemToDeleteType = MainItemType.INVENTORY
                                        confirmDeleteDialogText = "${item.name}: $id"

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