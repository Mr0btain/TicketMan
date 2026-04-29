package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.composables.shared.PanelTopBar
import java.util.*

// TODO: Setup setting reorder on stock hitting a certain amount. Like a popup on login or when it happens?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryPanel(
    modifier: Modifier = Modifier,
    inventoryList: List<Inventory>,
    selectedInventory: InventorySeed,
    onSelectedInventoryChange: (UUID) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onAddInventory: () -> Unit,
    onEditInventory: () -> Unit,
    onDeleteInventory: () -> Unit,
    coroutineScope: CoroutineScope,
    inventoryListState: LazyListState
) {
    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectedUpdateFromClick by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            // --------------------------------- Header ---------------------------------------------------
            PanelTopBar(
                label = "Inventory",
                onSearchClick = { onSelectedInventoryChange(it) },
                onSearchQuery = onSearchQuery,
                onAddClick = onAddInventory,
                onDeleteClick = onDeleteInventory
            )

            Spacer(Modifier.padding(4.dp))

            // --------------------------------- List and Info Panes ---------------------------------------------------
            Row(
                modifier = Modifier.sizeIn(maxHeight = 500.dp)
            ) {
                // List of all the inventory items
                InventoryListPane(
                    selected = selectedInventory.inventory.identifier,
                    inventoryList = inventoryList,
                    onClick = {
                        // Set is update from click
                        isSelectedUpdateFromClick = true

                        // change selected item
                        onSelectedInventoryChange(it)
                    },
                    state = inventoryListState,
                    coroutineScope = coroutineScope
                )

                InventoryInfoPane(
                    inventoryData = selectedInventory,
                    onEditClick = { onEditInventory() }
                )
            }
        }
    }

    // --------------------------------- Launched effect for selected ticket ---------------------------------------------------

    //TODO: FIX
    LaunchedEffect(selectedInventory) {
        /*
        if (!isSelectedUpdateFromClick) {
            // Get index of our item
            val index = inventoryList.indexOf(selectedInventory.inventory)

            // Get index of visible items
            val visibleItemIndex = inventoryListState.layoutInfo.visibleItemsInfo.map { it.index }

            // Check if item is in viewport, else scroll to it
            if (visibleItemIndex.isNotEmpty() && index >= 0) {
                if (index < visibleItemIndex.first() || index > visibleItemIndex.last()) {
                    inventoryListState.animateScrollToItem(
                        index = index,
                        scrollOffset = -(inventoryListState.layoutInfo.viewportSize.height / 2) + 30
                    )
                }
            }
        } else isSelectedUpdateFromClick = false
         */
    }
}