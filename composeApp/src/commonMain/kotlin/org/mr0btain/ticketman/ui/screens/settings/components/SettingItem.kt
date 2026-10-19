package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.ui.graphics.painter.Painter

/**
 * One row in a [SettingItemGroup], drawn by [SettingsGroup] according to its kind
 * @param enabled Off greys the row out and ignores clicks
 */
sealed class SettingItem {
    abstract val icon: Painter
    abstract val label: String
    abstract val description: String
    abstract val enabled: Boolean

    /** A row that toggles [checked] when clicked */
    data class Switch(
        override val icon: Painter,
        override val label: String,
        override val description: String,
        val checked: Boolean,
        override val enabled: Boolean = true,
        val onToggle: (Boolean) -> Unit
    ) : SettingItem()

    /**
     * Shows [value], clicking opens a dialog to type a new one
     * @param onChange Gets the typed text. Returns false to keep the dialog open and show [description] as the error
     */
    data class Text(
        override val icon: Painter,
        override val label: String,
        override val description: String,
        val value: String,
        override val enabled: Boolean = true,
        val onChange: (String) -> Boolean
    ) : SettingItem()

    /** A row with a chevron that runs [onClick], used to open a dialog */
    data class Action(
        override val icon: Painter,
        override val label: String,
        override val description: String,
        val value: String = "",
        override val enabled: Boolean = true,
        val onClick: () -> Unit
    ) : SettingItem()

    /** Pick one of [options] from a dropdown */
    data class Choice(
        override val icon: Painter,
        override val label: String,
        override val description: String,
        val selected: String,
        val options: List<String>,
        override val enabled: Boolean = true,
        val onSelect: (String) -> Unit
    ) : SettingItem()
}
