package org.mr0btain.ticketman.ui.screens.dashboard.components.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.screens.shared.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.data.TmImagePager
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.data.MoneyText
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.stockText
import org.mr0btain.ticketman.ui.screens.dashboard.components.DetailSection
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.package_icon
import kotlin.uuid.Uuid

/**
 * The picked item: description, pricing, photos and notes
 * @param onImageClick Opens the item's photos big
 */
@Composable
fun InventoryInfoPane(
    inventoryData: InventorySeed,
    onEditClick: () -> Unit,
    onImageClick: (page: Int) -> Unit = {},
    onNoteImageClick: (noteIdentifier: Uuid, page: Int) -> Unit = { _, _ -> }
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

        if (inventoryData.images.isNotEmpty()) {
            DetailSection("Photos") {
                TmImagePager(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    storedImages = inventoryData.images,
                    onImageClick = onImageClick
                )
            }
        }

        DetailSection("Notes") {
            TmNoteBlock(notes = inventoryData.notes, onImageClick = onNoteImageClick)
        }
    }
}
