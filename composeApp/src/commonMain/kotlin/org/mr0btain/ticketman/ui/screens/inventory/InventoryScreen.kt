package org.mr0btain.ticketman.ui.screens.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

@Composable
fun InventoryScreen(
    viewModel: MainViewModel,
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Inventory Screen",
                fontSize = 40.sp
            )
        }
    }
}