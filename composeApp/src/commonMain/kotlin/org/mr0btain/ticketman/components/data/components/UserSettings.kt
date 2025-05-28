package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

@Serializable
data class UserSettings(
    val darkMode: Boolean = false,
    val serverAddress: String = "",
    val verticalScrollRight: Boolean = true,
    val horizontalScrollTop: Boolean = false
)