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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.DialogDismissReason
import org.mr0btain.ticketman.components.data.components.MainItemType
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.components.getTime
import org.mr0btain.ticketman.ui.dialog.SimpleConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.main.components.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.components.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.components.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.components.TicketPanel

/**
 * The main landing screen
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    scrollBarTop: Boolean,
    scrollBarRight: Boolean,
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()

    val customerData = viewModel.customerDataList.collectAsState()
    val ticketInfoList = viewModel.ticketDataList.collectAsState()
    val employeeList = viewModel.employeeList.collectAsState()
    val currentEmployee = viewModel.employee.collectAsState()

    // This is needed for dialog
    val selectedCustomer = viewModel.selectedCustomerId.collectAsState()
    val selectedTicket = viewModel.selectedTicketId.collectAsState()

    // For customer dialog
    var customerSeedData by remember { mutableStateOf(CustomerData()) }
    var showCustomerDialog by remember { mutableStateOf(false) }

    // For ticket dialog
    var showTicketDialog by remember { mutableStateOf(false) }
    var ticketSeedData by remember { mutableStateOf(TicketInfo()) }

    // TODO: Maybe elevate these states to viewmodel so its saved between screen changes? will have to see on mobile when screen rotates
    // For ticket panel state
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by remember { mutableIntStateOf(0) }

    // For confirm delete dialog
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var itemToDeleteType by remember { mutableStateOf(MainItemType.NONE) }
    var confirmDeleteDialogText by remember { mutableStateOf("") }

    // For customer panel state
    val customerLazyListState = rememberLazyListState()

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
                        modifier = Modifier.width(260.dp)
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
                        }
                    }

                    Spacer(Modifier.padding(8.dp))
                }

                HorizontalDivider(
                    modifier = Modifier,
                    thickness = DividerDefaults.Thickness,
                    color = if (isDarkMode) MaterialTheme.colorScheme.surface else DividerDefaults.color
                )

                if (tabIndex.value == 0) {
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
                            customerSeedData = viewModel.getCustomerData(selectedCustomer.value)

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
                        ticketList = ticketInfoList.value,
                        customerLazyListState = customerLazyListState,
                        coroutineScope = coroutineScope
                    )
                } else {
                    TicketPanel(
                        isDarkMode = isDarkMode,
                        selectedTicketId = selectedTicket.value,
                        ticketList = ticketInfoList.value,
                        customerList = customerData.value,
                        currentEmployee = currentEmployee.value,
                        employeeList = employeeList.value,
                        scrollOnRight = scrollBarRight,
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
            }
        }
    }
}