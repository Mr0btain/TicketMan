package org.mr0btain.ticketman.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.ui.screens.main.components.CustomerDialog
import org.mr0btain.ticketman.ui.screens.main.components.TicketDialog
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.components.CustomerPanel
import org.mr0btain.ticketman.ui.screens.main.components.TicketPanel

/**
 * The main landing screen after logon maybe?
 */

// TODO: Need to add error handling
// TODO: Add snack bar notifications on errors or cancels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    focusManager: FocusManager = LocalFocusManager.current,
    scrollBarTop: Boolean,
    scrollBarRight: Boolean,
    isDarkMode: Boolean
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
    var customerSeedData by rememberSaveable { mutableStateOf(CustomerData()) }
    var showCustomerDialog by rememberSaveable { mutableStateOf(false) }
    var isNewCustomer by remember { mutableStateOf(false) }

    // For ticket dialog
    var showTicketDialog by rememberSaveable { mutableStateOf(false) }
    var ticketSeedData by rememberSaveable { mutableStateOf(TicketInfo()) }

    // TODO: Maybe elevate these states to viewmodel so its saved between screen changes?
    // For ticket panel state
    val ticketLazyListState = rememberLazyListState()
    var selectedTicketPanel by rememberSaveable { mutableIntStateOf(0) }

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
                ticketSeedData = TicketInfo(customerId = customerId)

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
                customerSeedData = CustomerData(info = CustomerInfo(name = name))

                // Trigger dialog
                showCustomerDialog = true
            },
            focusManager = focusManager
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
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
                            selectedTabIndex = tabIndex.value.toInt(),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .wrapContentWidth(),
                            containerColor = Color.Transparent
                        ) {
                            // Customer Tab
                            Tab(
                                selected = tabIndex.value == "0",
                                onClick = { viewModel.changeMainTabIndex("0") },
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
                                selected = tabIndex.value == "1",
                                onClick = { viewModel.changeMainTabIndex("1") },
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

                if (tabIndex.value == "0") {
                    CustomerPanel(
                        isDarkMode = isDarkMode,
                        onAddCustomer = { name ->
                            // Add starter data
                            customerSeedData = CustomerData(info = CustomerInfo(name = name))

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
                        selectedCustomerId = selectedCustomer.value,
                        customerDataList = customerData.value,
                        ticketList = ticketInfoList.value,
                        customerLazyListState = customerLazyListState,
                        focusManager = focusManager,
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
                                assignedEmployee = currentEmployee.value.identifier
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
                        selectedPanel = selectedTicketPanel,
                        ticketListState = ticketLazyListState,
                        focusManager = focusManager
                    )
                }
            }
        }
    }
}