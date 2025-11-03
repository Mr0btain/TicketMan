package org.mr0btain.ticketman.ui.screens.main.mobile.customer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.shared.PersonName

@Composable
fun CustomerPanelMobile(
    modifier: Modifier = Modifier,
    scrollBarsOnRight: Boolean,
    selectedCustomerId: String,
    customerList: List<Customer>,
    ticketList: List<Ticket>,
    onAddCustomer: (PersonName) -> Unit,
    onEditCustomer: () -> Unit,
    onCustomerSelectionChange: (String) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onTicketClick: (String) -> Unit,
    customerLazyListState: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    isDarkMode: Boolean
) {
    /*
    // Hopefully this saves on calculations and updates properly
    val sortedCustomerInfo = remember(customerList) { customerList.map { it }.sortedBy { it.name.firstName + it.name.lastName } }

    // For search input
    var searchInput by remember { mutableStateOf("") }

    // Search list
    val searchItems = remember(customerList) { customerList.map { it } }

    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectUpdateFromClick by remember { mutableStateOf(false) }

    // -------------------------------- Info Pane --------------------------------
    CustomerInfoPaneMobile(
        isDarkMode = isDarkMode,
        selectedCustomer = customerList.singleOrNull { it.identifier == selectedCustomerId }
            ?: Customer(),
        onEditClick = { onEditCustomer() },
        tickets = ticketList.filter { it.customerId == selectedCustomerId },
        onTicketClick = onTicketClick,
        scrollBarsOnRight = scrollBarsOnRight
    )

    // -------------------------------- List Pane --------------------------------
    CustomerListPaneMobile(
        modifier = Modifier,
        customerInfo = sortedCustomerInfo,
        lazyListState = customerLazyListState,
        selectedCustomerId = selectedCustomerId,
        scope = coroutineScope,
        scrollBarsOnRight = scrollBarsOnRight,
        isDarkMode = isDarkMode,
        onClick = { id ->
            // Set the isupdatefromclick so we don't jar the scroll in the launched effect
            isSelectUpdateFromClick = true

            // Call selection change
            onCustomerSelectionChange(id)
        }
    )
     */

}