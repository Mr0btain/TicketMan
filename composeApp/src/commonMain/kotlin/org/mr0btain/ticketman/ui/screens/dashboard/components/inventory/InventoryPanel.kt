package org.mr0btain.ticketman.ui.screens.dashboard.components.inventory

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.screens.shared.PanelTopBar
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordsLayout
import kotlin.uuid.Uuid

// TODO: Setup setting reorder on stock hitting a certain amount. Like a popup on login or when it happens?

/** Inventory: search and add on top, the list, and the picked item's details */
@Composable
fun InventoryPanel(
    inventoryList: List<Inventory>,
    selectedInventory: InventorySeed,
    pendingId: Uuid? = null,
    onSelectedInventoryChange: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onAddInventory: () -> Unit,
    onEditInventory: () -> Unit,
    onDeleteInventory: () -> Unit,
    onImageClick: () -> Unit,
    inventoryListState: LazyListState
) {
    RecordsLayout(
        toolbar = {
            PanelTopBar(
                label = "Item",
                onSearchClick = onSelectedInventoryChange,
                onSearchQuery = onSearchQuery,
                onAddClick = onAddInventory,
                onDeleteClick = onDeleteInventory,
                canDelete = !selectedInventory.isNew
            )
        },
        list = {
            InventoryListPane(
                inventoryList = inventoryList,
                selected = pendingId ?: selectedInventory.identifier,
                onClick = onSelectedInventoryChange,
                state = inventoryListState
            )
        },
        detail = {
            InventoryInfoPane(inventoryData = selectedInventory, onEditClick = onEditInventory, onImageClick = onImageClick)
        }
    )
}
