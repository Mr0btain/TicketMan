package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.ui.composables.TicketSearchBox
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

// TODO: Add ability to click column and sort by that value
// TODO: Add scroll to item when make new ticket
// TODO: Implement search function

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    viewModel: MainViewModel,
    scrollOnRight: Boolean
) {
    // State for tab selection
    val selectedTab = viewModel.selectedTicketTabIndex.collectAsState()

    val selectedTicket = viewModel.selectedTicketInfo.collectAsState()

    var customerData by remember(selectedTicket.value) { mutableStateOf(viewModel.getCustomerData(selectedTicket.value.customerId)) }

    val employeeId = viewModel.employeeId.collectAsState()

    val tickets = viewModel.ticketDataList.collectAsState()
    val sortedTickets = remember(tickets.value) { mutableStateOf(tickets.value) }

    // State for showing things
    var showSearchBar by remember { mutableStateOf(false) }
    var showEditTicketDialog by remember { mutableStateOf(false) }
    var showAddTicketDialog by remember { mutableStateOf(false) }

    val ticketLazyListState = rememberLazyListState()

    val focusManager = LocalFocusManager.current

    // To check if this is an update from clicking on an item, which we wouldn't want to scroll to it as that would be jarring
    var isSelectedUpdateFromClick by remember { mutableStateOf(false) }

    // On list update scroll to selected item
    // This will only really happen if we add a new customer
    LaunchedEffect(selectedTicket.value) {
        if (!isSelectedUpdateFromClick) {
            val index = sortedTickets.value.indexOf(selectedTicket.value)
            // Sanity check
            if (index >= 0 && index <= sortedTickets.value.size) {
                ticketLazyListState.scrollToItem(index)
            }
        } else isSelectedUpdateFromClick = false
    }

    // When we change values we should also update the data this way
    LaunchedEffect(selectedTab.value, tickets.value) {
        if (selectedTab.value == "0") {
            sortedTickets.value = tickets.value.sortedBy { it.identifier }.filter { it.assignedEmployee == employeeId.value }.reversed()
        } else {
            sortedTickets.value = tickets.value.sortedBy { it.identifier }.reversed()
        }
    }

    // For editing customer data
    if (showEditTicketDialog) {
        // TODO
    }

    // For adding new customer
    if (showAddTicketDialog) {
        AddTicketDialog(
            viewModel = viewModel,
            onDismiss = { showAddTicketDialog = false },
            isDarkMode = isDarkMode
        )
    }

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(135.dp))

                // Tab row for switching tables
                Box(
                    modifier = Modifier.width(260.dp)
                ) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab.value.toInt(),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .wrapContentWidth(),
                        containerColor = Color.Transparent
                    ) {
                        // My Tickets
                        Tab(
                            selected = selectedTab.value == "0",
                            onClick = {
                                focusManager.clearFocus()

                                viewModel.changeTicketTabIndex("0")
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
                            selected = selectedTab.value == "1",
                            onClick = {
                                focusManager.clearFocus()

                                viewModel.changeTicketTabIndex("1")
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

                // Button to drop down Search bar
                ElevatedButton(
                    onClick = { showSearchBar = !showSearchBar },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Search Bar",
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    Icon(
                        modifier = Modifier.size(22.dp),
                        painter = painterResource(
                            if (showSearchBar) Res.drawable.chevron_up else Res.drawable.chevron_down
                        ),
                        contentDescription = "Show search bar"
                    )
                }

                // Button for adding ticket
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()

                            showAddTicketDialog = true
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Add Ticket")
                    }

                    Spacer(Modifier.padding(8.dp))
                }
            }

            AnimatedVisibility(visible = showSearchBar) {
                Row(
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TicketSearchBox(
                        items = sortedTickets.value,
                        onResult = { item, isNew ->
                            if (isNew) {

                                // Popup new customer dialog
                                //showAddCustomerDialog = true
                            } else {
                                // Update selected customer
                                viewModel.changeSelectedTicketInfo(
                                    tickets.value.firstOrNull { it.identifier == item } ?: TicketInfo()
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.padding(4.dp))

            Row {
                // Check if were on my tickets which is 0 or all tickets
                if (selectedTab.value == "0") {
                    // My Tickets tab
                    TicketListPane(
                        isDarkMode = isDarkMode,
                        scrollOnRight = scrollOnRight,
                        ticketInfo = sortedTickets.value,
                        onClick = { ticket ->
                            focusManager.clearFocus()

                            // Set is update from click
                            isSelectedUpdateFromClick = true

                            // Change selected ticket
                            viewModel.changeSelectedTicketInfo(ticket)
                        },
                        selectedId = selectedTicket.value.identifier,
                        state = ticketLazyListState
                    )
                } else {
                    // All tickets tab.
                    TicketListPane(
                        isDarkMode = isDarkMode,
                        scrollOnRight = scrollOnRight,
                        ticketInfo = sortedTickets.value,
                        onClick = { ticket ->
                            focusManager.clearFocus()

                            // Update from click
                            isSelectedUpdateFromClick = true

                            // Update selected ticket
                            viewModel.changeSelectedTicketInfo(ticket)
                        },
                        selectedId = selectedTicket.value.identifier,
                        state = ticketLazyListState
                    )
                }

                // This pane shows the info for selected ticket
                TicketInfoPane(
                    modifier = Modifier,
                    customerData = customerData,
                    isDarkMode = isDarkMode,
                    ticketInfo = selectedTicket.value,
                    onEditClick = {
                        focusManager.clearFocus()
                        // TODO
                    }
                )
            }
        }
    }
}