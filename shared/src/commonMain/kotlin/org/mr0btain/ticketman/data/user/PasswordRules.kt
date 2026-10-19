package org.mr0btain.ticketman.data.user

/**
 * What a password has to be, checked by both the client and the server
 */
object PasswordRules {
    const val MIN_LENGTH = 8

    /** Problem with the password, or null if it's fine */
    fun problem(password: String): String? =
        if (password.length < MIN_LENGTH) "Password needs at least $MIN_LENGTH characters" else null
}
