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
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.components.data.user.UserData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.ui.composables.AddOrDeleteButtonRow
import org.mr0btain.ticketman.ui.composables.search.TicketSearch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Add hours worked/ inventory items to a list of expense and charges
// TODO: Add invoice and ability to export

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun TicketPanel(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedPanel: Int,
    onSelectedPanelChange: (Int) -> Unit,
    selectedTicket: TicketData?,
    pagingItems: LazyPagingItems<Ticket>,
    currentUser: UserData?,
    onAddNewTicket: () -> Unit,
    onEditTicket: () -> Unit,
    onSelectedTicketChange: (Uuid) -> Unit,
    onDeleteTicket: (Uuid) -> Unit,
    onImageClick: (List<ImageData>) -> Unit,
    coroutineScope: CoroutineScope,
    ticketListState: LazyListState
) {
    // For search input
    var searchInput by remember { mutableStateOf("") }

    // Search items
    //TODO: FIx
    val searchItems = remember(selectedPanel) {
        emptyList<Uuid>() //sortedTickets.map { it.identifier }
    }

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
                    items = searchItems,
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
                        selectedTabIndex = selectedPanel,
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
                            selectedTicket?.let { ticketData ->
                                onDeleteTicket(ticketData.ticket.identifier)
                            }
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
                    pagingItems = pagingItems,
                    onClick = { ticket ->
                        // Update from click
                        isSelectedUpdateFromClick = true

                        // Update selected ticket
                        onSelectedTicketChange(ticket)
                    },
                    selectedId = selectedTicket?.ticket?.identifier,
                    state = ticketListState,
                    coroutineScope = coroutineScope
                )

                // This pane shows the info for selected ticket
                TicketInfoPane(
                    modifier = Modifier,
                    customer = Customer(), //selectedTicket.customer,
                    assignedUserName = PersonName(), //selectedTicket.assignedUser.name, //(users.firstOrNull() { it.identifier.toString() == selectedTicket.assignedUser } ?: User()).name,
                    isDarkMode = isDarkMode,
                    ticketData = selectedTicket,
                    onEditClick = {
                        // Call edit ticket
                        onEditTicket()
                    },
                    onImageClick = onImageClick
                )
            }
        }
    }

    // --------------------------------- Launched effect for selected ticket ---------------------------------------------------

    // TODO: FIX
    // TODO: Maybe make this also switch to the right tab as well
    // On certain list update scroll to selected item
    // This will only really happen if we add a new ticket
    LaunchedEffect(selectedTicket?.ticket?.identifier, selectedPanel) {
        /*
        if (!isSelectedUpdateFromClick) {
            // Check to see if were even on the list were selecting the ticket for, this comes up if
            // were on mytickets tab, and we add a new ticket that isn't assigned to our employee
            // then there's no reason to scroll to it as were not even on that list
            if (sortedTickets.map { it.identifier }.contains(selectedTicket.ticket.identifier)) {
                // Get index of our item
                val index = sortedTickets.indexOf(selectedTicket.ticket)

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
         */
    }
}