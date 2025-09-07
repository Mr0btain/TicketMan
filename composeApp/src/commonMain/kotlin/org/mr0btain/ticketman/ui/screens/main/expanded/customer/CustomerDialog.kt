package org.mr0btain.ticketman.ui.screens.main.expanded.customer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.shared.*
import org.mr0btain.ticketman.data.ticket.toNameString
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.dialog.AddBasicSubItemDialog
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.EditNoteDialog
import org.mr0btain.ticketman.ui.screens.main.components.ConfirmOrCancelButtonRow
import org.mr0btain.ticketman.ui.screens.main.components.DialogTextRow
import org.mr0btain.ticketman.ui.screens.main.components.NewNotesBlock
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit_2
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.math.min
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Add ability to assign already existing tickets to customer.
// TODO: Add ability to edit note
@OptIn(ExperimentalUuidApi::class)
@Composable
fun CustomerDialog(
    modifier: Modifier = Modifier,
    seedData: CustomerData,
    onDismiss: () -> Unit,
    onContinue: (CustomerData) -> Unit,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean,
    scope: CoroutineScope = rememberCoroutineScope()
) {
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

    // For adding note dialog
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteSeedData by remember { mutableStateOf<NoteData?>(null) }

    // For adding a basic sub item
    var itemToAdd by remember { mutableStateOf(SubItemType.NONE) }
    var showAddBasicSubItemDialog by remember { mutableStateOf(false) }

    // Add Note dialog
    // TODO: Redo this to modern notedata with images and such
    if (showNoteDialog) {
        noteSeedData?.let { seedData ->
            EditNoteDialog(
                noteData = seedData,
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
                onDismissRequest = {
                    noteSeedData = null
                    showNoteDialog = false
                                   },
                isDarkMode = isDarkMode
            )
        }
    }

    // Add basic item dialog
    // TODO: Redo this
    if (showAddBasicSubItemDialog) {
        AddBasicSubItemDialog(
            isDarkMode = isDarkMode,
            subItemType = itemToAdd,
            onConfirm = { item ->
                if (item.isNotBlank()) {
                    when (itemToAdd) {
                        SubItemType.EMAIL -> {
                            // Add item to list
                            email.add(item)
                        }
                        SubItemType.PHONE -> {
                            // Add item to list
                            phone.add(item)
                        }
                        else -> {}
                    }
                }
            },
            onDismissRequest = {
                showAddBasicSubItemDialog = false
                itemToAdd = SubItemType.NONE
            }
        )
    }

    // Delete item dialog
    if (showConfirmDeleteScreen && dataType != DataType.NONE) {
        ConfirmDeleteDialog(
            dataType = dataType,
            onContinue = {
                when (dataType) {
                    DataType.TICKET -> {
                        (itemToDelete as? Uuid)?.let { identifier ->
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
                        (itemToDelete as? Uuid)?.let { identifier ->
                            notes.removeIf { it.note.identifier == identifier }
                        }
                    }

                    else -> {}
                }
            },
            onDismiss = {
                dataType = DataType.NONE
                showConfirmDeleteScreen = false
            }
        )
    }

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
                        text = if (seedData.customer.name.toStringName().isEmpty()) {
                            "Add Customer"
                        } else "Edit Customer: ${seedData.customer.name.toStringName()}",
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
                    columnModifier = Modifier.width(500.dp),
                    scrollBarOnRight = scrollBarsOnRight,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    isVisible = true
                ) {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Email",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        IconButton(
                            modifier = Modifier.size(26.dp),
                            onClick = {
                                itemToAdd = SubItemType.EMAIL
                                showAddBasicSubItemDialog = true
                            }
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(20.dp),
                                painter = painterResource(Res.drawable.plus),
                                contentDescription = "Add Email Address"
                            )
                        }
                    }

                    // Email list
                    CustomerDialogEmailList(
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Phone",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        IconButton(
                            modifier = Modifier.size(26.dp),
                            onClick = {
                                itemToAdd = SubItemType.PHONE
                                showAddBasicSubItemDialog = true
                            }
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(20.dp),
                                painter = painterResource(Res.drawable.plus),
                                contentDescription = "Add Phone Number"
                            )
                        }
                    }

                    // Phone List
                    CustomerDialogPhoneList(
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tickets",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        IconButton(
                            modifier = Modifier.size(26.dp),
                            onClick = {
                                // TODO
                            }
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(20.dp),
                                painter = painterResource(Res.drawable.plus),
                                contentDescription = "Add Ticket"
                            )
                        }
                    }

                    Spacer(Modifier.padding(4.dp))

                    // ------------------------- Ticket list -------------------------------
                    CustomerDialogTicketList(
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
                            onClick = {
                                noteSeedData = NoteData()
                                showNoteDialog = true
                            }
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

                    NewNotesBlock(
                        noteData = notes,
                        onImageClick = {
                            // TODO: Not yet implemented
                        }
                    )

                    /*
                    CustomerDialogNoteList(
                        notes = notes,
                        onDeleteClick = { item ->
                            itemForDelete = SubItemType.NOTE
                            itemUpForDeleteIndex = item
                            showConfirmDeleteScreen = true
                        }
                    )
                     */

                    Spacer(Modifier.padding(4.dp))
                }

                Spacer(Modifier.padding(8.dp))

                // -------------------------------------------- Confirm Cancel buttons ----------------------------------------------------
                ConfirmOrCancelButtonRow(
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
                                    contactInfo = ContactInfo(email, phone),
                                    time = seedData.customer.time
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

/**
 * For displaying emails on dialog. Only one per line as emails that get very long will not be displayed for whatever reason
 */

@Composable
private fun CustomerDialogEmailList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        items.forEachIndexed { index, email ->
            CustomChip(
                label = items[index],
                onEditClick =  { onEditClick(email) },
                onDeleteClick =  { onDeleteClick(email) }
            )

            Spacer(Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun CustomerDialogPhoneList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column {
        for (i in 0..(items.size / 3) + 1) {
            val startIndex = i * 3

            // CHeck if there is even items in this row
            if (items.size > startIndex) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.subList(startIndex, min(startIndex + 3, items.size)).forEach { phoneNumber ->
                        CustomChip(
                            label = phoneNumber,
                            onEditClick = { onEditClick(phoneNumber) },
                            onDeleteClick = { onDeleteClick(phoneNumber) }
                        )

                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

// TODO: Convert to lazy row
// TODO: Add Note section
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun CustomerDialogTicketList(
    modifier: Modifier = Modifier,
    tickets: List<TicketData>,
    onTicketClick: (Uuid) -> Unit,
    onDeleteClick: (Uuid) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        tickets.forEach { (ticket, notes) ->
            CustomInputChip(
                label = ticket.device.toNameString(),
                onClick = { onTicketClick(ticket.identifier) },
                onDeleteClick = { onDeleteClick(ticket.identifier) }
            )

            Spacer(Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun CustomerDialogNoteList(
    modifier: Modifier = Modifier,
    notes: List<Note>,
    onDeleteClick: (Int) -> Unit
) {
    Column {
        notes.forEachIndexed { index, item ->
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${item.author}:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.padding(2.dp))

                    Text(
                        text = item.message,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.padding(2.dp))

                Box(
                    modifier = Modifier
                        .size(InputChipDefaults.AvatarSize + 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .clickable(
                            onClick = { onDeleteClick(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(InputChipDefaults.AvatarSize),
                        painter = painterResource(Res.drawable.x),
                        contentDescription = "Remove Item",
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomInputChip(
    label: String,
    onClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    InputChip(
        label = { Text(label) },
        onClick = { onClick(label)},
        selected = false,
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        onClick = { onDeleteClick(label) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    painter = painterResource(Res.drawable.x),
                    contentDescription = "Remove Item",
                )
            }
        }
    )
}

@Composable
private fun CustomChip(
    modifier: Modifier = Modifier,
    label: String,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Box(
        modifier = modifier
            .height(InputChipDefaults.Height)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(InputChipDefaults.AvatarSize)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        onClick = { onEditClick(label) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(InputChipDefaults.AvatarSize - 4.dp),
                    painter = painterResource(Res.drawable.edit_2),
                    contentDescription = "Remove Email",
                )
            }

            // Label
            Text(
                modifier = Modifier.padding(4.dp),
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Trailing icon
            Box(
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        onClick = { onDeleteClick(label) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    painter = painterResource(Res.drawable.x),
                    contentDescription = "Remove Email",
                )
            }
        }
    }
}