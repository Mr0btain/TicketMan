package org.mr0btain.ticketman.data.employee

import kotlinx.serialization.Serializable

@Serializable
enum class AccessLevel(val level: Int) {
    RESTRICTED(0),
    NORMAL(1),
    ADMIN(2)
}