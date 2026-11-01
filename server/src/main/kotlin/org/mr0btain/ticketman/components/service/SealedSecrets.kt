package org.mr0btain.ticketman.components.service

/**
 * Keeps the keys in the saved settings (mail password, text and card processor tokens) sealed under the server's
 * secret, so the database and its backups don't hold them readable. Read with a different secret they come back as
 * not set, for an admin to enter again. Like the database itself this is one per server
 */
object SealedSecrets {
    private const val PREFIX = "sealed:"

    /** Set on start, once the secret is known. Until then values pass through as they are, like in tests of parts */
    @Volatile
    var vault: PasscodeVault? = null

    /** [value] sealed, unless it's empty or sealed already */
    fun seal(value: String?): String? {
        val vault = vault
        return if (value.isNullOrEmpty() || vault == null || value.startsWith(PREFIX)) value else PREFIX + vault.seal(value)
    }

    /** What [seal] was given. Null when it can't be opened here, and as it is when it was never sealed */
    fun open(value: String?): String? =
        if (value == null || !value.startsWith(PREFIX)) value else vault?.open(value.removePrefix(PREFIX))
}
