package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.containers.TmTextEditDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_right

/** One panel of settings, each item drawn as a row for its kind of [SettingItem] */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    settingItemGroup: SettingItemGroup
) {
    TmPanel(modifier = modifier.fillMaxWidth(), title = settingItemGroup.label, icon = settingItemGroup.icon) {
        Column {
            settingItemGroup.items.forEach { settingItem ->
                when (settingItem) {
                    is SettingItem.Text -> TextSettingRow(settingItem)
                    is SettingItem.Action -> SettingRow(settingItem, value = settingItem.value, onClick = settingItem.onClick)
                    is SettingItem.Switch -> SwitchSettingRow(settingItem)
                    is SettingItem.Choice -> ChoiceSettingRow(settingItem)
                }
            }
        }
    }
}

/** Shows the value, clicking opens a dialog to change it */
@Composable
private fun TextSettingRow(settingItem: SettingItem.Text) {
    var showDialog by remember { mutableStateOf(false) }

    TmTextEditDialog(
        label = settingItem.label,
        value = settingItem.value,
        visible = showDialog,
        hint = settingItem.description,
        onConfirm = settingItem.onChange,
        onCancel = { showDialog = false },
        keyboardType = KeyboardType.Text
    )

    SettingRow(settingItem, value = settingItem.value, onClick = { showDialog = true })
}

@Composable
private fun SwitchSettingRow(settingItem: SettingItem.Switch) {
    val toggle = { settingItem.onToggle(!settingItem.checked) }

    TmListRow(
        title = settingItem.label,
        subtitle = settingItem.description,
        enabled = settingItem.enabled,
        leading = { SettingIcon(settingItem) },
        onClick = toggle,
        trailing = {
            TmSwitch(checked = settingItem.checked, onCheckedChange = { toggle() }, enabled = settingItem.enabled)
        }
    )
}

@Composable
private fun ChoiceSettingRow(settingItem: SettingItem.Choice) {
    TmListRow(
        title = settingItem.label,
        subtitle = settingItem.description,
        enabled = settingItem.enabled,
        leading = { SettingIcon(settingItem) },
        trailing = {
            TmDropdown(
                modifier = Modifier.width(180.dp),
                options = settingItem.options,
                selected = settingItem.selected,
                onSelect = settingItem.onSelect,
                optionLabel = { it },
                enabled = settingItem.enabled,
                size = TmControlSize.SMALL
            )
        }
    )
}

@Composable
private fun SettingRow(settingItem: SettingItem, value: String, onClick: () -> Unit) {
    TmListRow(
        title = settingItem.label,
        subtitle = settingItem.description,
        enabled = settingItem.enabled,
        leading = { SettingIcon(settingItem) },
        onClick = if (settingItem.enabled) onClick else null,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) {
                    Text(
                        modifier = Modifier.widthIn(max = 240.dp),
                        text = value,
                        style = TmTheme.typography.body,
                        color = TmTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(Res.drawable.chevron_right),
                    contentDescription = null,
                    tint = TmTheme.colors.textFaint
                )
            }
        }
    )
}

@Composable
private fun SettingIcon(settingItem: SettingItem) {
    Icon(
        modifier = Modifier.size(18.dp),
        painter = settingItem.icon,
        contentDescription = null,
        tint = TmTheme.colors.textMuted
    )
}
