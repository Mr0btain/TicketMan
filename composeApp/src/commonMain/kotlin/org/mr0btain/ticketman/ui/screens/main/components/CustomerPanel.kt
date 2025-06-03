package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.PersonName
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.ui.composables.CustomerSearch
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

// TODO: Should really offload some of the calculations on the lists to before we call this screen

@Composable
fun CustomerPanel(
    modifier: Modifier = Modifier,
    scrollBarTop: Boolean = false,
    scrollBarRight: Boolean = true,
    selectedCustomerId: String,
    customerDataList: List<CustomerData>,
    ticketList: List<TicketInfo>,
    onAddCustomer: (PersonName) -> Unit,
    onEditCustomer: () -> Unit,
    onCustomerSelectionChange: (String) -> Unit,
    customerLazyListState: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    focusManager: FocusManager = LocalFocusManager.current,
    isDarkMode: Boolean
) {
    // Hopefully this saves on calculations and updates properly
    val sortedCustomerInfo = remember(customerDataList) { customerDataList.map { it.info }.sortedBy { it.name.firstName + it.name.lastName } }

    // For search input
    var searchInput by remember { mutableStateOf("") }

    // Search list
    val searchItems = remember(customerDataList) { customerDataList.map { it.info } }

    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectUpdateFromClick by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search box
                CustomerSearch(
                    modifier = Modifier.padding(start = 8.dp),
                    items = searchItems,
                    input = searchInput,
                    onClick = {
                        // Set search input
                        searchInput = it

                        // Call selection change
                        onCustomerSelectionChange(it)
                              },
                    onInputChange = { searchInput = it },
                    focusManager = focusManager
                )

                // Button for adding a customer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()

                            onAddCustomer(PersonName())
                                  },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Add Customer")
                    }

                    Spacer(Modifier.padding(8.dp))
                }
            }

            Spacer(Modifier.padding(4.dp))

            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                CustomerListPane(
                    modifier = Modifier
                        .height(500.dp),
                    customerInfo = sortedCustomerInfo,
                    lazyListState = customerLazyListState,
                    selectedCustomerId = selectedCustomerId,
                    scope = coroutineScope,
                    scrollBarRight = scrollBarRight,
                    isDarkMode = isDarkMode,
                    onClick = { id ->
                        // Set the isupdatefromclick so we don't jar the scroll in the launched effect
                        isSelectUpdateFromClick = true

                        // Call selection change
                        onCustomerSelectionChange(id)

                        // Clear request focus to clear search bar if its open
                        focusManager.clearFocus()
                    }
                )

                CustomerInfoPane(
                    modifier = modifier
                        .height(500.dp),
                    isDarkMode = isDarkMode,
                    selectedCustomerData = customerDataList.singleOrNull() { it.info.identifier == selectedCustomerId } ?: CustomerData(),
                    onEditClick = {
                        focusManager.clearFocus()

                        onEditCustomer()
                    },
                    tickets = ticketList.filter { it.customerId == selectedCustomerId }
                )
            }
        }
    }

    // On list update or search scroll to selected item
    LaunchedEffect(selectedCustomerId) {
        if (!isSelectUpdateFromClick) {
            // Get index of visible items
            val visibleItemIndex = customerLazyListState.layoutInfo.visibleItemsInfo.map { it.index }

            if (visibleItemIndex.isNotEmpty() && sortedCustomerInfo.map { it.identifier }.contains(selectedCustomerId)) {
                val index = sortedCustomerInfo.indexOf(sortedCustomerInfo.fastFirst { it.identifier == selectedCustomerId })

                if (index < visibleItemIndex.first() || index > visibleItemIndex.last() && index >= 0) {
                    customerLazyListState.animateScrollToItem(
                        index = index,
                        scrollOffset = -(customerLazyListState.layoutInfo.viewportSize.height / 2) + 30
                    )
                }
            }

        } else isSelectUpdateFromClick = false
    }
}