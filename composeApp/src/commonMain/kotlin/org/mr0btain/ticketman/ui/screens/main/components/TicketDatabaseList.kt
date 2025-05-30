package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.composables.SearchTextField
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

// TODO: Sort the lists and remember the values to save on performance
// TODO: Add ability to click column and sort by that value
// TODO: Add scroll to item when make new ticket
// TODO: Add highlight to item when selected

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDatabaseList(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    viewModel: MainViewModel,
    scrollOnRight: Boolean
) {
    // State for tab selection
    val selectedTab = viewModel.selectedTicketTabIndex.collectAsState()
    val selectedTicket = viewModel.selectedTicketInfo.collectAsState()
    val employeeId = viewModel.employeeId.collectAsState()
    val tickets = viewModel.ticketDataList.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }

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
                            onClick = { viewModel.changeTicketTabIndex("0") },
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
                            onClick = { viewModel.changeTicketTabIndex("1") },
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
                            // TODO
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
                    SearchTextField(
                        modifier = Modifier.padding(bottom = 4.dp),
                        label = "Search",
                        placeholder = "",
                        onValueChange = {}
                    )
                }
            }

            Spacer(Modifier.padding(4.dp))

            Row {
                // Check if were on my tickets which is 0 or all tickets
                if (selectedTab.value == "0") {
                    // My Tickets tab. Filter tickets
                    TicketDataGrid(
                        isDarkMode = isDarkMode,
                        scrollOnRight = scrollOnRight,
                        ticketInfo = tickets.value.filter { it.assignedEmployee == employeeId.value }.reversed(),
                        onClick = { ticket ->
                            viewModel.changeSelectedTicketInfo(ticket)
                        }
                    )
                } else {
                    // All tickets tab.
                    // TODO: Sorting is just for testing will probably keep reversed in there
                    TicketDataGrid(
                        isDarkMode = isDarkMode,
                        scrollOnRight = scrollOnRight,
                        ticketInfo = tickets.value.sortedBy { it.idNum.toInt() }.reversed(),
                        onClick = { ticket ->
                            viewModel.changeSelectedTicketInfo(ticket)
                        }
                    )
                }

                TicketInfoItem(
                    modifier = Modifier,
                    isDarkMode = isDarkMode,
                    selectedTicketInfo = selectedTicket.value,
                    onEditClick = {}
                )
            }
        }
    }
}