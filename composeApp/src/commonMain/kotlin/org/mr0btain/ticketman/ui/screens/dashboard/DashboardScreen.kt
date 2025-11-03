package org.mr0btain.ticketman.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.screens.dashboard.components.DashboardItem
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.info
import ticketman.composeapp.generated.resources.tag
import ticketman.composeapp.generated.resources.trello
import ticketman.composeapp.generated.resources.user

// TODO: Were gonna have to move some dialog states up the chain so we can call them from say the view model.

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    snackBarState: SnackbarHostState
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            FlowRow(
                modifier = Modifier
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DashboardItem(
                    icon = painterResource(Res.drawable.tag),
                    label = "Add Ticket",
                    description = "Add a new ticket",
                    onClick = {
                        println("Add Ticket Click")
                    }
                )

                DashboardItem(
                    icon = painterResource(Res.drawable.user),
                    label = "Add Customer",
                    description = "Add a new customer",
                    onClick = {
                        println("Add Customer Click")
                    }
                )

                DashboardItem(
                    icon = painterResource(Res.drawable.trello),
                    label = "Ticket Status",
                    description = "Check status of a ticket",
                    onClick = {
                        println("Status Click")
                    }
                )

                DashboardItem(
                    icon = painterResource(Res.drawable.archive),
                    label = "Inventory",
                    description = "Inventory items",
                    onClick = {
                        println("Add Inventory Click")
                    }
                )
            }
        }
    }
}