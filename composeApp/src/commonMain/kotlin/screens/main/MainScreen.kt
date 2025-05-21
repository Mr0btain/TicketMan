package screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import components.MainViewModel
import components.composables.SearchTextField
import screens.main.components.CustomerDatabaseList
import screens.main.components.TicketDatabaseList

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
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                ) {
                    // Customer Tab
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        modifier = Modifier
                            .height(56.dp)
                            .weight(1f),    // Equal width for all tabs
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
                        modifier = Modifier
                            .height(56.dp)  // Standard Material3 tab height
                            .weight(1f),    // Equal width for all tabs
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

                Row(
                    modifier = Modifier.padding(4.dp)
                ) {
                    SearchTextField(
                        label = "Search",
                        placeholder = "",
                        onValueChange = {}
                    )

                    // TODO: Maybe add when you hot enter it adds a pin for that item then you can add more pins to narrow searches? like filters
                }

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