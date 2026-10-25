package org.mr0btain.ticketman.ui.screens.records.components.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.images.TmPhotoEditor
import org.mr0btain.ticketman.ui.kit.notes.TmEditNoteDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.data.LocalTaxes
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

/**
 * Adds or edits an inventory item. Changes go straight onto [seed]
 * @param onContinue Saves the seed and closes the dialog. Cancelling goes through onDismiss, which reloads the record
 */
@Composable
fun InventoryDialog(
    seed: InventorySeed,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    if (!isVisible) return

    var showNoteDialog by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    val noteSeed = remember { NoteSeed() }
    val spacing = TmTheme.spacing

    TmEditNoteDialog(
        seed = noteSeed,
        label = if (noteSeed.isNew) "Add note" else "Edit note",
        isVisible = showNoteDialog,
        onConfirm = { seed.saveNote(noteSeed.note) },
        onDelete = { seed.deleteNote(noteSeed.identifier) },
        onDismissRequest = {
            noteSeed.clear()
            showNoteDialog = false
        }
    )

    TmDialog(
        title = if (seed.inventory.name.isEmpty()) "New item" else "Edit ${seed.inventory.name}",
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (seed.isNew) "Add item" else "Save item",
                // Saving mid upload would leave the photo off the item
                confirmEnabled = seed.name.isNotBlank() && seed.isDirty && !isUploading,
                onCancel = onDismiss,
                onConfirm = onContinue
            )
        }
    ) {
        TmTextField(modifier = Modifier.fillMaxWidth(), value = seed.name, onValueChange = { seed.changeName(it) }, label = "Name")
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = seed.description,
            onValueChange = { seed.changeDescription(it) },
            label = "Description",
            singleLine = false,
            minLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = seed.sku,
                onValueChange = { seed.changeSku(it.take(64)) },
                label = "SKU",
                textStyle = TmTheme.typography.numeric
            )
            TmTextField(
                modifier = Modifier.weight(1f),
                value = seed.barcode,
                onValueChange = { seed.changeBarcode(it.filterNot(Char::isWhitespace).take(64)) },
                label = "Barcode",
                placeholder = "Scan it here",
                textStyle = TmTheme.typography.numeric
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(
                modifier = Modifier.weight(2f),
                value = seed.category,
                onValueChange = { seed.changeCategory(it.take(60)) },
                label = "Category",
                placeholder = "Screens, batteries"
            )
            TmTextField(
                modifier = Modifier.weight(1f),
                value = seed.reorderPoint?.toString().orEmpty(),
                onValueChange = { text -> seed.changeReorderPoint(text.filter { it.isDigit() }.take(6).toIntOrNull()) },
                label = "Reorder at",
                placeholder = "Default",
                enabled = seed.tracksStock,
                textStyle = TmTheme.typography.numeric,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = if (seed.tracksStock) seed.stock.toString() else "",
                onValueChange = { text -> seed.changeStock(text.filter { it.isDigit() }.take(9).toIntOrNull() ?: 0) },
                label = "In stock",
                placeholder = if (seed.tracksStock) null else "Not tracked",
                enabled = seed.tracksStock,
                textStyle = TmTheme.typography.numeric,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            TmMoneyField(
                modifier = Modifier.weight(1f),
                label = "Cost",
                cents = Money.fromDollars(seed.cost),
                onCentsChange = { seed.changeCost(Money.toDollars(it)) }
            )
            TmMoneyField(
                modifier = Modifier.weight(1f),
                label = "Price",
                cents = Money.fromDollars(seed.price),
                onCentsChange = { seed.changePrice(Money.toDollars(it)) }
            )
        }

        TmCheckbox(
            checked = seed.tracksStock,
            onCheckedChange = { seed.changeTracksStock(it) },
            label = "Track stock"
        )

        // Only when the shop has rates besides its main one
        val taxes = LocalTaxes.current
        if (taxes.classes.isNotEmpty()) {
            TmDropdown(
                label = "Taxed at",
                // Blank is the main rate, since the dropdown takes no selection as nothing picked
                options = listOf("") + taxes.classes.map { it.identifier },
                selected = seed.taxClass?.takeIf { id -> taxes.classes.any { it.identifier == id } } ?: "",
                onSelect = { seed.changeTaxClass(it.ifEmpty { null }) },
                optionLabel = { taxes.label(taxable = true, taxClass = it.ifEmpty { null }) },
                supportingText = "Parts from inventory start with this on invoices"
            )
        }

        TmSectionLabel("Photos")
        TmPhotoEditor(
            images = seed.images,
            onAdd = { seed.addImage(it) },
            onRemove = { seed.removeImage(it.identifier) },
            onUploadingChange = { isUploading = it }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TmSectionLabel("Notes") }
            TmButton(
                text = "Add note",
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.SMALL,
                leadingIcon = Res.drawable.plus,
                onClick = {
                    noteSeed.clear()
                    showNoteDialog = true
                }
            )
        }

        TmNoteBlock(
            notes = seed.notes,
            editable = true,
            onEditClick = { note ->
                noteSeed.setNote(note)
                showNoteDialog = true
            }
        )
    }
}
