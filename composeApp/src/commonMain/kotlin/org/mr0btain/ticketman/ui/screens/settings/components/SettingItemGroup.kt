package org.mr0btain.ticketman.ui.screens.settings.components

import org.jetbrains.compose.resources.DrawableResource

/**
 * A titled group of settings
 * @param icon Shown beside the group's title
 */
data class SettingItemGroup(
    val label: String,
    val icon: DrawableResource? = null,
    val items: List<SettingItem<*>>
)
