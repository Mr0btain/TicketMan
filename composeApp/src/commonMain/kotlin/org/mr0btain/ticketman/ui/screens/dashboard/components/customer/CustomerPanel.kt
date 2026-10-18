package org.mr0btain.ticketman.ui.screens.dashboard.components.customer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.screens.shared.PanelTopBar
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordsLayout
import kotlin.uuid.Uuid

/** Customers: search and add on top, the list, and the picked customer's details */
@Composable
fun CustomerPanel(
    customerList: List<Customer>,
    seed: CustomerSeed,
    pendingId: Uuid? = null,
    onAddCustomer: () -> Unit,
    onEditCustomer: () -> Unit,
    onCustomerSelectionChange: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onDeleteCustomer: () -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onImageClick: (Uuid) -> Unit,
    customerLazyListState: LazyListState = rememberLazyListState()
) {
    RecordsLayout(
        toolbar = {
            PanelTopBar(
                label = "Customer",
                onSearchClick = onCustomerSelectionChange,
                onSearchQuery = onSearchQuery,
                onAddClick = onAddCustomer,
                onDeleteClick = onDeleteCustomer,
                canDelete = !seed.isNew
            )
        },
        list = {
            CustomerListPane(
                customerList = customerList,
                selectedId = pendingId ?: seed.identifier,
                lazyListState = customerLazyListState,
                onClick = onCustomerSelectionChange
            )
        },
        detail = {
            CustomerInfoPane(
                selectedCustomer = seed,
                onEditClick = onEditCustomer,
                onTicketClick = onTicketClick,
                onImageClick = onImageClick
            )
        }
    )
}
