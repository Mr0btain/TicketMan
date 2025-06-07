package org.mr0btain.ticketman.components.snackbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Custom SnackBar implementation
 */

@Composable
fun CustomSnackBar(
    modifier: Modifier = Modifier,
    state: SnackbarHostState
) {
    SnackbarHost(
        modifier = modifier.padding(bottom = 35.dp),
        hostState = state
    ) { data: SnackbarData ->
        Snackbar(
            containerColor = Color.Gray.copy(alpha = 0.8f),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            ) {
                Text(data.visuals.message)
            }
        }
    }
}