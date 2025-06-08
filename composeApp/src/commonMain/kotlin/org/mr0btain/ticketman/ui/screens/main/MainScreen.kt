package org.mr0btain.ticketman.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
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
import org.mr0btain.ticketman.ui.screens.main.components.ticket.TicketPanel

/**
 * The main landing screen
 */

// TODO: Inventory dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
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
                // Check if we should update or add
                when (reason) {
                    DialogDismissReason.CONTINUE -> {
                        viewModel.updateOrAddCustomer(data!!)
                    }

                    DialogDismissReason.CANCEL -> {
                        // Do Nothing
                    }
                }

                // Reset starter data
                customerSeedData = CustomerData()

                // Hide dialog
                showCustomerDialog = false
            },
            onAddNewTicket = { customerId ->
                // Seed data with customer id
                ticketSeedData = TicketInfo(customerId = customerId, time = getTime(), identifier = viewModel.getNextTicketId())

                // Show dialog
                showTicketDialog = true
            },
            isDarkMode = isDarkMode,
            seedData = customerSeedData
        )
    }

    // For adding new ticket
    // TODO: Need to handle the event if were making a new customer and they add a ticket then cancel adding customer?
    // TODO: Need to make it so when adding new customer and we add a ticket we update customers tickets after ticket creation
    if (showTicketDialog) {
        TicketDialog(
            onDismiss = { data, reason ->
                // Check if it's an actual add else data will be null
                when (reason) {
                    DialogDismissReason.CONTINUE -> {
                        viewModel.updateOrAddTicket(data!!)
                    }

                    DialogDismissReason.CANCEL -> {
                        // Do nothing
                    }
                }

                // Reset seed data
                ticketSeedData = TicketInfo()

                // Close dialog
                showTicketDialog = false
                        },
            isDarkMode = isDarkMode,
            seedData = ticketSeedData,
            customerInfo = customerData.value.map { it.info },
            employees = employeeList.value,
            onAddNewCustomer = { name ->
                // Add seeded data if there is any
                customerSeedData = CustomerData(info = CustomerInfo(name = name, time = getTime(), identifier = viewModel.getNextCustomerId()))

                // Trigger dialog
                showCustomerDialog = true
            }
        )
    }

    // For confirming deleting an item
    if (showConfirmDeleteDialog) {
        SimpleConfirmDeleteDialog(
            itemName = confirmDeleteDialogText,
            onDismissRequest = { confirmed ->
                if (confirmed) {
                    // Delete item
                    when (itemToDeleteType) {
                        MainItemType.CUSTOMER -> { viewModel.removeCustomer(itemToDelete) }
                        MainItemType.TICKET -> { viewModel.removeTicket(itemToDelete) }
                        MainItemType.EMPLOYEE -> { /* We cant remove employees here */ }
                        MainItemType.INVENTORY -> { viewModel.removeInventoryItem(itemToDelete) }
                        MainItemType.NONE -> { /* Do nothing */ }
                    }
                }

                // Reset values
                itemToDelete = Int.MAX_VALUE
                itemToDeleteType = MainItemType.NONE
                confirmDeleteDialogText = ""

                // Close the dialog
                showConfirmDeleteDialog = false
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
                                // Add starter data
                                customerSeedData = CustomerData(info = CustomerInfo(name = name, time = getTime(), identifier = viewModel.getNextCustomerId()))

                                // Trigger dialog
                                showCustomerDialog = true
                            },
                            onEditCustomer = {
                                // Set seed data
                                customerSeedData = viewModel.getCustomerForId(selectedCustomer.value)

                                // Trigger dialog
                                showCustomerDialog = true
                            },
                            onCustomerSelectionChange = { id ->  viewModel.changeSelectedCustomerId(id) },
                            onDeleteCustomer = { id ->
                                // TODO: Maybe add name of customer to this as well?
                                // TODO: Add ability to delete tickets for customer as well
                                // Set values
                                itemToDelete = id
                                itemToDeleteType = MainItemType.CUSTOMER
                                confirmDeleteDialogText = "Customer: $id"

                                // Trigger the dialog
                                showConfirmDeleteDialog = true
                            },
                            onTicketClick = { id ->
                                // Set selected ticket
                                viewModel.changeSelectedTicketId(id)

                                // Get seed data
                                ticketSeedData = viewModel.getTicketForId(selectedTicket.value)

                                // Pop up ticket dialog
                                showTicketDialog = true
                            },
                            selectedCustomerId = selectedCustomer.value,
                            customerDataList = customerData.value,
                            ticketList = ticketList.value,
                            customerLazyListState = customerLazyListState,
                            coroutineScope = coroutineScope
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
                            onAddNewTicket = {
                                // Set up seed data
                                ticketSeedData = TicketInfo(
                                    identifier = viewModel.getNextTicketId(),
                                    assignedEmployee = currentEmployee.value.identifier,
                                    time = getTime()
                                )

                                // Show the dialog
                                showTicketDialog = true
                            },
                            onEditTicket = {
                                // Get seed data
                                ticketSeedData = viewModel.getTicketForId(selectedTicket.value)

                                // Trigger dialog
                                showTicketDialog = true
                            },
                            onSelectedTicketChange = { id -> viewModel.changeSelectedTicketId(id) },
                            onSelectedPanelChange = { selectedTicketPanel = it },
                            onDeleteTicket = { id ->
                                // Set values
                                itemToDelete = id
                                itemToDeleteType = MainItemType.TICKET
                                confirmDeleteDialogText = "Ticket: $id"

                                // Trigger the dialog
                                showConfirmDeleteDialog = true
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
                            onAddInventoryItem = {  },
                            onEditInventoryItem = {  },
                            onDeleteInventoryItem = { id ->
                                // Get item so we can get the name
                                val item = inventoryList.value.first { it.identifier == id }
                                // Set values
                                itemToDelete = id
                                itemToDeleteType = MainItemType.INVENTORY
                                confirmDeleteDialogText = "${item.name}: $id"

                                // Trigger the dialog
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
}