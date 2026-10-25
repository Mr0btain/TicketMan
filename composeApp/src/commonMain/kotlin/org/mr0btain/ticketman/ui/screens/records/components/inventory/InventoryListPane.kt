package org.mr0btain.ticketman.ui.screens.records.components.inventory

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
import org.mr0btain.ticketman.ui.screens.records.components.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.package_icon
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Name", weight = 1f),
    TmColumn("SKU, category", weight = 1.4f),
    TmColumn("Stock", width = 88.dp, end = true),
    TmColumn("Cost", width = 88.dp, end = true),
    TmColumn("Price", width = 88.dp, end = true)
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

    RecordsList(
        columns = COLUMNS,
        state = state,
        isEmpty = inventoryList.isEmpty(),
        emptyTitle = "No items yet",
        emptyIcon = Res.drawable.package_icon,
        emptyMessage = "Add parts with New item so they can go on invoices.",
        loadError = loadError
    ) {
        items(inventoryList, key = { it.identifier }) { item ->
            TmTableRow(COLUMNS, selected = item.identifier == selected, onClick = { onClick(item.identifier) }) { column ->
                when (column) {
                    0 -> Text(item.name, style = TmTheme.typography.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Its code and category, or the description when it has neither
                    1 -> Text(
                        listOf(item.sku, item.category).filter { it.isNotBlank() }.joinToString(", ").ifBlank { item.description },
                        style = TmTheme.typography.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    2 -> if (item.stock == Int.MAX_VALUE) {
                        Text("Not tracked", style = TmTheme.typography.body, color = colors.textMuted, maxLines = 1)
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
