package org.mr0btain.ticketman.components.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val darkMode: Boolean = false,
    val serverUrl: String = "",
    val autoSync: Boolean = true,
    val scrollBarsOnRight: Boolean = true,
    val scrollBarsOnBottom: Boolean = false
)