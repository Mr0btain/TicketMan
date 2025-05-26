package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.CustomerData

@Composable
fun CustomerInfoItem(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedCustomerData: CustomerData
) {
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            Text("${selectedCustomerData}")
        }
    }
}