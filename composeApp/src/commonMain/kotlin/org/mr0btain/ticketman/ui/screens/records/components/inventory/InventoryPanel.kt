package org.mr0btain.ticketman.ui.screens.records.components.inventory

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.kit.navigation.TmPanelTopBar
import org.mr0btain.ticketman.ui.screens.records.components.RecordsLayout
import kotlin.uuid.Uuid


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
    onMoveStock: (() -> Unit)? = null,
    canEdit: Boolean = true,
    onDeleteInventory: () -> Unit,
    onImageClick: (page: Int) -> Unit,
    onNoteImageClick: (noteIdentifier: Uuid, page: Int) -> Unit,
    inventoryListState: LazyListState,
    loadError: String? = null,
    printAction: @Composable () -> Unit = {}
) {
    RecordsLayout(
        toolbar = {
            TmPanelTopBar(
                label = "Item",
                onSearchClick = onSelectedInventoryChange,
                onSearchQuery = onSearchQuery,
                onAddClick = onAddInventory,
                onDeleteClick = onDeleteInventory,
                canDelete = !selectedInventory.isNew,
                canChange = canEdit
            )
        },
        list = {
            InventoryListPane(
                inventoryList = inventoryList,
                selected = pendingId ?: selectedInventory.identifier,
                onClick = onSelectedInventoryChange,
                state = inventoryListState,
                loadError = loadError
            )
        },
        detail = {
            InventoryInfoPane(
                inventoryData = selectedInventory,
                onEditClick = onEditInventory,
                onMoveStock = onMoveStock,
                canEdit = canEdit,
                onImageClick = onImageClick,
                onNoteImageClick = onNoteImageClick,
                printAction = printAction
            )
        }
    )
}
