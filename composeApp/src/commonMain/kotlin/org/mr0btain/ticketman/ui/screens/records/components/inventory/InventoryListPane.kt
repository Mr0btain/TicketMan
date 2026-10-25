package org.mr0btain.ticketman.ui.screens.records.components.inventory

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.records.components.NARROW_LIST_WIDTH
import org.mr0btain.ticketman.ui.screens.records.components.RecordNameCell
import org.mr0btain.ticketman.ui.screens.records.components.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.package_icon
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Name", weight = 1.6f),
    TmColumn("SKU, category", weight = 1f),
    TmColumn("Stock", width = 88.dp, end = true),
    TmColumn("Cost", width = 88.dp, end = true),
    TmColumn("Price", width = 88.dp, end = true)
)

// Narrow: the name gets the room, with its code, category or description under it
private val NARROW_COLUMNS = listOf(
    TmColumn("Item", weight = 1f),
    TmColumn("Stock", width = 56.dp, end = true),
    TmColumn("Price", width = 80.dp, end = true)
)

@Composable
fun InventoryListPane(
    inventoryList: List<Inventory>,
    selected: Uuid,
    onClick: (Uuid) -> Unit,
    state: LazyListState,
    loadError: String? = null
) {
    val colors = TmTheme.colors

    BoxWithConstraints {
        val narrow = maxWidth < NARROW_LIST_WIDTH
        val columns = if (narrow) NARROW_COLUMNS else COLUMNS

        RecordsList(
            columns = columns,
            state = state,
            isEmpty = inventoryList.isEmpty(),
            emptyTitle = "No items yet",
            emptyIcon = Res.drawable.package_icon,
            emptyMessage = "Add parts with New item so they can go on invoices.",
            loadError = loadError
        ) {
            items(inventoryList, key = { it.identifier }) { item ->
                // Its code and category, or the description when it has neither
                val detail = listOf(item.sku, item.category).filter { it.isNotBlank() }.joinToString(", ").ifBlank { item.description }

                TmTableRow(columns, selected = item.identifier == selected, onClick = { onClick(item.identifier) }) { column ->
                    when (if (narrow) NARROW_TO_WIDE[column] else column) {
                        0 -> if (narrow) RecordNameCell(item.name, detail)
                            else Text(item.name, style = TmTheme.typography.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        1 -> Text(detail, style = TmTheme.typography.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        2 -> if (item.stock == Int.MAX_VALUE) {
                            Text(if (narrow) "–" else "Not tracked", style = TmTheme.typography.body, color = colors.textMuted, maxLines = 1)
                        } else {
                            Text(
                                text = item.stock.toString(),
                                style = TmTheme.typography.numeric,
                                color = if (item.stock <= 0) colors.danger.color else colors.text
                            )
                        }
                        3 -> TmMoneyText(item.costCents, color = colors.textMuted)
                        4 -> TmMoneyText(item.priceCents)
                    }
                }
            }
        }
    }
}

// Which wide column each narrow one shows
private val NARROW_TO_WIDE = listOf(0, 2, 4)
