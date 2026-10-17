package org.mr0btain.ticketman.data.user

/**
 * What a username can look like, checked by both the client and the server
 */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 32

    private val ALLOWED = Regex("^[a-z0-9._-]+$")

    /** Problem with the username, or null if it's fine. Usernames are stored lowercase */
    fun problem(username: String): String? {
        val name = username.trim().lowercase()

        return when {
            name.length < MIN_LENGTH -> "Username needs at least $MIN_LENGTH characters"
            name.length > MAX_LENGTH -> "Username can't be over $MAX_LENGTH characters"
            !ALLOWED.matches(name) -> "Username can only have letters, numbers, dots, dashes and underscores"
            else -> null
        }
    }
}
