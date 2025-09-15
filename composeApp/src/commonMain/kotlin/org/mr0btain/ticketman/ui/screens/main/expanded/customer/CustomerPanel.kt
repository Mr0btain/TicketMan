@file:OptIn(ExperimentalUuidApi::class)

package org.mr0btain.ticketman.ui.screens.main.expanded.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.shared.toStringName
import org.mr0btain.ticketman.ui.composables.AddOrDeleteButtonRow
import org.mr0btain.ticketman.ui.composables.search.CustomerSearch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun CustomerPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    pagingItems: LazyPagingItems<Customer>,
    selectedCustomer: CustomerData?,
    onAddCustomer: () -> Unit,
    onEditCustomer: () -> Unit,
    onCustomerSelectionChange: (Uuid) -> Unit,
    onDeleteCustomer: () -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onImageClick: (List<ImageData>) -> Unit,
    customerLazyListState: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    // Hopefully this saves on calculations and updates properly
    //val sortedCustomerInfo = remember(customerList) { customerList.map { it }.sortedBy { it.name.firstName + it.name.lastName } }

    // For search input
    var searchInput by remember { mutableStateOf("") }

    // Search list
    val searchItems = emptyList<Customer>() //remember(customerList) { customerList.map { it } }

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
                        // Call selection change
                        onCustomerSelectionChange(it)

                        // Set search input
                        selectedCustomer?.customer?.let { customer ->
                            searchInput = customer.name.toStringName()
                        }
                              },
                    onInputChange = { searchInput = it }
                )

                // Button for adding or deleting a customer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddOrDeleteButtonRow(
                        label = "Customer",
                        onAddClick = {
                            // Trigger new item dialog
                            onAddCustomer()
                        },
                        onDeleteClick = onDeleteCustomer
                    )

                    Spacer(Modifier.padding(8.dp))
                }
            }

            Spacer(Modifier.padding(4.dp))

            Row(
                modifier = Modifier.sizeIn(maxHeight = 500.dp)
            ) {
                CustomerListPane(
                    modifier = Modifier,
                    isDarkMode = isDarkMode,
                    customerInfo = pagingItems,
                    lazyListState = customerLazyListState,
                    selectedCustomerId = selectedCustomer?.customer?.identifier,
                    scope = coroutineScope,
                    onClick = { id ->
                        // Set the isupdatefromclick so we don't jar the scroll in the launched effect
                        isSelectUpdateFromClick = true

                        // Call selection change
                        onCustomerSelectionChange(id)
                    }
                )

                CustomerInfoPane(
                    modifier = Modifier,
                    isDarkMode = isDarkMode,
                    selectedCustomer = selectedCustomer,
                    onEditClick = { onEditCustomer() },
                    onTicketClick = onTicketClick,
                    onImageClick = onImageClick
                )
            }
        }
    }

    // On list update or search scroll to selected item
    LaunchedEffect(selectedCustomer) {
        /*
        if (!isSelectUpdateFromClick) {
            // Get index of visible items
            val visibleItemIndex = customerLazyListState.layoutInfo.visibleItemsInfo.map { it.index }

            if (visibleItemIndex.isNotEmpty() && sortedCustomerInfo.map { it.identifier }.contains(selectedCustomer.customer?.identifier)) {
                val index = sortedCustomerInfo.indexOf(sortedCustomerInfo.fastFirst { it.identifier == selectedCustomer })

                if (index < visibleItemIndex.first() || index > visibleItemIndex.last() && index >= 0) {
                    customerLazyListState.animateScrollToItem(
                        index = index,
                        scrollOffset = -(customerLazyListState.layoutInfo.viewportSize.height / 2) + 30
                    )
                }
            }

        } else isSelectUpdateFromClick = false
         */
    }
}