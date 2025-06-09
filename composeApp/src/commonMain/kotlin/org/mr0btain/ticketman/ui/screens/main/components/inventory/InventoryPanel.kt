package org.mr0btain.ticketman.ui.screens.main.components.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.components.customer.CustomerData
import org.mr0btain.ticketman.components.data.components.inventory.InventoryItem
import org.mr0btain.ticketman.components.data.components.ticket.TicketInfo
import org.mr0btain.ticketman.ui.composables.InventorySearch
import org.mr0btain.ticketman.ui.composables.TicketSearch
import org.mr0btain.ticketman.ui.screens.main.components.AddOrDeleteButtonRow

// TODO: Setup setting reorder on stock hitting a certain amount. Like a popup on login or when it happens?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    inventoryList: List<InventoryItem>,
    selectedInventoryItemId: Int,
    onSelectedInventoryItemChange: (Int) -> Unit,
    onAddInventoryItem: () -> Unit,
    onEditInventoryItem: (Int) -> Unit,
    onDeleteInventoryItem: (Int) -> Unit,
    coroutineScope: CoroutineScope,
    inventoryListState: LazyListState
) {
    // Selected inventory item info
    val selectedInventoryItem = inventoryList.firstOrNull() { it.identifier == selectedInventoryItemId } ?: TicketInfo()

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
                InventorySearch(
                    modifier = Modifier.padding(start = 8.dp),
                    items = inventoryList,
                    input = searchInput,
                    onInputChange = { searchInput = it },
                    onClick = {
                        // Set search input in out text box
                        searchInput = "${it.name}: ${it.identifier}"

                        // Call selected item change
                        onSelectedInventoryItemChange(it.identifier)
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
                            onAddInventoryItem()
                        },
                        onDeleteClick = {
                            // Trigger delete item dialog/ confirm
                            onDeleteInventoryItem(selectedInventoryItemId)
                        }
                    )

                    Spacer(Modifier.padding(8.dp))
                }
            }

            Spacer(Modifier.padding(4.dp))

            // --------------------------------- List and Info Panes ---------------------------------------------------
            Row {
                // List of all the inventory items
                InventoryListPane(
                    isDarkMode = isDarkMode,
                    selectedId = selectedInventoryItemId,
                    items = inventoryList,
                    onClick = {
                        // Set is update from click
                        isSelectedUpdateFromClick = true

                        // change selected item
                        onSelectedInventoryItemChange(it)
                    },
                    state = inventoryListState,
                    coroutineScope = coroutineScope
                )

                InventoryInfoPane(
                    isDarkMode = isDarkMode,
                    inventoryItem = inventoryList.firstOrNull { it.identifier == selectedInventoryItemId } ?: InventoryItem(),
                    onEditClick = { onEditInventoryItem(selectedInventoryItemId) }
                )
            }
        }
    }

    // --------------------------------- Launched effect for selected ticket ---------------------------------------------------

    LaunchedEffect(selectedInventoryItemId) {
        if (!isSelectedUpdateFromClick) {
            // Get index of our item
            val index = inventoryList.indexOf(selectedInventoryItem)

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
    }
}