package org.mr0btain.ticketman.data.inventory

enum class AccessLevel(val level: Int) {
    RESTRICTED(0),
    NORMAL(1),
    ADMIN(2)
}