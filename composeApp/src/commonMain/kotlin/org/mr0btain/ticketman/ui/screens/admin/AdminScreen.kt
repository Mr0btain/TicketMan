package org.mr0btain.ticketman.ui.screens.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    isDarkMode: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 10.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Admin Screen",
                fontSize = 40.sp
            )
        }
    }
}