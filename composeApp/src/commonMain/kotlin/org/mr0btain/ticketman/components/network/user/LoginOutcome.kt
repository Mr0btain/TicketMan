package org.mr0btain.ticketman.components.network.user

/**
 * What came of logging in
 */
sealed interface LoginOutcome {
    /** Logged in, tokens are stored. [needsSetup] means first login setup has to be finished */
    data class LoggedIn(val needsSetup: Boolean) : LoginOutcome

    /** Requested account that an admin hasn't approved yet */
    data object PendingApproval : LoginOutcome

    /** Logged in with an invite code, finish with [setupToken] */
    data class InviteSetup(val username: String, val setupToken: String) : LoginOutcome

    data object Failed : LoginOutcome
}
