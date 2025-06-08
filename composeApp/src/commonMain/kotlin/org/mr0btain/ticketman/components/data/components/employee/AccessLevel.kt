package org.mr0btain.ticketman.components.data.components.employee

import kotlinx.serialization.Serializable

@Serializable
enum class AccessLevel {
    RESTRICTED,
    NORMAL,
    ADMIN
}