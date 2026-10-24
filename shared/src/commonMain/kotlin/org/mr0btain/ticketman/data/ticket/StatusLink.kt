package org.mr0btain.ticketman.data.ticket

import org.mr0btain.ticketman.server.ApiRoutes

/**
 * The customer's link to their ticket's status page, served by the server at `/status/{token}`
 */
object StatusLink {
    /** Hex characters in a status token: 16 random bytes */
    const val TOKEN_LENGTH = 32

    private val TOKEN = Regex("""^[0-9a-f]{$TOKEN_LENGTH}$""")

    // Anywhere in a scanned or pasted link, like https://repairs.example.com/status/<token>
    private val IN_LINK = Regex("""${ApiRoutes.STATUS}/([0-9a-f]{$TOKEN_LENGTH})(?![0-9a-f])""", RegexOption.IGNORE_CASE)

    /**
     * The link for [ticket] at the shop's [publicUrl], null until the shop has one or the ticket has a token
     */
    fun url(publicUrl: String, ticket: Ticket): String? {
        val base = publicUrl.trim().trimEnd('/')
        val token = ticket.statusToken

        return if (base.isBlank() || token.isNullOrBlank()) null else "$base${ApiRoutes.STATUS}/$token"
    }

    fun isToken(text: String): Boolean = TOKEN.matches(text)

    /** The token in a status link, or null when [text] isn't one, for the counter search taking a scanned slip */
    fun tokenIn(text: String): String? = IN_LINK.find(text)?.groupValues?.get(1)?.lowercase()
}
