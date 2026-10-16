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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.arrow_right

@Composable
fun ClickableSetting(
    settingItem: SettingItem<String>,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var state by remember { mutableStateOf(settingItem.initialState) }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = {
            settingItem.onClick?.let { function ->
                function(state)
            }
        },
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        settingItem.icon,
                        contentDescription = settingItem.description,
                        modifier = Modifier
                            .size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = settingItem.label,
                        style = textStyle,
                        modifier = Modifier
                            .padding(16.dp),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                Text(
                    text = settingItem.initialState,
                    style = textStyle
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    painterResource(Res.drawable.arrow_right),
                    tint = MaterialTheme.colorScheme.surfaceTint,
                    contentDescription = ""
                )
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }

    }
}