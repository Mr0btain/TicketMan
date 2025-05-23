package org.mr0btain.ticketman.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.MainViewModel
import org.mr0btain.ticketman.ui.composables.SearchTextField
import org.mr0btain.ticketman.ui.screens.main.components.CustomerDatabaseList
import org.mr0btain.ticketman.ui.screens.main.components.TicketDatabaseList

/**
 * Main Screen containing all the customer/ticket data and such
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isDarkMode: State<Boolean>
) {
    // State for tab selection
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = if (isDarkMode.value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.padding(8.dp))

                    SearchTextField(
                        modifier = Modifier.padding(bottom = 4.dp),
                        label = "Search",
                        placeholder = "",
                        onValueChange = {}
                    )

                    // TODO: Maybe add when you hot enter it adds a pin for that item then you can add more pins to narrow searches? like filters

                    Spacer(Modifier.padding(12.dp))

                    Box(
                        modifier = Modifier.width(260.dp)
                    ) {
                        SecondaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .wrapContentWidth(),
                            containerColor = Color.Transparent
                        ) {
                            // Customer Tab
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                modifier = Modifier.wrapContentWidth(),
                                text = {
                                    Text(
                                        text = "Customers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )

                            // Ticket Tab
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                modifier = Modifier.wrapContentWidth(),
                                text = {
                                    Text(
                                        text = "Tickets",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = {

                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        /*
                        Icon(
                            modifier = Modifier
                                .size(22.dp),
                            painter = painterResource(Res.drawable.plus),
                            contentDescription = ""
                        )

                        Spacer(Modifier.padding(4.dp))
                         */

                        Text(
                            text = "Add " + if (selectedTabIndex == 0) "Customer" else "Ticket"
                        )
                    }

                    Spacer(Modifier.padding(8.dp))
                }

                HorizontalDivider(
                    modifier = Modifier,
                    thickness = DividerDefaults.Thickness,
                    color = if (isDarkMode.value) MaterialTheme.colorScheme.surface else DividerDefaults.color
                )

                if (selectedTabIndex == 0) {
                    CustomerDatabaseList(
                        customerInfoList = viewModel.mockCustomerInfoList
                    )
                } else {
                    TicketDatabaseList()
                }
            }
        }
    }
}