package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.DialogTextRow
import org.mr0btain.ticketman.ui.composables.note.NoteBlock
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.composables.search.SearchBar
import org.mr0btain.ticketman.ui.composables.ticket.TicketPriorityDropDown
import org.mr0btain.ticketman.ui.composables.ticket.TicketStatusDropDown
import org.mr0btain.ticketman.ui.dialog.EditNoteDialog
import kotlin.uuid.ExperimentalUuidApi

// TODO: Add ability to trigger new customer

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TicketDialog(
    modifier: Modifier = Modifier,
    seed: TicketSeed,
    isVisible: Boolean,
    onCustomerSearchQuery: suspend (String) -> List<SearchResult>,
    onUserSearchQuery: suspend (String) -> List<SearchResult>,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        // For deleting item dialog
        var showConfirmDeleteScreen by remember { mutableStateOf(false) }
        var itemForDelete by remember { mutableStateOf(DataType.NONE) }

        // For editing or adding notes dialog
        var showNoteDialog by remember { mutableStateOf(false) }
        var noteSeedData by remember { mutableStateOf<NoteData?>(null) }

        // For customer search bar
        var isCustomerSearchEnabled by remember { mutableStateOf(false) }

        // For employee search bar
        var isUserSearchEnabled by remember { mutableStateOf(false) }

        // Edit or add update dialog
        EditNoteDialog(
            seed = noteSeedData,
            onConfirm = { result ->
                val foundNote = notes.firstOrNull { it.note.identifier == result.note.identifier }

                // Check if note exists, if so remove old data
                if (foundNote != null) notes.remove(foundNote)

                notes.add(result)
                noteSeedData = null
                showNoteDialog = false
            },
            isVisible = showNoteDialog,
            onDismissRequest = {
                noteSeedData = null
                showNoteDialog = false
            }
        )

        // Delete item dialog
        if (showConfirmDeleteScreen) {
            /*
            ConfirmDeleteDialog(
                label = when (itemForDelete) {
                    SubItemType.UPDATE -> notes[itemUpForDeleteIndex].note.author + "'s Update"
                    else -> ""
                },
                isDarkMode = isDarkMode,
                mainItemType = MainItemType.CUSTOMER,
                onDismissRequest = { confirm ->
                    if (confirm && itemForDelete != SubItemType.NONE) {
                        when (itemForDelete) {
                            SubItemType.UPDATE -> {
                                // Remove item from list
                                notes.removeAt(itemUpForDeleteIndex)
                            }

                            // For everything else do nothing
                            else -> {}
                        }
                    }

                    showConfirmDeleteScreen = false
                },
            )
             */
        }

        Dialog(
            onDismissRequest = { onDismiss() }
        ) {
            Surface(
                modifier = modifier
                    .height(600.dp)
                    .width(600.dp),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp
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
                            text = if (seed.isNew) "Add New Ticket: ${seed.ticket?.identifier}" else "Edit Ticket: ${seed.ticket?.identifier}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            maxLines = 1
                        )
                    }

                    HorizontalDivider(Modifier.padding(4.dp))

                    Spacer(Modifier.padding(8.dp))

                    // -------------------------------------------- Content ----------------------------------------------------
                    PlatformLazyColumn(
                        modifier = Modifier
                            .width(500.dp)
                            .height(400.dp),
                        isVisible = true
                    ) {
                        item {
                            // ------------------------- Customer -------------------------------
                            Text(
                                text = "Customer",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Customer search bar
                            // TODO: Maybe make this a regular text field then add and editable button
                            SearchBar(
                                modifier = Modifier.padding(start = 8.dp),
                                initialText = customer.toString(),
                                onClick = { result -> customer = result.identity() },
                                onQuery = onCustomerSearchQuery,
                                enabled = isCustomerSearchEnabled
                            )

                            Spacer(Modifier.padding(8.dp))

                            // ------------------------- Assigned user -------------------------------
                            Text(
                                text = "Assigned User",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            // User search bar
                            // TODO: Maybe make this a regular text field then add and editable button
                            SearchBar(
                                modifier = Modifier.padding(start = 8.dp),
                                initialText = user.toString(),
                                onClick = { result -> user = result.identity() },
                                onQuery = onUserSearchQuery,
                                enabled = isUserSearchEnabled
                            )

                            Spacer(Modifier.padding(8.dp))

                            // ------------------------- Device -------------------------------
                            Text(
                                text = "Device",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            // TODO: Maybe make this drop down with list of things?
                            DialogTextRow(
                                modifier = Modifier,
                                firstLabel = "Manufacturer",
                                firstValue = deviceManufacturer,
                                onFirstValueChange = { deviceManufacturer = it },
                                secondLabel = "Model",
                                secondValue = deviceModel,
                                onSecondValueChange = { deviceModel = it }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Status -------------------------------
                            Text(
                                text = "Status",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            TicketStatusDropDown(
                                modifier = Modifier.width(350.dp),
                                status = status,
                                onClick = { status = it }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Priority -------------------------------
                            Text(
                                text = "Priority",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )

                            TicketPriorityDropDown(
                                modifier = Modifier.width(350.dp),
                                priority = priority,
                                onClick = { priority = it }
                            )

                            Spacer(Modifier.padding(4.dp))

                            // ------------------------- Updates -------------------------------
                            NoteBlock(
                                label = "Updates",
                                noteData = notes,
                                editable = true,
                                onEditClick = { id ->
                                    noteSeedData = notes.find { it.note.identifier == id }
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
                        onConfirmClick = { onContinue() }
                    )
                }
            }
        }
    }
}