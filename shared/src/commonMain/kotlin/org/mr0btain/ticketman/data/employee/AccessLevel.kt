package org.mr0btain.ticketman.data.employee

import kotlinx.serialization.Serializable

@Serializable
enum class AccessLevel {
    RESTRICTED,
    NORMAL,
    ADMIN
}