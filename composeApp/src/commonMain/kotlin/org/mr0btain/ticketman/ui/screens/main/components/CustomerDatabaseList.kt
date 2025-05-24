package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.data.CustomerInfo
import org.mr0btain.ticketman.ui.composables.CustomerDataGrid

@Composable
fun CustomerDatabaseList(
    modifier: Modifier = Modifier,
    customerInfoList: List<CustomerInfo>
) {
    Surface(
        modifier = modifier
    ) {
        CustomerDataGrid(
            modifier = Modifier,
            customerInfo = customerInfoList
        )
    }
}