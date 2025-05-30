package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.Address
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.CustomerName
import org.mr0btain.ticketman.components.data.components.MainItemType
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.SubItemType
import org.mr0btain.ticketman.ui.dialog.AddBasicSubItemDialog
import org.mr0btain.ticketman.ui.dialog.AddNoteDialog
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit_2
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.math.ceil

// TODO: Maybe make specific functions for updating database stuff so we don't have to pass the entire item only id and change

@Composable
fun EditCustomerDataDialog(
    modifier: Modifier = Modifier,
    customerData: CustomerData,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    isDarkMode: Boolean
) {
    val identifier = customerData.info.id
    // Use mutable state here to make sure the updates are reflected properly with state change as updating view model sometimes
    // doesn't trigger change to dialog but for some reason this works
    var data by remember { mutableStateOf(customerData) }

    // Name
    val firstName = remember { mutableStateOf(data.info.name.firstName) }
    val lastName = remember { mutableStateOf(data.info.name.lastName) }

    // Address
    val street = remember { mutableStateOf(data.info.address.street) }
    val city = remember { mutableStateOf(data.info.address.city) }
    val state = remember { mutableStateOf(data.info.address.state) }
    val zip = remember { mutableStateOf(data.info.address.zip) }

    // Mutable lists
    val email = remember { data.info.contactInfo.email.toMutableStateList() }
    val phone = remember { data.info.contactInfo.phone.toMutableStateList() }
    val tickets = remember { data.tickets.toMutableStateList() }
    val notes = remember { data.notes.toMutableStateList() }

    // For deleting item dialog
    var showConfirmDeleteScreen by remember { mutableStateOf(false) }
    var itemUpForDeleteIndex by remember { mutableIntStateOf(0) }
    var itemForDelete by remember { mutableStateOf(SubItemType.NONE) }

    // For adding note dialog
    var showAddNoteDialog by remember { mutableStateOf(false) }

    // For adding a basic sub item
    var itemToAdd by remember { mutableStateOf(SubItemType.NONE) }
    var showAddBasicSubItemDialog by remember { mutableStateOf(false) }

    // Add Note dialog
    if (showAddNoteDialog) {
        AddNoteDialog(
            isDarkMode = isDarkMode,
            onConfirm = { item ->
                // Add to out list
                notes.add(item)

                // Add to database
                viewModel.updateCustomerNotes(identifier, notes)
                        },
            onDismissRequest = { showAddNoteDialog = false }
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

                            // Update database
                            viewModel.updateCustomerContact(identifier, data.info.contactInfo.copy(email = email))
                        }
                        SubItemType.PHONE -> {
                            // Add item to list
                            phone.add(item)

                            // Update database
                            viewModel.updateCustomerContact(identifier, data.info.contactInfo.copy(phone = phone))
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
                SubItemType.TICKET -> tickets[itemUpForDeleteIndex]
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
                            tickets.removeAt(itemUpForDeleteIndex)

                            // Update database
                            viewModel.updateCustomerTickets(identifier, tickets)
                        }

                        SubItemType.EMAIL -> {
                            // Remove item from list
                            email.removeAt(itemUpForDeleteIndex)

                            // Update database
                            viewModel.updateCustomerContact(identifier, data.info.contactInfo.copy(email = email))
                        }

                        SubItemType.PHONE -> {
                            // Remove item from list
                            phone.removeAt(itemUpForDeleteIndex)

                            // Update database
                            viewModel.updateCustomerContact(identifier, data.info.contactInfo.copy(phone = phone))
                        }

                        SubItemType.NOTE -> {
                            // Remove item from list
                            notes.removeAt(itemUpForDeleteIndex)

                            // Update database
                            viewModel.updateCustomerNotes(identifier, notes)
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
            onDismiss()
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
                        text = "Edit Customer: ${data.info.id}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(4.dp),
                    color = if (isDarkMode) MaterialTheme.colorScheme.onSurface else DividerDefaults.color
                )

                Spacer(Modifier.padding(8.dp))

                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    item {
                        // Name section
                        Text(
                            text = "Name",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        CustomerDialogTextRow(
                            modifier = Modifier,
                            firstLabel = "First",
                            firstValue = firstName,
                            secondLabel = "Last",
                            secondValue = lastName
                        )

                        Spacer(Modifier.padding(8.dp))

                        // Address section
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
                            CustomerDialogTextRow(
                                modifier = Modifier,
                                firstLabel = "Street",
                                firstValue = street,
                                secondLabel = "City",
                                secondValue = city
                            )

                            CustomerDialogTextRow(
                                modifier = Modifier,
                                firstLabel = "State",
                                firstValue = state,
                                secondLabel = "Zip",
                                secondValue = zip,
                            )
                        }

                        Spacer(Modifier.padding(8.dp))

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

                        // TODO: Logic for delete/ confirm screen dialog
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

                        // Ticket list
                        CustomerDialogTicketList(
                            items = tickets,
                            onDeleteClick = { item ->
                                itemForDelete = SubItemType.TICKET
                                itemUpForDeleteIndex = item
                                showConfirmDeleteScreen = true
                            },
                            onTicketClick = {}
                        )

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
                }

                Spacer(Modifier.padding(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onDismiss() },
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
                            // Check remaining data
                            checkDataOnClose(
                                oldData = data,
                                newName = CustomerName(firstName.value, lastName.value),
                                newAddress = Address(street.value, city.value, state.value, zip.value),
                                viewModel = viewModel
                            )

                            // Call dismiss
                            onDismiss()
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

@Composable
fun AddCustomerDataDialog(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    isDarkMode: Boolean
) {
    // Get next id
    val identifier = remember { viewModel.getNextCustomerId() }

    // Name
    val firstName = remember { mutableStateOf("") }
    val lastName = remember { mutableStateOf("") }

    // Address
    val street = remember { mutableStateOf("") }
    val city = remember { mutableStateOf("") }
    val state = remember { mutableStateOf("") }
    val zip = remember { mutableStateOf("") }

    // Mutable lists
    val email = remember { mutableStateListOf<String>() }
    val phone = remember { mutableStateListOf<String>() }
    val tickets = remember { mutableStateListOf<String>() }
    val notes = remember { mutableStateListOf<Note>() }

    // For deleting item dialog
    var showConfirmDeleteScreen by remember { mutableStateOf(false) }
    var itemUpForDeleteIndex by remember { mutableIntStateOf(0) }
    var itemForDelete by remember { mutableStateOf(SubItemType.NONE) }

    // For adding note dialog
    var showAddNoteDialog by remember { mutableStateOf(false) }

    // For adding a basic sub item
    var itemToAdd by remember { mutableStateOf(SubItemType.NONE) }
    var showAddBasicSubItemDialog by remember { mutableStateOf(false) }

    // Add Note dialog
    if (showAddNoteDialog) {
        AddNoteDialog(
            isDarkMode = isDarkMode,
            onConfirm = { item ->
                // Add to out list
                notes.add(item)
            },
            onDismissRequest = { showAddNoteDialog = false }
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
                SubItemType.TICKET -> tickets[itemUpForDeleteIndex]
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
                            tickets.removeAt(itemUpForDeleteIndex)
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
            onDismiss()
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
                        text = "Add New Customer: $identifier",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(4.dp),
                    color = if (isDarkMode) MaterialTheme.colorScheme.onSurface else DividerDefaults.color
                )

                Spacer(Modifier.padding(8.dp))

                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    item {
                        // Name section
                        Text(
                            text = "Name",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        CustomerDialogTextRow(
                            modifier = Modifier,
                            firstLabel = "First",
                            firstValue = firstName,
                            secondLabel = "Last",
                            secondValue = lastName
                        )

                        Spacer(Modifier.padding(8.dp))

                        // Address section
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
                            CustomerDialogTextRow(
                                modifier = Modifier,
                                firstLabel = "Street",
                                firstValue = street,
                                secondLabel = "City",
                                secondValue = city
                            )

                            CustomerDialogTextRow(
                                modifier = Modifier,
                                firstLabel = "State",
                                firstValue = state,
                                secondLabel = "Zip",
                                secondValue = zip,
                            )
                        }

                        Spacer(Modifier.padding(8.dp))

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

                        // TODO: Logic for delete/ confirm screen dialog
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

                        // Ticket list
                        CustomerDialogTicketList(
                            items = tickets,
                            onDeleteClick = { item ->
                                itemForDelete = SubItemType.TICKET
                                itemUpForDeleteIndex = item
                                showConfirmDeleteScreen = true
                            },
                            onTicketClick = {}
                        )

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
                }

                Spacer(Modifier.padding(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onDismiss() },
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
                            // Add the customer to the database
                            viewModel.addCustomer(
                                CustomerData(
                                    info = CustomerInfo(
                                        id = identifier,
                                        name = CustomerName(firstName.value, lastName.value),
                                        address = Address(street.value, city.value, state.value, zip.value),
                                        contactInfo = ContactInfo(email, phone)
                                    ),
                                    tickets = tickets,
                                    notes = notes
                                )
                            )

                            // Call dismiss
                            onDismiss()
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

@Composable
private fun CustomerDialogTextRow(
    modifier: Modifier = Modifier,
    firstLabel: String,
    firstValue: MutableState<String>,
    secondLabel: String,
    secondValue: MutableState<String>
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(firstLabel)
            },
            value = firstValue.value,
            onValueChange = { firstValue.value = it },
            singleLine = true
        )

        Spacer(Modifier.padding(8.dp))

        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(secondLabel)
            },
            value = secondValue.value,
            onValueChange = { secondValue.value = it },
            singleLine = true
        )
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
    var row = 1

    Column {
        for (i in 1..ceil(items.size.toFloat() / 3f).toInt()) {
            val index = i * row

            // CHeck if there is even items in this row
            if (items.getOrElse(index - 1) { "" }.isNotEmpty()) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomChip(
                        label = items[index - 1],
                        onEditClick = { onEditClick(index - 1) },
                        onDeleteClick = { onDeleteClick(index - 1) }
                    )

                    if (items.getOrElse(index) { "" }.isNotEmpty()) {
                        CustomChip(
                            label = items[index],
                            onEditClick = { onEditClick(index) },
                            onDeleteClick = { onDeleteClick(index) }
                        )
                    }

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

@Composable
private fun CustomerDialogTicketList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onTicketClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    var row = 1

    Column {
        for (i in 1..ceil(items.size.toFloat() / 5f).toInt()) {
            val index = i * row

            // CHeck if there is even items in this row
            if (items.getOrElse(index - 1) { "" }.isNotEmpty()) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomInputChip(
                        label = items[index - 1],
                        onClick = { onTicketClick(index - 1) },
                        onDeleteClick = { onDeleteClick(index - 1) }
                    )

                    if (items.getOrElse(index) { "" }.isNotEmpty()) {
                        CustomInputChip(
                            label = items[index],
                            onClick = { onTicketClick(index) },
                            onDeleteClick = { onDeleteClick(index) }
                        )
                    }

                    if (items.getOrElse(index + 1) { "" }.isNotEmpty()) {
                        CustomInputChip(
                            label = items[index + 1],
                            onClick = { onTicketClick(index + 1) },
                            onDeleteClick = { onDeleteClick(index + 1) }
                        )
                    }

                    if (items.getOrElse(index + 2) { "" }.isNotEmpty()) {
                        CustomInputChip(
                            label = items[index + 2],
                            onClick = { onTicketClick(index + 2) },
                            onDeleteClick = { onDeleteClick(index + 2) }
                        )
                    }

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
                    .padding(8.dp),
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

/**
 * Compares values in data and updates as needed some things are drawn out because of issue with comparing snapshot state
 */

private fun checkDataOnClose(
    oldData: CustomerData,
    newName: CustomerName,
    newAddress: Address,
    viewModel: MainViewModel
) {
    // Check name
    when {
        oldData.info.name.firstName != newName.firstName -> viewModel.updateCustomerName(oldData.info.id, newName)
        oldData.info.name.lastName != newName.lastName -> viewModel.updateCustomerName(oldData.info.id, newName)
    }

    // Check address
    when {
        oldData.info.address.state != newAddress.state -> viewModel.updateCustomerAddress(oldData.info.id, newAddress)
        oldData.info.address.street != newAddress.street -> viewModel.updateCustomerAddress(oldData.info.id, newAddress)
        oldData.info.address.city != newAddress.city -> viewModel.updateCustomerAddress(oldData.info.id, newAddress)
        oldData.info.address.zip != newAddress.zip -> viewModel.updateCustomerAddress(oldData.info.id, newAddress)
    }
}