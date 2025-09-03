package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.inventory.InventoryData
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.data.shared.DialogDismissReason
import org.mr0btain.ticketman.data.shared.MainItemType
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.SubItemType
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.ui.composables.MoneyTextField
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.NoteDialog
import org.mr0btain.ticketman.ui.screens.main.components.ConfirmOrCancelButtonRow
import org.mr0btain.ticketman.ui.screens.main.components.DialogNoteList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import kotlin.uuid.ExperimentalUuidApi

// TODO: Add ability to edit notes?
@OptIn(ExperimentalUuidApi::class)
@Composable
fun InventoryDialog(
    modifier: Modifier = Modifier,
    onDismiss: (InventoryData?, DialogDismissReason) -> Unit,
    seedData: InventoryData,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean
) {
    // seed data
    var name by remember(seedData) { mutableStateOf(seedData.inventory.name) }
    var description by remember(seedData) { mutableStateOf(seedData.inventory.description) }
    var stock by remember(seedData) { mutableStateOf(if (seedData.inventory.stock != Int.MAX_VALUE) seedData.inventory.stock.toString() else "0") }
    val price = rememberTextFieldState(seedData.inventory.price.filter { it.isDigit() })
    val cost = rememberTextFieldState(seedData.inventory.cost.filter { it.isDigit() })

    // Mutable lists
    val notes = mutableStateListOf<Note>() //remember(seedData) { seedData.notes.toMutableStateList() }

    // For deleting item dialog
    var showConfirmDeleteScreen by remember { mutableStateOf(false) }
    var itemUpForDeleteIndex by remember { mutableIntStateOf(0) }
    var itemForDelete by remember { mutableStateOf(SubItemType.NONE) }

    // For adding note dialog
    var showAddNoteDialog by remember { mutableStateOf(false) }

    // Add Note dialog
    if (showAddNoteDialog) {
        NoteDialog(
            isDarkMode = isDarkMode,
            label = "Update",
            onConfirm = { item ->
                // Add to out list
                notes.add(item)
            },
            onDismissRequest = { showAddNoteDialog = false }
        )
    }

    // Delete item dialog
    if (showConfirmDeleteScreen) {
        ConfirmDeleteDialog(
            itemName = when (itemForDelete) {
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
    }

    Dialog(
        properties = DialogProperties(),
        onDismissRequest = {
            // Call dismiss as we haven't confirmed anything
            onDismiss(null, DialogDismissReason.CANCEL)
        }
    ) {
        Surface(
            modifier = modifier
                .height(600.dp)
                .width(400.dp),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // -------------------------------------------- Header ----------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = if (seedData.inventory.name.isEmpty()) "Add Item" else "Edit Item: ${seedData.inventory.name}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp,
                        maxLines = 1
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(4.dp),
                    color = if (isDarkMode) MaterialTheme.colorScheme.onSurface else DividerDefaults.color
                )

                Spacer(Modifier.padding(8.dp))

                // -------------------------------------------- Content ----------------------------------------------------
                PlatformColumnScrollable(
                    modifier = Modifier.height(400.dp),
                    columnModifier = Modifier.width(300.dp),
                    scrollBarOnRight = scrollBarsOnRight,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    isVisible = notes.isNotEmpty()
                ) {
                    // -------------------------- Name ------------------------
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") }
                    )

                    Spacer(Modifier.padding(4.dp))

                    // ------------------------- Description -------------------------------
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") }
                    )

                    Spacer(Modifier.padding(4.dp))

                    // ------------------------- Stock -------------------------------
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { change -> stock = change.filter { it.isDigit() } },
                        label = { Text("Stock") }
                    )

                    Spacer(Modifier.padding(4.dp))

                    // ------------------------- Price -------------------------------
                    MoneyTextField(
                        modifier = Modifier.width(280.dp),
                        state = price,
                        label = "Price"
                    )

                    Spacer(Modifier.padding(4.dp))

                    // ------------------------- Cost -------------------------------
                    MoneyTextField(
                        modifier = Modifier.width(280.dp),
                        state = cost,
                        label = "Cost"
                    )

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
                            onClick = { showAddNoteDialog = true }
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
                        notes = notes,
                        onDeleteClick = { item ->
                            itemForDelete = SubItemType.NOTE
                            itemUpForDeleteIndex = item
                            showConfirmDeleteScreen = true
                        }
                    )
                }

                Spacer(Modifier.padding(8.dp))

                ConfirmOrCancelButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                    onCancelClick = { onDismiss(null, DialogDismissReason.CANCEL) },
                    onConfirmClick = {
                        // Call dismiss with data
                        onDismiss(
                            InventoryData(
                                inventory = Inventory(
                                    identifier = seedData.inventory.identifier,
                                    name = name,
                                    description = description,
                                    stock = stock.toIntOrNull() ?: 0,
                                    price = price.text.toString(),
                                    cost = cost.text.toString(),
                                    time = if (seedData.inventory.time != Long.MAX_VALUE) seedData.inventory.time else getTime()
                                )
                            ),
                            DialogDismissReason.CONTINUE
                        )
                    }
                )
            }
        }
    }
}