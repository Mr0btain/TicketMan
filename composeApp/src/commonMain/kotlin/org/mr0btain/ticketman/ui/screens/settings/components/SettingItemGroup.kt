package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.ui.text.TextStyle

data class SettingItemGroup(
    val label: String,
    val textStyle: TextStyle,
    val childTextStyle: TextStyle,
    val items: List<SettingItem<Any>>
)
