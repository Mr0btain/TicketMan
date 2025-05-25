package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.login_button_text
import ticketman.composeapp.generated.resources.login_text

/**
 * Login screen composable made for dialog but can be used either way
 */

// TODO: Implement logic for login

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onConfirm: (user:String, pass: String) -> Boolean,
    onDismiss: () -> Unit,
    isDarkMode: State<Boolean>
) {
    TwoFactorField(
        isDarkMode = isDarkMode.value
    )
}