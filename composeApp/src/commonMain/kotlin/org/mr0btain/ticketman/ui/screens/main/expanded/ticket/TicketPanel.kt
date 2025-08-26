package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.ui.composables.TicketSearch
import org.mr0btain.ticketman.ui.screens.main.components.AddOrDeleteButtonRow

// TODO: Add hours worked/ inventory items to a list of expense and charges
// TODO: Add invoice and ability to export

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedPanel: Int,
    onSelectedPanelChange: (Int) -> Unit,
    scrollBarsOnRight: Boolean,
    selectedTicketId: String,
    ticketList: List<Ticket>,
    customerList: List<Customer>,
    currentUser: User,
    users: List<User>,
    onAddNewTicket: () -> Unit,
    onEditTicket: () -> Unit,
    onSelectedTicketChange: (String) -> Unit,
    onDeleteTicket: (String) -> Unit,
    onImageClick: (List<String>) -> Unit,
    coroutineScope: CoroutineScope,
    ticketListState: LazyListState
) {
    val currentUserId = remember(currentUser) { currentUser.identifier }

    // Sorted tickets by which tab were on
    val sortedTickets = remember(selectedPanel, ticketList, currentUser) {
        if (selectedPanel == 0) {
            ticketList.sortedBy { it.identifier }.filter { it.assignedUser == currentUserId }.reversed()
        } else {
            ticketList.sortedBy { it.identifier }.reversed()
        }
    }

    // Selected ticket info
    val selectedTicket =
        remember(selectedTicketId) { ticketList.firstOrNull() { it.identifier == selectedTicketId } ?: Ticket() }

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
                            onClick = { onSelectedPanelChange(0) },
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
                            onClick = { onSelectedPanelChange(1) },
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
                    AddOrDeleteButtonRow(
                        label = "Ticket",
                        onAddClick = {
                            // Trigger new item dialog
                            onAddNewTicket()
                        },
                        onDeleteClick = {
                            // Trigger delete ticket dialog/ confirm
                            onDeleteTicket(selectedTicketId)
                        }
                    )

                    Spacer(Modifier.padding(8.dp))
                }
            }

            Spacer(Modifier.padding(4.dp))

            // --------------------------------- List and Info Panes ---------------------------------------------------
            Row(
                modifier = Modifier.sizeIn(maxHeight = 500.dp)
            ) {
                // All tickets tab.
                TicketListPane(
                    isDarkMode = isDarkMode,
                    ticket = sortedTickets,
                    scrollBarsOnRight = scrollBarsOnRight,
                    onClick = { ticket ->
                        // Update from click
                        isSelectedUpdateFromClick = true

                        // Update selected ticket
                        onSelectedTicketChange(ticket)
                    },
                    selectedId = selectedTicket.identifier,
                    state = ticketListState,
                    coroutineScope = coroutineScope
                )

                // This pane shows the info for selected ticket
                TicketInfoPane(
                    modifier = Modifier,
                    customer = customerList.firstOrNull { it.identifier == selectedTicket.customerId }
                        ?: Customer(),
                    assignedUserName = (users.firstOrNull() { it.identifier.toString() == selectedTicket.assignedUser }
                        ?: User()).name,
                    isDarkMode = isDarkMode,
                    ticket = selectedTicket,
                    scrollBarsOnRight = scrollBarsOnRight,
                    onEditClick = {
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