package org.mr0btain.ticketman.ui.screens.main.components.ticket

import androidx.compose.foundation.layout.*
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
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.shared.DialogDismissReason
import org.mr0btain.ticketman.data.shared.MainItemType
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.shared.SubItemType
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.components.utils.isNewTicket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.composables.CustomerSearch
import org.mr0btain.ticketman.ui.composables.UserSearch
import org.mr0btain.ticketman.ui.composables.TicketPriorityDropDown
import org.mr0btain.ticketman.ui.composables.TicketStatusDropDown
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.dialog.ConfirmDeleteDialog
import org.mr0btain.ticketman.ui.dialog.NoteDialog
import org.mr0btain.ticketman.ui.screens.main.components.ConfirmOrCancelButtonRow
import org.mr0btain.ticketman.ui.screens.main.components.DialogNoteList
import org.mr0btain.ticketman.ui.screens.main.components.DialogTextRow
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

// TODO: Add checks to make sure its not blank
// TODO: Add ability to edit update?
@Composable
fun TicketDialog(
    modifier: Modifier = Modifier,
    customerInfo: List<CustomerInfo>,
    users: List<User>,
    onAddNewCustomer: (PersonName) -> Unit,
    onDismiss: (TicketInfo?, DialogDismissReason) -> Unit,
    seedData: TicketInfo,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean
) {
    val userIds = remember(users) { users.map { it.identifier.toString() } }

    // seed data
    var customerId by remember(seedData) { mutableIntStateOf(seedData.customerId) }

    // Assigned employee
    var assignedUser by remember(seedData) { mutableStateOf(seedData.assignedUser) }

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
    var customerSearchBarInput by remember { mutableStateOf(if (seedData.customerId != Int.MAX_VALUE) seedData.customerId.toString() else "") }

    // For employee search bar
    var userSearchBarInput by remember { mutableStateOf(if (userIds.contains(seedData.assignedUser)) seedData.assignedUser.toString()else "")  }

    // Add Note dialog
    if (showAddNoteDialog) {
        NoteDialog(
            isDarkMode = isDarkMode,
            label = "Update",
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
                        text = if (seedData.isNewTicket()) "Add New Ticket: ${seedData.identifier}" else "Edit Ticket: ${seedData.identifier}",
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
                    // ------------------------- Customer -------------------------------
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
                            customerSearchBarInput = item.toString()

                            // Set customer id
                            customerId = item
                        }
                    )

                    Spacer(Modifier.padding(8.dp))

                    // ------------------------- Assigned user -------------------------------
                    Text(
                        text = "Assigned User",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )

                    Spacer(Modifier.padding(4.dp))

                    UserSearch(
                        items = users,
                        input = userSearchBarInput,
                        onInputChange = { userSearchBarInput = it },
                        onClick = { item ->
                            // Set search bar text
                            userSearchBarInput = item.toString()

                            // Set user id
                            assignedUser = item.toString()
                        }
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

                Spacer(Modifier.padding(8.dp))

                // -------------------------------------------- Confirm Cancel buttons ----------------------------------------------------
                ConfirmOrCancelButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                    onCancelClick = { onDismiss(null, DialogDismissReason.CANCEL) },
                    onConfirmClick = {
                        // Call dismiss with data
                        onDismiss(
                            TicketInfo(
                                identifier = seedData.identifier,
                                customerId = customerId,
                                assignedUser = assignedUser,
                                device = Device(deviceManufacturer, deviceModel),
                                status = status,
                                priority = priority,
                                updates = updates,
                                time = seedData.time
                            ),
                            DialogDismissReason.CONTINUE
                        )
                    }
                )
            }
        }
    }
}