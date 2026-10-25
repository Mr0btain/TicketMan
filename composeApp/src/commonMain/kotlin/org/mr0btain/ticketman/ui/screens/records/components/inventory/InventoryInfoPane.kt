package org.mr0btain.ticketman.ui.screens.records.components.inventory

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
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.images.TmImagePager
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.stockText
import org.mr0btain.ticketman.ui.screens.records.components.DetailSection
import org.mr0btain.ticketman.ui.screens.records.components.RecordDetail
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
    canEdit: Boolean = true,
    onImageClick: (page: Int) -> Unit = {},
    onNoteImageClick: (noteIdentifier: Uuid, page: Int) -> Unit = { _, _ -> }
) {
    RecordDetail(
        title = inventoryData.name.ifBlank { "Unnamed item" },
        subtitle = stockText(inventoryData.stock),
        icon = Res.drawable.package_icon,
        isEmpty = inventoryData.isNew,
        emptyTitle = "No item picked",
        onEdit = onEditClick,
        canEdit = canEdit
    ) {
        if (inventoryData.description.isNotBlank()) {
            DetailSection("Description") {
                Text(text = inventoryData.description, style = TmTheme.typography.body, color = TmTheme.colors.text)
            }
        }

        if (listOf(inventoryData.sku, inventoryData.barcode, inventoryData.category).any { it.isNotBlank() }) {
            DetailSection("Details") {
                Column {
                    if (inventoryData.sku.isNotBlank()) TmKeyValueRow(label = "SKU", value = inventoryData.sku, numeric = true)
                    if (inventoryData.barcode.isNotBlank()) TmKeyValueRow(label = "Barcode", value = inventoryData.barcode, numeric = true)
                    if (inventoryData.category.isNotBlank()) TmKeyValueRow(label = "Category", value = inventoryData.category)
                }
            }
        }

        DetailSection("Pricing") {
            Column {
                TmKeyValueRow(label = "Price") { TmMoneyText(Money.fromDollars(inventoryData.price), style = TmTheme.typography.numericLarge) }
                TmKeyValueRow(label = "Cost") { TmMoneyText(Money.fromDollars(inventoryData.cost)) }
                TmKeyValueRow(
                    label = "In stock",
                    value = if (inventoryData.tracksStock) inventoryData.stock.toString() else "Not tracked",
                    numeric = inventoryData.tracksStock
                )
                inventoryData.reorderPoint?.takeIf { inventoryData.tracksStock }?.let {
                    TmKeyValueRow(label = "Reorder at", value = it.toString(), numeric = true)
                }
                TmKeyValueRow(label = "Added", value = formatDateTime(inventoryData.time.toEpochMilliseconds()))
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
