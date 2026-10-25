package org.mr0btain.ticketman.data.giftcard

import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * A gift card the shop sold or gave, spent as a payment by its [code]
 * @param issuedCents Everything loaded onto it, the first sale and any top ups
 * @param invoiceIdentifier The invoice that first loaded it, null for one an admin made
 * @param createdAt Epoch millis
 */
@Serializable
data class GiftCard(
    val identifier: Uuid,
    val code: String,
    val balanceCents: Long,
    val issuedCents: Long,
    val createdAt: Long,
    val invoiceIdentifier: Uuid? = null,
    val customerIdentifier: Uuid? = null
)

/**
 * Gift card codes: what's typed or scanned is cleaned up the same way everywhere, and new ones are long enough not
 * to be guessed
 */
object GiftCardCode {
    // No 0/O or 1/I, so a code read out over the counter is typed right
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val VALID = Regex("""[A-Z0-9-]{6,32}""")

    /** A new code like GC-7KQ2-M9XD-4HTP-WR8B */
    fun generate(random: Random = Random.Default): String =
        "GC-" + (1..4).joinToString("-") { (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }

    /** Upper case, no spaces, so "gc 7kq2-m9xd" finds GC7KQ2-M9XD */
    fun normalize(input: String): String = input.filterNot { it.isWhitespace() }.uppercase()

    /** What's wrong with a code someone typed, or null */
    fun problem(code: String): String? =
        if (VALID.matches(normalize(code))) null else "A gift card code is 6 to 32 letters, numbers and dashes"
}
