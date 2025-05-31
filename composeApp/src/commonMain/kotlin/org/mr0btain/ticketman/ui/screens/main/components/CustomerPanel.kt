package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.CustomerName
import org.mr0btain.ticketman.ui.composables.CustomerSearchBox
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

@Composable
fun CustomerPanel(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scrollBarTop: Boolean = false,
    scrollBarRight: Boolean = true,
    isDarkMode: Boolean
) {
    // Customer data
    val selectedCustomerData = viewModel.selectedCustomerData.collectAsState()
    val customerDataList = viewModel.customerDataList.collectAsState()
    //val selectedCustomerIndex = viewModel.selectedCustomerDataIndex.collectAsState()

    // Starter data to seed new customer if needed
    val starterData = remember { mutableStateOf(CustomerData()) }

    // Hopefully this saves on calculations and updates properly
    val sortedCustomerInfo = remember(customerDataList.value) { customerDataList.value.map { it.info }.sortedBy { it.name.firstName + it.name.lastName } }

    // Coroutine scope
    val coroutineScope = rememberCoroutineScope()

    // States for showing things
    var showSearchBar by remember { mutableStateOf(false) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    // Lazy list state for scrolling to item when we add new customer
    val customerDataListState = rememberLazyListState()

    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectUpdateFromClick by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // On list update or search scroll to selected item
    LaunchedEffect(selectedCustomerData.value) {
        if (!isSelectUpdateFromClick) {
            val index = sortedCustomerInfo.indexOf(selectedCustomerData.value.info)

            // Sanity check
            if (index >= 0 && index <= customerDataList.value.size) {
                customerDataListState.scrollToItem(index)
            }
        } else isSelectUpdateFromClick = false
    }

    // For editing customer data
    if (showEditCustomerDialog) {
        EditCustomerDataDialog(
            customerData = selectedCustomerData.value,
            viewModel = viewModel,
            onDismiss = { showEditCustomerDialog = false },
            isDarkMode = isDarkMode
        )
    }

    // For adding new customer
    if (showAddCustomerDialog) {
        AddCustomerDataDialog(
            viewModel = viewModel,
            onDismiss = {
                // Reset starter data
                starterData.value = CustomerData()

                // Hide dialog
                showAddCustomerDialog = false
                        },
            isDarkMode = isDarkMode,
            starterData = starterData.value
        )
    }

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(8.dp))

                // Button to drop down Search bar
                ElevatedButton(
                    onClick = { showSearchBar = !showSearchBar },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Search Bar",
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    Icon(
                        modifier = Modifier.size(22.dp),
                        painter = painterResource(
                            if (showSearchBar) Res.drawable.chevron_up else Res.drawable.chevron_down
                        ),
                        contentDescription = "Show search bar"
                    )
                }

                // Button for adding a customer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()

                            showAddCustomerDialog = true
                                  },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Add Customer")
                    }

                    Spacer(Modifier.padding(8.dp))
                }
            }

            // Search bar
            AnimatedVisibility(visible = showSearchBar) {
                CustomerSearchBox(
                    modifier = Modifier.sizeIn(
                        maxWidth = 500.dp
                    ),
                    items = viewModel.getAllCustomerInfo(),
                    onResult = { item, isNew ->
                        if (isNew) {
                            val newName = item.split(" ")

                            starterData.value = CustomerData(
                                info = CustomerInfo(
                                    name = CustomerName(
                                        firstName = newName.getOrElse(0) { "" },
                                        lastName = newName.getOrElse(1) { "" }
                                    )
                                )
                            )

                            // Popup new customer dialog
                            showAddCustomerDialog = true
                        } else {
                            // Update selected customer
                            viewModel.changeSelectedCustomerData(
                                customerDataList.value.firstOrNull { it.info.id == item } ?: CustomerData()
                            )
                        }

                        focusManager.clearFocus()
                    }
                )
            }

            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                CustomerListPane(
                    modifier = Modifier
                        .height(500.dp),
                    customerInfo = sortedCustomerInfo,
                    lazyListState = customerDataListState,
                    selectedId = selectedCustomerData.value.info.id,
                    scope = coroutineScope,
                    scrollBarRight = scrollBarRight,
                    isDarkMode = isDarkMode,
                    onClick = { id ->
                        // Set the isupdatefromclick so we don't jar the scroll in the launched effect
                        isSelectUpdateFromClick = true

                        // Update selected customer
                        viewModel.changeSelectedCustomerData(viewModel.getCustomerData(id))

                        // Clear request focus to clear search bar if its open
                        focusManager.clearFocus()
                    }
                )

                CustomerInfoPane(
                    modifier = modifier
                        .height(500.dp),
                    isDarkMode = isDarkMode,
                    selectedCustomerData = selectedCustomerData.value,
                    onEditClick = {
                        focusManager.clearFocus()
                        showEditCustomerDialog = true
                    },
                    tickets = viewModel.getTicketsForCustomer(selectedCustomerData.value.info.id)
                )
            }
        }
    }
}