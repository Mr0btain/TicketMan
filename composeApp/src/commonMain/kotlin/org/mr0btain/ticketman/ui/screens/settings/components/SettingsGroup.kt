package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem.Companion.runIfType

@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    settingItemGroup: SettingItemGroup
) {
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = settingItemGroup.label,
            style = settingItemGroup.textStyle
        )

        Spacer(modifier = Modifier.padding(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column {
                settingItemGroup.items.forEach { settingItem ->
                    when (settingItem.type) {
                        SettingType.TEXT -> {
                            settingItem.runIfType<String> { textSetting ->
                                TextSetting(
                                    settingItem = textSetting,
                                    textStyle = settingItemGroup.childTextStyle
                                )
                            }
                        }

                        SettingType.CLICKABLE_TEXT -> {
                            settingItem.runIfType { clickableSetting ->
                                ClickableSetting(
                                    settingItem = clickableSetting,
                                    textStyle = settingItemGroup.childTextStyle
                                )
                            }
                        }

                        SettingType.SWITCH -> {
                            settingItem.runIfType { switchSetting ->
                                SwitchSetting(
                                    settingItem = switchSetting,
                                    textStyle = settingItemGroup.childTextStyle
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
    }
}