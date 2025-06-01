package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import org.mr0btain.ticketman.components.data.components.*
import org.mr0btain.ticketman.ui.composables.CustomerSearchBox
import org.mr0btain.ticketman.ui.composables.EmployeeSearchBox
import org.mr0btain.ticketman.ui.composables.TicketPriorityDropDown
import org.mr0btain.ticketman.ui.composables.TicketStatusDropDown
import org.mr0btain.ticketman.ui.dialog.AddNoteDialog
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

// TODO: Add checks to make sure its not blank
@Composable
fun AddTicketDialog(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onAddNewCustomer: (PersonName) -> Unit,
    onDismiss: () -> Unit,
    isDarkMode: Boolean
) {
    // Current Employee
    val currentEmp = viewModel.employee.collectAsState().value

    // Get next id
    val identifier = remember { viewModel.getNextTicketId() }

    // Name
    val customerId = remember { mutableStateOf("") }

    // Assigned employee
    val assignedEmployee = remember { mutableStateOf("") }

    // Device
    val deviceManufacturer = remember { mutableStateOf("") }
    val deviceModel = remember { mutableStateOf("") }

    // Status and priority
    val status = remember { mutableStateOf(TicketStatus.IN_PROGRESS) }
    val priority = remember { mutableStateOf(TicketPriority.LOW) }

    // Mutable lists
    val updates = remember { mutableStateListOf<Note>() }

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
            label = "Add New Update",
            onConfirm = { item ->
                // Add to out list
                updates.add(item)
            },
            onDismissRequest = { showAddNoteDialog = false }
        )
    }

    // Add basic item dialog
    if (showAddBasicSubItemDialog) {
        // TODO Gonna have to make some custom logic for this if its even needed
    }


    // Delete item dialog
    if (showConfirmDeleteScreen) {
        ConfirmDeleteDialog(
            itemName = when (itemForDelete) {
                SubItemType.UPDATE -> updates[itemUpForDeleteIndex].author + "'s Update"
                else -> ""
            },
            isDarkMode = isDarkMode,
            mainItemType = MainItemType.CUSTOMER,
            onDismissRequest = { confirm ->
                if (confirm && itemForDelete != SubItemType.NONE) {
                    when (itemForDelete) {
                        SubItemType.UPDATE -> {
                            // Remove item from list
                            updates.removeAt(itemUpForDeleteIndex)
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
                        text = "Add New Ticket: $identifier",
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
                        // Add to Customer Section
                        Text(
                            text = "Customer",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        CustomerSearchBox(
                            items = viewModel.getAllCustomerInfo(),
                            onResult = { item, isNew ->
                                // Check if were adding a new customer or not
                                if (isNew) {
                                    val newName = item.split(" ")

                                    onAddNewCustomer(
                                        PersonName(
                                            firstName = newName.getOrElse(0) { "" },
                                            lastName = newName.getOrElse(1) { "" }
                                        )
                                    )
                                } else {
                                    // Else set customer id
                                    customerId.value = item
                                }
                            }
                        )

                        Spacer(Modifier.padding(8.dp))

                        // Assigned employee section
                        // TODO: Add drop down list for current employees
                        Text(
                            text = "Assigned Employee",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        EmployeeSearchBox(
                            items = viewModel.getAllEmployees(),
                            onResult = { emp, isNew ->
                                // TODO: don't know if were going to allow new employee creation here or not. Maybe if admin?
                                // Add assigned employee.
                                assignedEmployee.value = emp
                            },
                            currentEmployee = currentEmp
                        )

                        Spacer(Modifier.padding(8.dp))

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
                            secondLabel = "Model",
                            secondValue = deviceModel
                        )

                        Spacer(Modifier.padding(4.dp))

                        Text(
                            text = "Status",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        TicketStatusDropDown(
                            modifier = Modifier.width(350.dp),
                        ) {
                            status.value = it
                        }

                        Spacer(Modifier.padding(4.dp))

                        Text(
                            text = "Priority",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        TicketPriorityDropDown(
                            modifier = Modifier.width(350.dp)
                        ) {
                            priority.value = it
                        }

                        Spacer(Modifier.padding(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Updates",
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
                                    contentDescription = "Add new Update"
                                )
                            }
                        }

                        Spacer(Modifier.padding(4.dp))

                        DialogNoteList(
                            notes = updates,
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
                            viewModel.addTicket(
                                TicketInfo(
                                    identifier = identifier,
                                    customerId = customerId.value,
                                    assignedEmployee = assignedEmployee.value,
                                    device = Device(deviceManufacturer.value, deviceModel.value),
                                    status = status.value,
                                    priority = priority.value,
                                    updates = updates
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