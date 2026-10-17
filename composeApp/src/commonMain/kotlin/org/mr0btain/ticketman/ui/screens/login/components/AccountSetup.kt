package org.mr0btain.ticketman.ui.screens.login.components

import org.mr0btain.ticketman.data.user.User

/**
 * First login setup that's waiting to be finished
 */
sealed interface AccountSetup {
    /** Approved request, they're logged in and confirm their details */
    data class Approved(val user: User) : AccountSetup

    /** Used an invite code, they pick a password and fill in their details */
    data class Invite(val username: String, val setupToken: String) : AccountSetup
}
