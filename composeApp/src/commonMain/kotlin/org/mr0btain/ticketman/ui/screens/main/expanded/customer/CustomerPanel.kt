package org.mr0btain.ticketman.ui.screens.main.expanded.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.composables.shared.PanelTopBar
import java.util.*

@Composable
fun CustomerPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    customerList: List<Customer>,
    seed: CustomerSeed,
    onAddCustomer: () -> Unit,
    onEditCustomer: () -> Unit,
    onCustomerSelectionChange: (UUID) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onDeleteCustomer: () -> Unit,
    onTicketClick: (UUID) -> Unit,
    onImageClick: (UUID) -> Unit,
    customerLazyListState: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectUpdateFromClick by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            PanelTopBar(
                label = "Customer",
                onSearchClick = { onCustomerSelectionChange(it) },
                onSearchQuery = onSearchQuery,
                onAddClick = onAddCustomer,
                onDeleteClick = onDeleteCustomer
            )

            Spacer(Modifier.padding(4.dp))

            Row(
                modifier = Modifier.sizeIn(maxHeight = 500.dp)
            ) {
                CustomerListPane(
                    modifier = Modifier,
                    isDarkMode = isDarkMode,
                    customerList = customerList,
                    lazyListState = customerLazyListState,
                    selectedId = seed.customer.identifier,
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
                    selectedCustomer = seed,
                    onEditClick = { onEditCustomer() },
                    onTicketClick = onTicketClick,
                    onImageClick = onImageClick
                )
            }
        }
    }

    // On list update or search scroll to selected item
    LaunchedEffect(seed) {
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