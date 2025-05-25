package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.MainViewModel
import org.mr0btain.ticketman.ui.composables.SearchTextField
import org.mr0btain.ticketman.ui.composables.TicketDataGrid
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDatabaseList(
    isDarkMode: Boolean,
    viewModel: MainViewModel
) {
    // State for tab selection
    val selectedTab = viewModel.selectedTicketTabIndex.collectAsState()
    var showSearchBar by remember { mutableStateOf(false) }

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

        TicketDataGrid(
            isDarkMode = isDarkMode
        )
    }
}