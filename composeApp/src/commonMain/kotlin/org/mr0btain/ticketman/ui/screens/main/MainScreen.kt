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
import org.mr0btain.ticketman.ui.composables.TicketDataGrid
import org.mr0btain.ticketman.ui.screens.main.components.CustomerDatabaseList
import org.mr0btain.ticketman.ui.screens.main.components.TicketDatabaseList

/**
 * The main landing screen after logon
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    scrollBarTop: Boolean,
    scrollBarRight: Boolean,
    isDarkMode: Boolean
) {
    val tabIndex = viewModel.selectedMainTabIndex.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier.width(260.dp)
                    ) {
                        SecondaryTabRow(
                            selectedTabIndex = tabIndex.value.toInt(),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .wrapContentWidth(),
                            containerColor = Color.Transparent
                        ) {
                            // Customer Tab
                            Tab(
                                selected = tabIndex.value == "0",
                                onClick = { viewModel.changeMainTabIndex("0") },
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
                                selected = tabIndex.value == "1",
                                onClick = { viewModel.changeMainTabIndex("1") },
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

                    Spacer(Modifier.padding(8.dp))
                }

                HorizontalDivider(
                    modifier = Modifier,
                    thickness = DividerDefaults.Thickness,
                    color = if (isDarkMode) MaterialTheme.colorScheme.surface else DividerDefaults.color
                )

                if (tabIndex.value == "0") {
                    CustomerDatabaseList(
                        customerInfoList = viewModel.mockCustomerInfoList,
                        scrollBarTop = scrollBarTop,
                        scrollBarRight = scrollBarRight,
                        isDarkMode = isDarkMode
                    )
                } else {
                    TicketDatabaseList(viewModel)
                }
            }
        }
    }
}