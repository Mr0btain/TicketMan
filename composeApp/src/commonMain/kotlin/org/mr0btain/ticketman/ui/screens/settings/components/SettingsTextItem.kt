package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.ui.dialog.TextEditDialog


@Composable
fun SettingsTextItem(
    icon: Painter,
    iconDesc: String,
    name: String,
    value: String,
    onConfirmChange: (String) -> Unit,
    isError: Boolean,
    inputFilter: (String) -> String, // input filter for the preference
    keyboardType: KeyboardType,
    showDialog: Boolean,
    surfaceOnClick: () -> Unit,
    dialogDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(value) }

    if (showDialog) {
        Dialog(onDismissRequest = { dialogDismiss() }) {
            TextEditDialog(
                name = name,
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                onConfirm = onConfirmChange,
                inputFilter = inputFilter,
                keyboardType = keyboardType,
                buttonText = "Next",
                isError = isError
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = surfaceOnClick,
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = iconDesc,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.width(16.dp))
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}