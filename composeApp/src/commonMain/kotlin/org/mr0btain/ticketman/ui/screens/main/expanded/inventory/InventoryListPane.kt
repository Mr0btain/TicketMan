package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumnScrollable
import org.mr0btain.ticketman.ui.screens.main.components.TableCardTextItem
import org.mr0btain.ticketman.ui.screens.main.components.TableDivider

@Composable
fun InventoryListPane(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    items: List<InventoryItem>,
    scrollBarsOnRight: Boolean,
    selected: String,
    onClick: (String) -> Unit,
    state: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope
) {
    // Column widths
    val nameColumnWidth = 160.dp
    val stockColumnWidth = 60.dp
    val descriptionColumnWidth = 260.dp
    val priceColumnWidth = 60.dp
    val costColumnWidth = 60.dp

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
                .padding(start = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(4.dp))

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
                    width = priceColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                InventoryHeaderBox(
                    label = "Cost",
                    width = costColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(690.dp))

            if (getPlatform().name.contains("Java")) {
                PlatformLazyColumnScrollable(
                    modifier = Modifier
                        .width(720.dp)
                        .padding(start = 4.dp),
                    state = state,
                    scope = coroutineScope,
                    scrollBarOnRight = scrollBarsOnRight,
                    isVisible = items.isNotEmpty()
                ) {
                    itemsIndexed(items) { index, item ->
                        InventoryTableItem(
                            color = if (!isDarkMode) {
                                if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            } else {
                                if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            },
                            selected = selected == item.identifier,
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
                    itemsIndexed(items) { index, item ->
                        InventoryTableItem(
                            color = if (!isDarkMode) {
                                if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            } else {
                                if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            },
                            selected = selected == item.identifier,
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
            // Name
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(nameColumnWidth+ sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableCardTextItem(inventoryItem.name)
            }

            TableDivider()

            // Stock
            Box(
                modifier = Modifier
                    .padding(start = 5.dp)
                    .width(stockColumnWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableCardTextItem(inventoryItem.stock.toString())
            }

            TableDivider()

            // Description
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(descriptionColumnWidth+ sizeAdjust.dp)
            ) {
                TableCardTextItem(inventoryItem.description)
            }

            TableDivider()

            // Price
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(priceColumnWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableCardTextItem(inventoryItem.price)
            }

            TableDivider()

            // Cost
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(costColumnWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableCardTextItem(inventoryItem.cost)
            }
        }
    }
}