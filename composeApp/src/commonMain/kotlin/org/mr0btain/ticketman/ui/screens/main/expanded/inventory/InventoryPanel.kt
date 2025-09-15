package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.inventory.InventoryData
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.ui.composables.AddOrDeleteButtonRow
import org.mr0btain.ticketman.ui.composables.search.InventorySearch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Setup setting reorder on stock hitting a certain amount. Like a popup on login or when it happens?

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun InventoryPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    inventoryList: LazyPagingItems<Inventory>,
    selectedInventory: InventoryData?,
    onSelectedInventoryChange: (Uuid) -> Unit,
    onAddInventory: () -> Unit,
    onEditInventory: () -> Unit,
    onDeleteInventory: () -> Unit,
    coroutineScope: CoroutineScope,
    inventoryListState: LazyListState
) {
    // For search input
    var searchInput by remember { mutableStateOf("") }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search box
                // TODO: Fix this
                InventorySearch(
                    modifier = Modifier.padding(start = 8.dp),
                    items = emptyList(),
                    input = searchInput,
                    onInputChange = { searchInput = it },
                    onClick = {
                        // Set search input in out text box
                        searchInput = "${it.name}: ${it.identifier}"

                        // Call selected item change
                        onSelectedInventoryChange(it.identifier)
                    }
                )

                Spacer(Modifier.padding(8.dp))

                // Buttons for adding a removing items
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddOrDeleteButtonRow(
                        onAddClick = {
                            // Trigger new item dialog
                            onAddInventory()
                        },
                        onDeleteClick = {
                            // Trigger delete item dialog/ confirm
                            onDeleteInventory()
                        }
                    )

                    Spacer(Modifier.padding(8.dp))
                }
            }

            Spacer(Modifier.padding(4.dp))

            // --------------------------------- List and Info Panes ---------------------------------------------------
            Row(
                modifier = Modifier.sizeIn(maxHeight = 500.dp)
            ) {
                // List of all the inventory items
                InventoryListPane(
                    isDarkMode = isDarkMode,
                    selected = selectedInventory?.inventory?.identifier,
                    pagingItems = inventoryList,
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
                    isDarkMode = isDarkMode,
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