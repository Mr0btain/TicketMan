package org.mr0btain.ticketman.ui.screens.main.expanded.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.DialogTextRow
import org.mr0btain.ticketman.ui.composables.note.NoteBlock
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.composables.shared.AddItemLabel
import org.mr0btain.ticketman.ui.composables.shared.ChipList
import org.mr0btain.ticketman.ui.composables.shared.ChipPhoneList
import org.mr0btain.ticketman.ui.composables.shared.ChipTicketList
import org.mr0btain.ticketman.ui.dialog.AddBasicSubItemDialog
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.EditNoteDialog
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.name_divider_character
import java.util.*

// TODO: Add ability to assign already existing tickets to customer.
// TODO: Add ability to edit note
@Composable
fun CustomerDialog(
    modifier: Modifier = Modifier,
    seed: CustomerSeed,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onImageClick: (UUID) -> Unit,
    isDarkMode: Boolean
) {
    val TAG = "CustomerDialog"

    if (isVisible) {
        // For deleting item dialog
        var showConfirmDeleteScreen by remember { mutableStateOf(false) }

        // TODO: Could combine this with adding an item, and have one when statement
        var dataType by remember { mutableStateOf(DataType.NONE) }

        // Might be a better way of doing this, for now just send it like this
        var itemToDelete by remember { mutableStateOf("") }

        // For adding a basic sub item
        var itemToAdd by remember { mutableStateOf(DataType.NONE) }
        var showAddBasicSubItemDialog by remember { mutableStateOf(false) }

        // For adding note dialog
        var showNoteDialog by remember { mutableStateOf(false) }
        val noteSeed = remember { NoteSeed() }

        var firstName by remember { mutableStateOf(seed.firstName) }
        var lastName by remember { mutableStateOf(seed.lastName) }

        // Add Note dialog
        EditNoteDialog(
            seed = noteSeed,
            onConfirm = {
                // TODO
                /*
                scope.launch {
                    val foundNote = notes.firstOrNull { it.note.identifier == result.note.identifier }

                    // Check if note exists, if so remove old data
                    if (foundNote != null) notes.remove(foundNote)

                    // Insert new note
                    notes.add(result)

                    noteSeedData = null

                    showNoteDialog = false
                }
                 */
            },
            isVisible = showNoteDialog,
            onDismissRequest = {
                showNoteDialog = false
            }
        )

        // Add basic item dialog
        // TODO: Redo this
        AddBasicSubItemDialog(
            isDarkMode = isDarkMode,
            subItemType = itemToAdd,
            isVisible = showAddBasicSubItemDialog,
            onConfirm = { item ->
                if (item.isNotBlank()) {
                    when (itemToAdd) {
                        DataType.EMAIL -> {
                            // Add item to list
                            seed.changeEmail(item)
                        }
                        DataType.PHONE -> {
                            // Add item to list
                            seed.changePhone(item)
                        }
                        else -> {}
                    }
                }
            },
            onDismissRequest = {
                showAddBasicSubItemDialog = false
                itemToAdd = DataType.NONE
            }
        )

        // Delete item dialog
        ConfirmDeleteDialog(
            dataType = dataType,
            isVisible = showConfirmDeleteScreen && dataType != DataType.NONE,
            onConfirm = {
                try {
                    when (dataType) {
                        DataType.TICKET -> seed.removeTicket(UUID.fromString(itemToDelete))
                        DataType.EMAIL -> seed.changeEmail("")
                        DataType.PHONE -> seed.changePhone("")
                        DataType.NOTE -> seed.removeNote(UUID.fromString(itemToDelete))
                        else -> {} // Do Nothing
                    }
                } catch (ex: Exception) {
                    AppLogger.i(TAG, "Error removing item! : $itemToDelete")
                    ex.printStackTrace()
                }

                dataType = DataType.NONE
                itemToDelete = ""
                showConfirmDeleteScreen = false
            },
            onCancel = {
                dataType = DataType.NONE
                showConfirmDeleteScreen = false
            }
        )

        Dialog(
            onDismissRequest = { onDismiss() }
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
                    // -------------------------------------------- Header ----------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier.padding(4.dp),
                            text = if (seed.name.isEmpty()) {
                                "Add Customer"
                            } else "Edit Customer: ${seed.name}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            maxLines = 1
                        )
                    }

                    HorizontalDivider()

                    Spacer(Modifier.padding(8.dp))

                    // -------------------------------------------- Content ----------------------------------------------------
                    PlatformLazyColumn(
                        modifier = Modifier
                            .width(500.dp)
                            .height(400.dp),
                        isVisible = true
                    ) {
                        item {
                            // ------------------------- Name -------------------------------
                            Text(
                                text = "Name",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            DialogTextRow(
                                modifier = Modifier,
                                firstLabel = "First",
                                firstValue = seed.firstName,
                                onFirstValueChange = { firstName = it },
                                secondLabel = "Last",
                                secondValue = seed.lastName,
                                onSecondValueChange = { lastName = it }
                            )

                            Spacer(Modifier.padding(8.dp))

                            // ------------------------- Address -------------------------------
                            Text(
                                text = "Address",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            Column(
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                DialogTextRow(
                                    modifier = Modifier,
                                    firstLabel = "Street",
                                    firstValue = seed.street,
                                    onFirstValueChange = { seed.changeStreet(it) },
                                    secondLabel = "City",
                                    secondValue = seed.city,
                                    onSecondValueChange = { seed.changeCity(it) }
                                )

                                DialogTextRow(
                                    modifier = Modifier,
                                    firstLabel = "State",
                                    firstValue = seed.state,
                                    onFirstValueChange = { seed.changeState(it) },
                                    secondLabel = "Zip",
                                    secondValue = seed.zip,
                                    onSecondValueChange = { seed.changeZip(it) }
                                )
                            }

                            Spacer(Modifier.padding(8.dp))

                            // ------------------------- ContactInfo -------------------------------
                            Text(
                                text = "Contact Info",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            AddItemLabel("Email") {
                                itemToAdd = DataType.EMAIL
                                showAddBasicSubItemDialog = true
                            }

                            // Email list
                            ChipList(
                                modifier = Modifier,
                                items = listOf(seed.email),
                                onEditClick = {},
                                onDeleteClick = { item ->
                                    // Set data type so we know what were looking to delete
                                    dataType = DataType.EMAIL

                                    // Set item data so we know which item to delete
                                    itemToDelete = item

                                    // Popup dialog
                                    showConfirmDeleteScreen = true
                                }
                            )

                            Spacer(Modifier.padding(4.dp))

                            AddItemLabel("Phone") {
                                itemToAdd = DataType.PHONE
                                showAddBasicSubItemDialog = true
                            }

                            // Phone List
                            ChipPhoneList(
                                modifier = Modifier,
                                items = listOf(seed.phone),
                                onEditClick = {},
                                onDeleteClick = { item ->
                                    // Set data type so we know what were looking to delete
                                    dataType = DataType.PHONE

                                    // Set item data so we know which item to delete
                                    itemToDelete = item

                                    // Popup dialog
                                    showConfirmDeleteScreen = true
                                }
                            )

                            AddItemLabel("Tickets") {
                                // TODO
                            }

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Ticket list -------------------------------
                            ChipTicketList(
                                tickets = seed.tickets.map { it.identifier },
                                onDeleteClick = { item ->
                                    // Set data type so we know what were looking to delete
                                    dataType = DataType.TICKET

                                    // Set item data so we know which item to delete
                                    itemToDelete = item.toString()

                                    // Popup dialog
                                    showConfirmDeleteScreen = true
                                },
                                onTicketClick = {
                                    // TODO
                                }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Notes -------------------------------
                            AddItemLabel("Notes") {
                                noteSeed.clear()
                                showNoteDialog = true
                            }

                            Spacer(Modifier.padding(4.dp))

                            NoteBlock(
                                notes = seed.notes,
                                editable = true,
                                onImageClick = onImageClick,
                                onEditClick = { note ->
                                    noteSeed.setNote(note)
                                    showNoteDialog = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.padding(8.dp))

                    // -------------------------------------------- Confirm Cancel buttons ----------------------------------------------------
                    ConfirmCancelButtons(
                        modifier = Modifier.fillMaxWidth(),
                        onCancelClick = { onDismiss() },
                        onConfirmClick = {
                            // Update first and last names if they've changed
                            if (firstName != seed.firstName || lastName != seed.lastName) {
                                   seed.changeName(listOf(firstName, lastName))
                            }

                            onContinue()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}