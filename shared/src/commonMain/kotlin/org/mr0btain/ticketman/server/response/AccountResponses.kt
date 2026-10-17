package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.user.User

/**
 * A new invite. The code is only ever shown here, the server keeps a hash
 * @param expiresAt Epoch millis the code stops working
 */
@Serializable
data class InviteResponse(
    val user: User,
    val code: String,
    val expiresAt: Long
)

/**
 * Someone waiting on an admin or on finishing their invite
 * @param inviteExpiresAt Set for invites, epoch millis
 */
@Serializable
data class PendingUserResponse(
    val user: User,
    val kind: PendingKind,
    val inviteExpiresAt: Long? = null
)

@Serializable
enum class PendingKind {
    /** Asked from the login screen, needs approving */
    REQUEST,

    /** Made by an admin, waiting for the user to use their code */
    INVITE
}

/** What happened when someone tried to log in */
@Serializable
enum class LoginStatus {
    /** Logged in */
    OK,

    /** Logged in, but they have to finish setting up their account first */
    NEEDS_SETUP,

    /** Used an invite code, no session yet. Finish with the setup token */
    INVITE_SETUP,

    /** Right password but an admin hasn't approved the account yet */
    PENDING_APPROVAL
}
