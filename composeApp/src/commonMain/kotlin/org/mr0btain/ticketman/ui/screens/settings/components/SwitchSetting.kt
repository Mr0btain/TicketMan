package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp


@Composable
fun SwitchSetting(
    settingItem: SettingItem<Boolean>,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var state by remember { mutableStateOf(settingItem.initialState) }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        settingItem.icon,
                        contentDescription = settingItem.description,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = settingItem.label,
                        modifier = Modifier.padding(16.dp),
                        style = textStyle
                    )
                }

                Spacer(Modifier.padding(8.dp))

                Switch(
                    checked = state,
                    onCheckedChange = { value ->
                        state = value
                        settingItem.onClick?.let { it(value) }
                    },
                    enabled = settingItem.enabled
                )
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}