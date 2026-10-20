package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.kit.inputs.TmCustomerForm
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * The counter's quick add customer screen
 */
@Composable
fun NewCustomerScreen(
    onSave: (Customer, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
    ) {
        TmPageHeader(title = "New customer", onBack = onCancel)
        TmCustomerForm(title = "Details", onSave = onSave, onCancel = onCancel)
    }
}
