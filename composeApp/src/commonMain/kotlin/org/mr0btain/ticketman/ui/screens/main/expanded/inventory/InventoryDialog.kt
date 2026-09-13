package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.utils.MoneyVisualTransformation
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.DialogNoteList
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.dialog.EditNoteDialog
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

// TODO: Add ability to edit notes?
@Composable
fun InventoryDialog(
    modifier: Modifier = Modifier,
    seed: InventorySeed,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    if (isVisible) {
        // For deleting item dialog
        var showConfirmDeleteScreen by remember { mutableStateOf(false) }
        var itemUpForDeleteIndex by remember { mutableIntStateOf(0) }
        var itemForDelete by remember { mutableStateOf(DataType.NONE) }

        // For editing or adding notes dialog
        var showNoteDialog by remember { mutableStateOf(false) }

        val noteSeed = remember { NoteSeed() }

        // Edit or add update dialog
        EditNoteDialog(
            seed = noteSeed,
            onConfirm = {
                if (noteSeed.isDirty) seed.updateNote(noteSeed.note)
                noteSeed.clear()
                showNoteDialog = false
            },
            isVisible = showNoteDialog,
            onDismissRequest = {
                noteSeed.clear()
                showNoteDialog = false
            }
        )

        // Delete item dialog
        if (showConfirmDeleteScreen) {
            /*
            ConfirmDeleteDialog(
                label = when (itemForDelete) {
                    SubItemType.NOTE -> notes[itemUpForDeleteIndex].author + "'s Update"
                    else -> ""
                },
                isDarkMode = isDarkMode,
                mainItemType = MainItemType.CUSTOMER,
                onDismissRequest = { confirm ->
                    if (confirm && itemForDelete != SubItemType.NONE) {
                        when (itemForDelete) {
                            SubItemType.NOTE -> {
                                // Remove item from list
                                notes.removeAt(itemUpForDeleteIndex)
                            }

                            // For everything else do nothing
                            else -> {}
                        }
                    }

                    // Reset items for delete and set index to insane value
                    itemUpForDeleteIndex = 10000
                    itemForDelete = SubItemType.NONE

                    showConfirmDeleteScreen = false
                },
            )
             */
        }

        Dialog(
            onDismissRequest = { onDismiss() }
        ) {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // -------------------------------------------- Header ----------------------------------------------------
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(4.dp),
                            text = if (seed.inventory.name.isEmpty()) "Add Item" else "Edit Item: ${seed.inventory.name}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            maxLines = 1
                        )
                    }

                    HorizontalDivider()

                    Spacer(Modifier.padding(8.dp))

                    // -------------------------------------------- Content ----------------------------------------------------
                    PlatformLazyColumn(
                        modifier = Modifier.height(400.dp),
                        isVisible = seed.notes.isNotEmpty()
                    ) {
                        item {
                            // -------------------------- Name ------------------------
                            OutlinedTextField(
                                value = seed.name,
                                onValueChange = { seed.changeName(it) },
                                label = { Text("Name") }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Description -------------------------------
                            OutlinedTextField(
                                value = seed.description,
                                onValueChange = { seed.changeDescription(it) },
                                label = { Text("Description") }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Stock -------------------------------
                            OutlinedTextField(
                                value = seed.stock.toString(),
                                onValueChange = { change ->
                                    seed.changeStock(
                                        change.filter { it.isDigit() }.toInt()
                                    )
                                },
                                label = { Text("Stock") }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Price -------------------------------
                            OutlinedTextField(
                                modifier = Modifier.width(280.dp),
                                value = seed.price.toString(),
                                onValueChange = {
                                        newPrice -> seed.changePrice(
                                    newPrice
                                        .filter { it.isDigit() || it == '.' }
                                        .toDoubleOrNull() ?: 0.0
                                )
                                },
                                label = { Text("Price") },
                                prefix = { Text("$") },
                                visualTransformation = MoneyVisualTransformation()
                            )

                            Spacer(Modifier.padding(4.dp))

                            // TODO: Test this
                            // ------------------------- Cost -------------------------------
                            OutlinedTextField(
                                modifier = Modifier.width(280.dp),
                                value = seed.cost.toString(),
                                onValueChange = {
                                        newPrice -> seed.changeCost(
                                    newPrice
                                        .filter { it.isDigit() || it == '.' }
                                        .toDoubleOrNull() ?: 0.0
                                )
                                },
                                label = { Text("Price") },
                                prefix = { Text("$") },
                                visualTransformation = MoneyVisualTransformation()
                            )
                            /*
                            MoneyTextField(
                                modifier = Modifier.width(280.dp),
                                state = cost,
                                label = "Cost"
                            )
                             */

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Notes -------------------------------
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Notes",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )

                                Spacer(Modifier.padding(4.dp))

                                IconButton(
                                    modifier = Modifier.size(26.dp),
                                    onClick = { showNoteDialog = true }
                                ) {
                                    Icon(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(20.dp),
                                        painter = painterResource(Res.drawable.plus),
                                        contentDescription = "Add new Note"
                                    )
                                }
                            }

                            Spacer(Modifier.padding(4.dp))

                            DialogNoteList(
                                notes = seed.notes,
                                onDeleteClick = { item ->
                                    itemForDelete = DataType.NOTE
                                    itemUpForDeleteIndex = item
                                    showConfirmDeleteScreen = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.padding(8.dp))

                    ConfirmCancelButtons(
                        onCancelClick = onDismiss,
                        onConfirmClick = onContinue
                    )
                }
            }
        }
    }
}