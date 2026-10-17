package org.mr0btain.ticketman.data.user

import kotlinx.serialization.Serializable

/**
 * Whether a user can log in, kept apart from their access level
 */
@Serializable
enum class UserStatus {
    /** Asked for an account or was invited, waiting to be approved or set up */
    PENDING,
    ACTIVE,
    /** Kept for history but can't log in */
    DEACTIVATED
}
