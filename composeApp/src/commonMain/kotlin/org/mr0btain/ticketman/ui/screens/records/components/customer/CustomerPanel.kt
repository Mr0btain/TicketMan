package org.mr0btain.ticketman.ui.screens.records.components.customer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.ui.kit.navigation.TmPanelTopBar
import org.mr0btain.ticketman.ui.screens.records.components.RecordsLayout
import kotlin.uuid.Uuid

/** Customers: search and add on top, the list, and the picked customer's details */
@Composable
fun CustomerPanel(
    customerList: List<Customer>,
    seed: CustomerSeed,
    pendingId: Uuid? = null,
    onAddCustomer: () -> Unit,
    onEditCustomer: () -> Unit,
    canEdit: Boolean = true,
    onCustomerSelectionChange: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<TmSearchResult>,
    onSearchPick: (Uuid) -> Unit = onCustomerSelectionChange,
    onDeleteCustomer: () -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onImageClick: (noteIdentifier: Uuid, page: Int) -> Unit,
    customerLazyListState: LazyListState = rememberLazyListState(),
    loadError: String? = null
) {
    RecordsLayout(
        toolbar = {
            TmPanelTopBar(
                label = "Customer",
                onSearchClick = onSearchPick,
                onSearchQuery = onSearchQuery,
                onAddClick = onAddCustomer,
                onDeleteClick = onDeleteCustomer,
                canDelete = !seed.isNew,
                canChange = canEdit
            )
        },
        list = {
            CustomerListPane(
                customerList = customerList,
                selectedId = pendingId ?: seed.identifier,
                lazyListState = customerLazyListState,
                onClick = onCustomerSelectionChange,
                loadError = loadError
            )
        },
        detail = {
            CustomerInfoPane(
                selectedCustomer = seed,
                onEditClick = onEditCustomer,
                canEdit = canEdit,
                onTicketClick = onTicketClick,
                onImageClick = onImageClick
            )
        }
    )
}
