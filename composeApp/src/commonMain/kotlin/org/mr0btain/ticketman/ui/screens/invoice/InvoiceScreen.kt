package org.mr0btain.ticketman.ui.screens.invoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

/**
 * Screen for building invoice.
 */

@Composable
fun InvoiceScreen(
    viewModel: MainViewModel
) {
    Surface(
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Invoice")
        }
    }
}