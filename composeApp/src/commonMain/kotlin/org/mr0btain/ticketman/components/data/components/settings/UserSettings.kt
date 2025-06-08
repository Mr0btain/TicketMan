package org.mr0btain.ticketman.components.data.components.settings

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val darkMode: Boolean = false,
    val serverAddress: String = "",
    val verticalScrollRight: Boolean = true,
    val horizontalScrollTop: Boolean = false
)