package org.mr0btain.ticketman.ui.screens.records.components.inventory

import org.mr0btain.ticketman.ui.kit.feedback.LocalLocations
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
import ticketman.composeapp.generated.resources.truck
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import kotlin.uuid.Uuid

/**
 * The picked item: description, pricing, photos and notes
 * @param onImageClick Opens the item's photos big
 * @param onMoveStock Opens moving its parts between locations, null to leave it out
 */
@Composable
fun InventoryInfoPane(
    inventoryData: InventorySeed,
    onEditClick: () -> Unit,
    onMoveStock: (() -> Unit)? = null,
    canEdit: Boolean = true,
    onImageClick: (page: Int) -> Unit = {},
    onNoteImageClick: (noteIdentifier: Uuid, page: Int) -> Unit = { _, _ -> },
    printAction: @Composable () -> Unit = {}
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

        DetailSection("Details") {
            Column {
                if (inventoryData.sku.isNotBlank()) TmKeyValueRow(label = "SKU", value = inventoryData.sku, numeric = true)
                if (inventoryData.barcode.isNotBlank()) TmKeyValueRow(label = "Barcode", value = inventoryData.barcode, numeric = true)
                if (inventoryData.category.isNotBlank()) TmKeyValueRow(label = "Category", value = inventoryData.category)
            }
            // Anyone can print a label, the scanner at the counter reads its barcode
            printAction()
        }

        DetailSection("Pricing") {
            Column {
                TmKeyValueRow(label = "Price") { TmMoneyText(Money.fromDollars(inventoryData.price), style = TmTheme.typography.numericLarge) }
                TmKeyValueRow(label = "Cost") { TmMoneyText(Money.fromDollars(inventoryData.cost)) }
                val locations = LocalLocations.current
                TmKeyValueRow(
                    label = when {
                        !locations.several -> "In stock"
                        locations.working == null -> "In stock, every location"
                        else -> "In stock here"
                    },
                    value = if (inventoryData.tracksStock) inventoryData.stock.toString() else "Not tracked",
                    numeric = inventoryData.tracksStock
                )
                // The other locations' counts, so a part can be fetched from across town
                if (locations.several && inventoryData.tracksStock && locations.working != null) {
                    locations.all.filter { !it.retired && it.identifier != locations.working }.forEach { location ->
                        val count = inventoryData.stockAt.firstOrNull { it.locationIdentifier == location.identifier }?.stock ?: 0
                        TmKeyValueRow(label = "At ${location.name}", value = count.toString(), numeric = true)
                    }
                }
                if (locations.several && inventoryData.tracksStock && canEdit && onMoveStock != null) {
                    TmButton(text = "Move stock", leadingIcon = Res.drawable.truck, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL, onClick = onMoveStock)
                }
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
