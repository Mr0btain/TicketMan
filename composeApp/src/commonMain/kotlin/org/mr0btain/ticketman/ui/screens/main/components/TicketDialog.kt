package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.*
import org.mr0btain.ticketman.ui.composables.CustomerSearch
import org.mr0btain.ticketman.ui.composables.EmployeeSearch
import org.mr0btain.ticketman.ui.composables.TicketPriorityDropDown
import org.mr0btain.ticketman.ui.composables.TicketStatusDropDown
import org.mr0btain.ticketman.ui.dialog.AddNoteDialog
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

// TODO: Add checks to make sure its not blank
@Composable
fun TicketDialog(
    modifier: Modifier = Modifier,
    customerInfo: List<CustomerInfo>,
    employees: List<Employee>,
    onAddNewCustomer: (PersonName) -> Unit,
    onDismiss: (TicketInfo?, Boolean) -> Unit,
    seedData: TicketInfo,
    focusManager: FocusManager = LocalFocusManager.current,
    isDarkMode: Boolean
) {
    // seed data
    var customerId by remember(seedData) { mutableStateOf(seedData.customerId) }

    // Assigned employee
    var assignedEmployee by remember(seedData) { mutableStateOf(seedData.assignedEmployee) }

    // Device
    var deviceManufacturer by remember(seedData) { mutableStateOf(seedData.device.manufacturer) }
    var deviceModel by remember(seedData) { mutableStateOf(seedData.device.model) }

    // Status and priority
    var status by remember(seedData) { mutableStateOf(seedData.status) }
    var priority by remember(seedData) { mutableStateOf(seedData.priority) }

    // Mutable lists
    val updates = remember(seedData) { seedData.updates.toMutableStateList() }

    // For deleting item dialog
    var showConfirmDeleteScreen by remember { mutableStateOf(false) }
    var itemUpForDeleteIndex by remember { mutableIntStateOf(0) }
    var itemForDelete by remember { mutableStateOf(SubItemType.NONE) }

    // For adding note dialog
    var showAddNoteDialog by remember { mutableStateOf(false) }

    // For customer search bar
    var customerSearchBarInput by remember { mutableStateOf(seedData.customerId) }

    // For employee search bar
    var employeeSearchBarInput by remember { mutableStateOf(seedData.assignedEmployee) }

    // Add Note dialog
    if (showAddNoteDialog) {
        AddNoteDialog(
            isDarkMode = isDarkMode,
            label = "Add Update",
            onConfirm = { item ->
                // Add to out list
                updates.add(item)
            },
            onDismissRequest = { showAddNoteDialog = false }
        )
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
            onDismiss(null, false)
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
                        // Add to Customer Section
                        Text(
                            text = "Customer",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        CustomerSearch(
                            items = customerInfo,
                            input = customerSearchBarInput,
                            onInputChange = { customerSearchBarInput = it },
                            onClick = { item ->
                                // Set search bar text
                                customerSearchBarInput = item

                                // Set customer id
                                customerId = item
                                      },
                            focusManager = focusManager
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

                        EmployeeSearch(
                            items = employees,
                            focusManager = focusManager,
                            input = employeeSearchBarInput,
                            onInputChange = { employeeSearchBarInput = it },
                            onClick = { item ->
                                // Set search bar text
                                employeeSearchBarInput = item

                                // Set employee id
                                assignedEmployee = item
                            }
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
                            onFirstValueChange = { deviceManufacturer = it },
                            secondLabel = "Model",
                            secondValue = deviceModel,
                            onSecondValueChange = { deviceModel = it }
                        )

                        Spacer(Modifier.padding(4.dp))

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
                        onClick = { onDismiss(null, false) },
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
                                TicketInfo(
                                    identifier = seedData.identifier,
                                    customerId = customerId,
                                    assignedEmployee = assignedEmployee,
                                    device = Device(deviceManufacturer, deviceModel),
                                    status = status,
                                    priority = priority,
                                    updates = updates
                                ),
                                true
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