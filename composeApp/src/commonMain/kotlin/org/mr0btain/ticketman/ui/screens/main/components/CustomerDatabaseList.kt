package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.composables.SearchTextField
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

// TODO: Add ability to click on column and have it sorted by that value

@Composable
fun CustomerDatabaseList(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scrollBarTop: Boolean = false,
    scrollBarRight: Boolean = true,
    isDarkMode: Boolean
) {
    // Customer data
    val selectedCustomerData = viewModel.selectedCustomerData.collectAsState()
    val customerDataList = viewModel.customerDataList.collectAsState()
    val selectedCustomerIndex = viewModel.selectedCustomerDataIndex.collectAsState()

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
    var isIndexUpdateFromClick by remember { mutableStateOf(false) }

    // On list update scroll to selected item
    // This will only really happen if we add a new customer
    LaunchedEffect(selectedCustomerIndex.value) {
        if (!isIndexUpdateFromClick) {
            val index = sortedCustomerInfo.indexOf(selectedCustomerData.value.info)
            // Sanity check
            if (selectedCustomerIndex.value >= 0 && selectedCustomerIndex.value != Int.MAX_VALUE && index >= 0) {
                customerDataListState.scrollToItem(index)
            }
        } else isIndexUpdateFromClick = false
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
            onDismiss = { showAddCustomerDialog = false },
            isDarkMode = isDarkMode
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
                        onClick = { showAddCustomerDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Add Customer")
                    }

                    Spacer(Modifier.padding(8.dp))
                }
            }

            // Search bar
            AnimatedVisibility(visible = showSearchBar) {
                Row(
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchTextField(
                        modifier = Modifier.padding(bottom = 4.dp),
                        label = "Search",
                        placeholder = "",
                        onValueChange = {}
                    )
                }
            }

            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                CustomerDataGrid(
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
                        isIndexUpdateFromClick = true

                        // Update selected customer
                        viewModel.changeSelectedCustomerData(viewModel.getCustomerData(id))

                        // Update selected index
                        viewModel.changeSelectedCustomerIndex(
                            sortedCustomerInfo.indexOf(selectedCustomerData.value.info)
                        )
                    }
                )

                CustomerInfoItem(
                    modifier = modifier
                        .height(500.dp),
                    isDarkMode = isDarkMode,
                    selectedCustomerData = selectedCustomerData.value,
                    onEditClick = {
                        showEditCustomerDialog = true
                    },
                    tickets = viewModel.getTicketsForCustomer(selectedCustomerData.value.info.id)
                )
            }
        }
    }
}