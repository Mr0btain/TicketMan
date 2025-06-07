package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.Employee
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.ui.composables.TicketSearch
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

// TODO: Add ability to click column and sort by that value
// TODO: Should really offload some of the calculations on the lists to before we call this screen?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedPanel: Int,
    onSelectedPanelChange: (Int) -> Unit,
    selectedTicketId: Int,
    ticketList: List<TicketInfo>,
    customerList: List<CustomerData>,
    currentEmployee: Employee,
    employeeList: List<Employee>,
    onAddNewTicket: () -> Unit,
    onEditTicket: () -> Unit,
    onSelectedTicketChange: (Int) -> Unit,
    onDeleteTicket: (Int) -> Unit,
    scrollOnRight: Boolean,
    focusManager: FocusManager = LocalFocusManager.current,
    ticketListState: LazyListState = rememberLazyListState()
) {
    // Sorted tickets dependant upon which tab were on
    val sortedTickets = remember(selectedPanel, ticketList, currentEmployee) {
        if (selectedPanel == 0) {
            ticketList.sortedBy { it.identifier }.filter { it.assignedEmployee == currentEmployee.identifier }.reversed()
        } else {
            ticketList.sortedBy { it.identifier }.reversed()
        }
    }

    // Selected ticket info
    val selectedTicket = ticketList.firstOrNull() { it.identifier == selectedTicketId } ?: TicketInfo()

    // For search input
    var searchInput by remember { mutableStateOf("") }

    // Search items
    val searchItems = remember(selectedPanel) { sortedTickets.map { it.identifier } }

    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectedUpdateFromClick by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            // --------------------------------- Header ---------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                //Spacer(Modifier.width(135.dp))

                // Search box
                TicketSearch(
                    modifier = Modifier.padding(start = 8.dp),
                    items = searchItems.map { it.toString() },
                    input = searchInput,
                    onInputChange = { searchInput = it },
                    onClick = {
                        // Set search input in out text box
                        searchInput = it.toString()

                        // Call selected ticket change
                        onSelectedTicketChange(it)
                    }
                )

                Spacer(Modifier.padding(8.dp))

                // Tab row for switching tables
                Box(
                    modifier = Modifier.width(260.dp)
                ) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedPanel.toInt(),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .wrapContentWidth(),
                        containerColor = Color.Transparent
                    ) {
                        // My Tickets
                        Tab(
                            selected = selectedPanel == 0,
                            onClick = {
                                // CLear focus if search bar has it
                                focusManager.clearFocus()

                                // Call update panel
                                onSelectedPanelChange(0)
                                      },
                            modifier = Modifier.wrapContentWidth(),
                            text = {
                                Text(
                                    text = "My Tickets",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )

                        // All Tickets
                        Tab(
                            selected = selectedPanel == 1,
                            onClick = {
                                // Clear focus if search bar has it
                                focusManager.clearFocus()

                                onSelectedPanelChange(1)
                                      },
                            modifier = Modifier.wrapContentWidth(),
                            text = {
                                Text(
                                    text = "All Tickets",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.padding(8.dp))

                // Buttons for adding a removing tickets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Ticket Button
                    Button(
                        onClick = {
                            // Clear focus in case search bar isn't closed
                            focusManager.clearFocus()

                            // Trigger delete ticket dialog/ confirm
                            onDeleteTicket(selectedTicketId)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors().copy(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(text = "Delete Ticket")
                    }

                    Spacer(Modifier.padding(8.dp))

                    // Add Ticket Button
                    Button(
                        onClick = {
                            // Clear focus in case search bar isn't closed
                            focusManager.clearFocus()

                            // Trigger new ticket dialog
                            onAddNewTicket()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Add Ticket")
                    }

                    Spacer(Modifier.padding(8.dp))
                }
            }

            Spacer(Modifier.padding(4.dp))

            // --------------------------------- List and Info Panes ---------------------------------------------------
            Row {
                // Check if were on my tickets which is 0 or all tickets
                if (selectedPanel == 0) {
                    // My Tickets tab
                    TicketListPane(
                        isDarkMode = isDarkMode,
                        scrollOnRight = scrollOnRight,
                        ticketInfo = sortedTickets,
                        onClick = { ticket ->
                            focusManager.clearFocus()

                            // Set is update from click
                            isSelectedUpdateFromClick = true

                            // Change selected ticket
                            onSelectedTicketChange(ticket)
                        },
                        selectedId = selectedTicket.identifier,
                        state = ticketListState
                    )
                } else {
                    // All tickets tab.
                    TicketListPane(
                        isDarkMode = isDarkMode,
                        scrollOnRight = scrollOnRight,
                        ticketInfo = sortedTickets,
                        onClick = { ticket ->
                            focusManager.clearFocus()

                            // Update from click
                            isSelectedUpdateFromClick = true

                            // Update selected ticket
                            onSelectedTicketChange(ticket)
                        },
                        selectedId = selectedTicket.identifier,
                        state = ticketListState
                    )
                }

                // This pane shows the info for selected ticket
                TicketInfoPane(
                    modifier = Modifier,
                    customerData = customerList.firstOrNull { it.info.identifier == selectedTicket.customerId } ?: CustomerData(),
                    employeeName = (employeeList.firstOrNull() { it.identifier == selectedTicket.assignedEmployee } ?: Employee()).name,
                    isDarkMode = isDarkMode,
                    ticketInfo = selectedTicket,
                    onEditClick = {
                        // Clear focus if search bar has it
                        focusManager.clearFocus()

                        // Call edit ticket
                        onEditTicket()
                    }
                )
            }
        }
    }

    // --------------------------------- Launched effect for selected ticket ---------------------------------------------------

    // TODO: Maybe make this also switch to the right tab as well
    // On certain list update scroll to selected item
    // This will only really happen if we add a new ticket
    LaunchedEffect(selectedTicketId, selectedPanel) {
        if (!isSelectedUpdateFromClick) {
            // Check to see if were even on the list were selecting the ticket for, this comes up if
            // were on mytickets tab, and we add a new ticket that isn't assigned to our employee
            // then there's no reason to scroll to it as were not even on that list
            if (sortedTickets.map { it.identifier }.contains(selectedTicketId)) {
                // Get index of our item
                val index = sortedTickets.indexOf(selectedTicket)

                // Get index of visible items
                val visibleItemIndex = ticketListState.layoutInfo.visibleItemsInfo.map { it.index }

                // Check if item is in viewport, else scroll to it
                if (visibleItemIndex.isNotEmpty() && index >= 0) {
                    if (index < visibleItemIndex.first() || index > visibleItemIndex.last()) {
                        ticketListState.animateScrollToItem(
                            index = index,
                            scrollOffset = -(ticketListState.layoutInfo.viewportSize.height / 2) + 30
                            )
                    }
                }
            }
        } else isSelectedUpdateFromClick = false
    }
}