package org.mr0btain.ticketman.ui.screens.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    snackBarState: SnackbarHostState
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
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