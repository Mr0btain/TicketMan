package org.mr0btain.ticketman.ui.screens.main.components.customer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.shared.DialogDismissReason
import org.mr0btain.ticketman.data.shared.MainItemType
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.shared.SubItemType
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.dialog.AddBasicSubItemDialog
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.NoteDialog
import org.mr0btain.ticketman.ui.screens.main.components.ConfirmOrCancelButtonRow
import org.mr0btain.ticketman.ui.screens.main.components.DialogTextRow
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit_2
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.math.ceil

// TODO: Add checks to make sure its not blank
// TODO: Add ability to assign already existing tickets to customer.
// TODO: Add ability to edit note
// TODO: Find better way to remove items based off more than just an index?
@Composable
fun CustomerDialog(
    modifier: Modifier = Modifier,
    seedData: CustomerData = CustomerData(),
    ticketList: SnapshotStateList<String>,
    onDismiss: (CustomerData?, DialogDismissReason) -> Unit,
    onAddNewTicket: (String) -> Unit,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean
) {
    // Name
    var firstName by remember(seedData) { mutableStateOf(seedData.info.name.firstName) }
    var lastName by remember(seedData) { mutableStateOf(seedData.info.name.lastName) }

    // Address
    var street by remember(seedData) { mutableStateOf(seedData.info.address.street) }
    var city by remember(seedData) { mutableStateOf(seedData.info.address.city) }
    var state by remember(seedData) { mutableStateOf(seedData.info.address.state) }
    var zip by remember(seedData) { mutableStateOf(seedData.info.address.zip) }

    // Mutable lists
    val email = remember(seedData) { seedData.info.contactInfo.email.toMutableStateList() }
    val phone = remember(seedData) { seedData.info.contactInfo.phone.toMutableStateList() }
    val notes = remember(seedData) { seedData.notes.toMutableStateList() }

    // For deleting item dialog
    var showConfirmDeleteScreen by remember { mutableStateOf(false) }
    var itemUpForDeleteIndex by remember { mutableIntStateOf(0) }
    var itemForDelete by remember { mutableStateOf(SubItemType.NONE) }

    // For adding note dialog
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteSeedData by remember { mutableStateOf(Note()) }

    // For adding a basic sub item
    var itemToAdd by remember { mutableStateOf(SubItemType.NONE) }
    var showAddBasicSubItemDialog by remember { mutableStateOf(false) }

    // Add Note dialog
    if (showAddNoteDialog) {
        NoteDialog(
            isDarkMode = isDarkMode,
            seedData = noteSeedData,
            label = "Note",
            onConfirm = { item ->
                // Add to out list
                notes.add(item)
            },
            onDismissRequest = {
                // Reset seed data
                noteSeedData = Note()

                // Close dialog
                showAddNoteDialog = false
            }
        )
    }

    // Add basic item dialog
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
    if (showConfirmDeleteScreen) {
        ConfirmDeleteDialog(
            itemName = when (itemForDelete) {
                SubItemType.TICKET -> itemForDelete.toString()
                SubItemType.EMAIL -> email[itemUpForDeleteIndex]
                SubItemType.PHONE -> phone[itemUpForDeleteIndex]
                SubItemType.NOTE -> notes[itemUpForDeleteIndex].author + "'s Note"
                else -> ""
            },
            isDarkMode = isDarkMode,
            mainItemType = MainItemType.CUSTOMER,
            onDismissRequest = { confirm ->
                if (confirm && itemForDelete != SubItemType.NONE) {
                    when (itemForDelete) {
                        SubItemType.TICKET -> {
                            // Remove item from list
                            ticketList.removeAt(itemUpForDeleteIndex)
                        }

                        SubItemType.EMAIL -> {
                            // Remove item from list
                            email.removeAt(itemUpForDeleteIndex)
                        }

                        SubItemType.PHONE -> {
                            // Remove item from list
                            phone.removeAt(itemUpForDeleteIndex)
                        }

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
                // -------------------------------------------- Header ----------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = if (seedData.info.name.firstName.isEmpty() && seedData.info.name.lastName.isEmpty()) {
                            "Add Customer: ${seedData.info.identifier}"
                        } else "Edit Customer: ${seedData.info.name.firstName} ${seedData.info.name.lastName}",
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
                            itemForDelete = SubItemType.EMAIL
                            itemUpForDeleteIndex = item
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
                            itemForDelete = SubItemType.PHONE
                            itemUpForDeleteIndex = item
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
                            onClick = { onAddNewTicket(seedData.identifier) }
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
                        items = ticketList.map { it.toString() },
                        onDeleteClick = { item ->
                            itemForDelete = SubItemType.TICKET
                            itemUpForDeleteIndex = item
                            showConfirmDeleteScreen = true
                        },
                        onTicketClick = {}
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

                    CustomerDialogNoteList(
                        notes = notes,
                        onDeleteClick = { item ->
                            itemForDelete = SubItemType.NOTE
                            itemUpForDeleteIndex = item
                            showConfirmDeleteScreen = true
                        }
                    )

                    Spacer(Modifier.padding(4.dp))
                }

                Spacer(Modifier.padding(8.dp))

                // -------------------------------------------- Confirm Cancel buttons ----------------------------------------------------
                ConfirmOrCancelButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                    onCancelClick = { onDismiss(null, DialogDismissReason.CANCEL) },
                    onConfirmClick = {
                        // Call dismiss with continue
                        onDismiss(
                            CustomerData(
                                info = CustomerInfo(
                                    identifier = seedData.info.identifier,
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
                                    time = seedData.info.time
                                ),
                                tickets = ticketList,
                                notes = notes
                            ),
                            DialogDismissReason.CONTINUE
                        )
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
    onEditClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        items.forEachIndexed { index, email ->
            CustomChip(
                label = items[index],
                onEditClick =  { onEditClick(index) },
                onDeleteClick =  { onDeleteClick(index) }
            )

            Spacer(Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun CustomerDialogPhoneList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onEditClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    val padding = 4.dp
    var row = 1

    Column {
        for (i in 1..ceil(items.size.toFloat() / 3f).toInt()) {
            val index = i * row

            // CHeck if there is even items in this row
            if (items.getOrElse(index - 1) { "" }.isNotEmpty()) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomChip(
                        label = items[index - 1],
                        onEditClick = { onEditClick(index - 1) },
                        onDeleteClick = { onDeleteClick(index - 1) }
                    )

                    Spacer(Modifier.padding(padding))

                    if (items.getOrElse(index) { "" }.isNotEmpty()) {
                        CustomChip(
                            label = items[index],
                            onEditClick = { onEditClick(index) },
                            onDeleteClick = { onDeleteClick(index) }
                        )
                    }

                    Spacer(Modifier.padding(padding))

                    if (items.getOrElse(index + 1) { "" }.isNotEmpty()) {
                        CustomChip(
                            label = items[index + 1],
                            onEditClick = { onEditClick(index + 1) },
                            onDeleteClick = { onDeleteClick(index + 1) }
                        )
                    }
                }
            }

            row++
        }
    }
}

// TODO: Convert to lazy row
@Composable
private fun CustomerDialogTicketList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onTicketClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    val padding = 4.dp
    var row = 1

    Column {
        for (i in 1..ceil(items.size.toFloat() / 5f).toInt()) {
            val index = i * row

            // CHeck if there is even items in this row
            if (items.getOrElse(index - 1) { "" }.isNotEmpty()) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomInputChip(
                        label = items[index - 1],
                        onClick = { onTicketClick(index - 1) },
                        onDeleteClick = { onDeleteClick(index - 1) }
                    )

                    Spacer(Modifier.padding(padding))

                    if (items.getOrElse(index) { "" }.isNotEmpty()) {
                        CustomInputChip(
                            label = items[index],
                            onClick = { onTicketClick(index) },
                            onDeleteClick = { onDeleteClick(index) }
                        )
                    }

                    Spacer(Modifier.padding(padding))

                    if (items.getOrElse(index + 1) { "" }.isNotEmpty()) {
                        CustomInputChip(
                            label = items[index + 1],
                            onClick = { onTicketClick(index + 1) },
                            onDeleteClick = { onDeleteClick(index + 1) }
                        )
                    }

                    Spacer(Modifier.padding(padding))

                    if (items.getOrElse(index + 2) { "" }.isNotEmpty()) {
                        CustomInputChip(
                            label = items[index + 2],
                            onClick = { onTicketClick(index + 2) },
                            onDeleteClick = { onDeleteClick(index + 2) }
                        )
                    }

                    Spacer(Modifier.padding(padding))

                    if (items.getOrElse(index + 3, { "" }).isNotEmpty()) {
                        CustomInputChip(
                            label = items[index + 3],
                            onClick = { onTicketClick(index + 3) },
                            onDeleteClick = { onDeleteClick(index + 3) }
                        )
                    }
                }
            }

            row++
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
                        text = item.text,
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