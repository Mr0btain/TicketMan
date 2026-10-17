package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.screens.shared.NoteBlock
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.data.MoneyText
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.stockText
import org.mr0btain.ticketman.ui.screens.main.expanded.DetailSection
import org.mr0btain.ticketman.ui.screens.main.expanded.RecordDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.package_icon

// TODO: Add photo options
@Composable
fun InventoryInfoPane(
    inventoryData: InventorySeed,
    onEditClick: () -> Unit
) {
    RecordDetail(
        title = inventoryData.name.ifBlank { "Unnamed item" },
        subtitle = stockText(inventoryData.stock),
        icon = Res.drawable.package_icon,
        isEmpty = inventoryData.isNew,
        emptyTitle = "No item picked",
        onEdit = onEditClick
    ) {
        if (inventoryData.description.isNotBlank()) {
            DetailSection("Description") {
                Text(text = inventoryData.description, style = TmTheme.typography.body, color = TmTheme.colors.text)
            }
        }

        DetailSection("Pricing") {
            Column {
                KeyValueRow(label = "Price") { MoneyText(Money.fromDollars(inventoryData.price), style = TmTheme.typography.numericLarge) }
                KeyValueRow(label = "Cost") { MoneyText(Money.fromDollars(inventoryData.cost)) }
                KeyValueRow(label = "In stock", value = inventoryData.stock.toString(), numeric = true)
                KeyValueRow(label = "Added", value = formatDateTime(inventoryData.time.toEpochMilliseconds()))
            }
        }

        DetailSection("Notes") {
            NoteBlock(notes = inventoryData.notes)
        }
    }
}
