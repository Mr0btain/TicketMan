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
import ticketman.composeapp.generated.resources.user

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
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
                    description = "Add a new ticket"
                )

                DashboardItem(
                    icon = painterResource(Res.drawable.user),
                    label = "Add Customer",
                    description = "Add a new customer"
                )

                DashboardItem(
                    icon = painterResource(Res.drawable.info),
                    label = "Ticket Status",
                    description = "Check status of a ticket"
                )

                DashboardItem(
                    icon = painterResource(Res.drawable.archive),
                    label = "Inventory",
                    description = "Check inventory items"
                )
            }
        }
    }
}