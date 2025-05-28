package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.composables.CustomerDataGrid
import org.mr0btain.ticketman.ui.composables.SearchTextField
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down
import ticketman.composeapp.generated.resources.chevron_up

@Composable
fun CustomerDatabaseList(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scrollBarTop: Boolean = false,
    scrollBarRight: Boolean = true,
    isDarkMode: Boolean
) {
    // Search bar state
    var showSearchBar by remember { mutableStateOf(false) }

    // Customer data
    val selectedCustomerData = viewModel.selectedCustomerData.collectAsState()
    val customerInfoList = viewModel.customerDataList.collectAsState()

    Surface(
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                // Button for adding a customer
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
                        Text(text = "Add Customer")
                    }

                    Spacer(Modifier.padding(8.dp))
                }
            }

            // Search bar
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

            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                CustomerDataGrid(
                    modifier = Modifier
                        .height(500.dp),
                    customerInfo = customerInfoList.value.map { it.info },
                    scrollBarRight = scrollBarRight,
                    isDarkMode = isDarkMode,
                    onClick = { id ->
                        viewModel.changeSelectedCustomerData(viewModel.getCustomerData(id))
                    }
                )

                CustomerInfoItem(
                    modifier = modifier
                        .height(500.dp),
                    isDarkMode = isDarkMode,
                    selectedCustomerData = selectedCustomerData.value,
                    onEditClick = {
                        // TODO: Add logic for this
                    },
                    tickets = viewModel.getTicketsForCustomer(selectedCustomerData.value.info.id)
                )
            }
        }
    }
}