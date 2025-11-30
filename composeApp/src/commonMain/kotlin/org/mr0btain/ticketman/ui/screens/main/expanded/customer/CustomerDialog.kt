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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.PersonName
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
import java.util.*

// TODO: Add ability to assign already existing tickets to customer.
// TODO: Add ability to edit note
@Composable
fun CustomerDialog(
    modifier: Modifier = Modifier,
    seedData: CustomerData?,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: (CustomerData) -> Unit,
    onImageClick: (UUID) -> Unit,
    isDarkMode: Boolean,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    if (isVisible && seedData != null) {
        // Name
        var firstName by remember(seedData) { mutableStateOf(seedData.customer.name.firstName) }
        var lastName by remember(seedData) { mutableStateOf(seedData.customer.name.lastName) }

        // Address
        var street by remember(seedData) { mutableStateOf(seedData.customer.address.street) }
        var city by remember(seedData) { mutableStateOf(seedData.customer.address.city) }
        var state by remember(seedData) { mutableStateOf(seedData.customer.address.state) }
        var zip by remember(seedData) { mutableStateOf(seedData.customer.address.zip) }

        // Mutable lists
        val email = remember(seedData) { seedData.customer.contactInfo.email.toMutableStateList() }
        val phone = remember(seedData) { seedData.customer.contactInfo.phone.toMutableStateList() }
        val notes =  remember(seedData) { seedData.notes.toMutableStateList() }
        val tickets = remember(seedData) { seedData.tickets.toMutableStateList() }

        // For deleting item dialog
        var showConfirmDeleteScreen by remember { mutableStateOf(false) }

        // TODO: Could combine this with adding an item, and have one when statement
        var dataType by remember { mutableStateOf(DataType.NONE) }

        // Might be a better way of doing this, for now just send it like this
        var itemToDelete by remember { mutableStateOf<Any?>(null) }

        // For adding a basic sub item
        var itemToAdd by remember { mutableStateOf(DataType.NONE) }
        var showAddBasicSubItemDialog by remember { mutableStateOf(false) }

        // For adding note dialog
        var showNoteDialog by remember { mutableStateOf(false) }
        var noteSeedData by remember { mutableStateOf<NoteData?>(null) }

        // Add Note dialog
        EditNoteDialog(
            note = noteSeedData.note,
            onConfirm = { result ->
                scope.launch {
                    val foundNote = notes.firstOrNull { it.note.identifier == result.note.identifier }

                    // Check if note exists, if so remove old data
                    if (foundNote != null) notes.remove(foundNote)

                    // Insert new note
                    notes.add(result)

                    noteSeedData = null

                    showNoteDialog = false
                }
            },
            isVisible = showNoteDialog,
            onDismissRequest = {
                noteSeedData = null
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
                            email.add(item)
                        }
                        DataType.PHONE -> {
                            // Add item to list
                            phone.add(item)
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
                when (dataType) {
                    DataType.TICKET -> {
                        (itemToDelete as? UUID)?.let { identifier ->
                            tickets.removeIf { it.ticket.identifier == identifier }
                        }
                    }

                    DataType.EMAIL -> {
                        (itemToDelete as? String)?.let { item ->
                            email.removeIf { it == item }
                        }
                    }

                    DataType.PHONE -> {
                        (itemToDelete as? String)?.let { item ->
                            phone.removeIf { it == item }
                        }
                    }

                    DataType.NOTE -> {
                        (itemToDelete as? UUID)?.let { identifier ->
                            notes.removeIf { it.note.identifier == identifier }
                        }
                    }

                    else -> {}
                }

                dataType = DataType.NONE
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
                            text = if (seedData.customer.name.toString().isEmpty()) {
                                "Add Customer"
                            } else "Edit Customer: ${seedData.customer.name.toString()}",
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
                                firstValue = firstName,
                                onFirstValueChange = { firstName = it },
                                secondLabel = "Last",
                                secondValue = lastName,
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
                                    firstValue = street,
                                    onFirstValueChange = { street = it },
                                    secondLabel = "City",
                                    secondValue = city,
                                    onSecondValueChange = { city = it }
                                )

                                DialogTextRow(
                                    modifier = Modifier,
                                    firstLabel = "State",
                                    firstValue = state,
                                    onFirstValueChange = { state = it },
                                    secondLabel = "Zip",
                                    secondValue = zip,
                                    onSecondValueChange = { zip = it }
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
                                items = email,
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
                                items = phone,
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
                                tickets = tickets,
                                onDeleteClick = { item ->
                                    // Set data type so we know what were looking to delete
                                    dataType = DataType.TICKET

                                    // Set item data so we know which item to delete
                                    itemToDelete = item

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
                                noteSeedData = NoteData()
                                showNoteDialog = true
                            }

                            Spacer(Modifier.padding(4.dp))

                            NoteBlock(
                                noteData = notes,
                                editable = true,
                                onImageClick = onImageClick,
                                onEditClick = { id ->
                                    noteSeedData = notes.find { it.note.identifier == id }

                                    showNoteDialog = true
                                }
                            )

                            Spacer(Modifier.padding(4.dp))

                        }

                    }

                    Spacer(Modifier.padding(8.dp))

                    // -------------------------------------------- Confirm Cancel buttons ----------------------------------------------------
                    ConfirmCancelButtons(
                        modifier = Modifier.fillMaxWidth(),
                        onCancelClick = { onDismiss() },
                        onConfirmClick = {
                            onContinue(
                                CustomerData(
                                    customer = Customer(
                                        identifier = seedData.customer.identifier,
                                        name = PersonName(
                                            firstName.lowercase().replaceFirstChar { it.uppercase() },
                                            lastName.lowercase().replaceFirstChar { it.uppercase() }
                                        ),
                                        address = Address(
                                            street.lowercase().replaceFirstChar { it.uppercase() },
                                            city.lowercase().replaceFirstChar { it.uppercase() },
                                            state.lowercase().replaceFirstChar { it.uppercase() },
                                            zip
                                        ),
                                        contactInfo = ContactInfo(email, phone)
                                    ),
                                    tickets = tickets,
                                    notes = notes
                                )
                            )

                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}