package org.mr0btain.ticketman.ui.screens.main.components.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.components.inventory.InventoryItem
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.ui.composables.scrollbar.LazyVerticalScrollBar
import org.mr0btain.ticketman.ui.screens.main.components.TableTicketIdItem

// TODO: Add time or date to these

@Composable
fun InventoryListPane(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    ticketInfo: List<InventoryItem> = listOf(),
    selectedId: Int,
    onClick: (Int) -> Unit,
    state: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope
) {
    // Column widths
    val nameColumnWidth = 100.dp
    val stockColumnWidth = 80.dp
    val descriptionColumnWidth = 260.dp
    val priceColumnWidth = 80.dp
    val costColumnWidth = 80.dp

    Surface(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
    ) {
        // ------------------------------------------ Header -----------------------------------------------------
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(10.dp))

                InventoryHeaderBox(
                    label = "Name",
                    width = nameColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                InventoryHeaderBox(
                    label = "Stock",
                    width = stockColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                InventoryHeaderBox(
                    label = "Description",
                    width = descriptionColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                InventoryHeaderBox(
                    label = "Price",
                    width = priceColumnWidth,
                    dividerEnable = false
                )

                Spacer(Modifier.padding(10.dp))

                InventoryHeaderBox(
                    label = "Cost",
                    width = costColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(750.dp))

            if (getPlatform().name.contains("Java")) {
                LazyVerticalScrollBar(
                    state = state,
                    scope = coroutineScope
                ) {
                    itemsIndexed(ticketInfo) { index, item ->
                        InventoryTableItem(
                            color = if (!isDarkMode) {
                                if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            } else {
                                if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            },
                            selected = selectedId == item.identifier,
                            inventoryItem = item,
                            nameColumnWidth = nameColumnWidth,
                            stockColumnWidth = stockColumnWidth,
                            descriptionColumnWidth = descriptionColumnWidth,
                            costColumnWidth = costColumnWidth,
                            priceColumnWidth = priceColumnWidth,
                            isDarkMode = isDarkMode,
                            onClick = { onClick(item.identifier) }
                        )

                        Spacer(Modifier.padding(2.dp))
                    }
                }
            } else {
                LazyColumn(
                    state = state
                ) {
                    itemsIndexed(ticketInfo) { index, item ->
                        InventoryTableItem(
                            color = if (!isDarkMode) {
                                if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            } else {
                                if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            },
                            selected = selectedId == item.identifier,
                            inventoryItem = item,
                            nameColumnWidth = nameColumnWidth,
                            stockColumnWidth = stockColumnWidth,
                            descriptionColumnWidth = descriptionColumnWidth,
                            costColumnWidth = costColumnWidth,
                            priceColumnWidth = priceColumnWidth,
                            isDarkMode = isDarkMode,
                            onClick = { onClick(item.identifier) }
                        )

                        Spacer(Modifier.padding(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryHeaderBox(
    modifier: Modifier = Modifier,
    label: String,
    width: Dp,
    dividerEnable: Boolean = true
) {
    Row(
        modifier.width(width),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        if (dividerEnable) {
            VerticalDivider(Modifier.height(42.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InventoryTableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    selected: Boolean,
    inventoryItem: InventoryItem,
    nameColumnWidth: Dp,
    stockColumnWidth: Dp,
    descriptionColumnWidth: Dp,
    priceColumnWidth: Dp,
    costColumnWidth: Dp,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                onClick = { onClick() }
            ),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = color,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent)
    ) {
        val sizeAdjust = -8

        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(nameColumnWidth+ sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableTicketIdItem(
                    label = inventoryItem.name,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 5.dp)
                    .width(stockColumnWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableTicketIdItem(
                    label = inventoryItem.stock.toString(),
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(descriptionColumnWidth+ sizeAdjust.dp)
            ) {
                TableTicketIdItem(
                    label = inventoryItem.description,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(priceColumnWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableTicketIdItem(
                    label = inventoryItem.price,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(costColumnWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableTicketIdItem(
                    label = inventoryItem.cost,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}