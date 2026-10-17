package org.mr0btain.ticketman.data.inventory

enum class AccessLevel(val level: Int) {
    RESTRICTED(0),
    NORMAL(1),
    ADMIN(2),

    /** How deactivated users used to be stored, now [org.mr0btain.ticketman.data.user.UserStatus.DEACTIVATED] */
    NONE(9);

    /** Whether this level is allowed in somewhere that needs [minimum]. NONE is a deactivated user so it never is */
    fun meets(minimum: AccessLevel): Boolean =
        this != NONE && level >= minimum.level
}
