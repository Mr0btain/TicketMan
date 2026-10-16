package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.ui.dialog.TextEditDialog

/**
 * An editable text setting item. Maybe later i may change this to instead of using a dialog popup,
 * we just use a TextField and an icon button to make it editable? or something like that
 */

// TODO: Get rid if text dialog and make a cleaner way of doing this

@Composable
fun TextSetting(
    settingItem: SettingItem<String>,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var state by remember(settingItem.initialState) { mutableStateOf(settingItem.initialState) }
    var showDialog by remember { mutableStateOf(false) }

    TextEditDialog(
        label = settingItem.label,
        value = state,
        visible = showDialog,
        onConfirm = { text ->
            val result = settingItem.onClick?.let { it(text) } ?: false
            if (result) state = text
            result
                    },
        onCancel = { showDialog = false },
        keyboardType = KeyboardType.Text,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = { showDialog = true },
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
                        settingItem.icon,
                        contentDescription = settingItem.description,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = settingItem.label,
                        style = textStyle
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = settingItem.initialState,
                    style = textStyle
                )

                Spacer(modifier = Modifier.width(16.dp))
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}