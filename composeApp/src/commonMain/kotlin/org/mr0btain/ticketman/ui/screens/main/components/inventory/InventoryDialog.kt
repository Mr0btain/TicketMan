package org.mr0btain.ticketman.ui.screens.main.components.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.customer.CustomerInfo
import org.mr0btain.ticketman.components.data.components.employee.Employee
import org.mr0btain.ticketman.components.data.components.inventory.InventoryItem
import org.mr0btain.ticketman.components.data.components.shared.DialogDismissReason
import org.mr0btain.ticketman.components.data.components.shared.MainItemType
import org.mr0btain.ticketman.components.data.components.shared.PersonName
import org.mr0btain.ticketman.components.data.components.shared.SubItemType
import org.mr0btain.ticketman.components.data.components.shared.getTime
import org.mr0btain.ticketman.components.data.components.ticket.Device
import org.mr0btain.ticketman.components.data.components.ticket.TicketInfo
import org.mr0btain.ticketman.ui.composables.CustomerSearch
import org.mr0btain.ticketman.ui.composables.EmployeeSearch
import org.mr0btain.ticketman.ui.composables.TicketPriorityDropDown
import org.mr0btain.ticketman.ui.composables.TicketStatusDropDown
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.NoteDialog
import org.mr0btain.ticketman.ui.screens.main.components.DialogNoteList
import org.mr0btain.ticketman.ui.screens.main.components.DialogTextRow
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

// TODO: Add ability to edit update?
@Composable
fun InventoryDialog(
    modifier: Modifier = Modifier,
    onDismiss: (InventoryItem?, DialogDismissReason) -> Unit,
    seedData: InventoryItem,
    isDarkMode: Boolean
) {
    // seed data
    var name by remember(seedData) { mutableStateOf(seedData.name) }
    var description by remember(seedData) { mutableStateOf(seedData.description) }
    var stock by remember(seedData) { mutableStateOf(seedData.stock) }
    var price by remember(seedData) { mutableStateOf(seedData.price) }
    var cost by remember(seedData) { mutableStateOf(seedData.cost) }

    // Mutable lists
    val notes = remember(seedData) { seedData.notes.toMutableStateList() }

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
        onDismissRequest = {
            // Call dismiss as we haven't confirmed anything
            onDismiss(null, DialogDismissReason.CANCEL)
        }
    ) {
        Surface(
            modifier = modifier
                .height(600.dp)
                .width(600.dp),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = "Add New Ticket: ${seedData.identifier}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(4.dp),
                    color = if (isDarkMode) MaterialTheme.colorScheme.onSurface else DividerDefaults.color
                )

                Spacer(Modifier.padding(8.dp))

                // TODO: Make this regular ScrollBarColumn for desktops wintout scroll wheel
                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    item {

                        Text(
                            text = "Name",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        // TODO

                        Spacer(Modifier.padding(4.dp))

                        Text(
                            text = "Description",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        // TODO

                        Spacer(Modifier.padding(8.dp))

                        Text(
                            text = "Stock",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        // TODO

                        Spacer(Modifier.padding(4.dp))

                        Text(
                            text = "Price",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        // TODO

                        Spacer(Modifier.padding(4.dp))

                        Text(
                            text = "Cost",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        // TODO

                        Spacer(Modifier.padding(4.dp))

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
                }

                Spacer(Modifier.padding(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onDismiss(null, DialogDismissReason.CANCEL) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.padding(8.dp))

                    Button(
                        onClick = {
                            // Call dismiss with data
                            onDismiss(
                                InventoryItem(
                                    identifier = seedData.identifier,
                                    name = name,
                                    description = description,
                                    stock = stock,
                                    price = price,
                                    cost = cost,
                                    time = if (seedData.time != Long.MAX_VALUE) seedData.time else getTime()
                                ),
                                DialogDismissReason.CONTINUE
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}