package org.mr0btain.ticketman.data.user

/**
 * The code on a user's ID card, in its QR. Random and separate from the user's id, so a lost card is cancelled by
 * making a new code, and nothing else about the user is on the card. Nothing scans it yet; clocking in will
 */
object BadgeCode {
    /** What the QR starts with, so a scanner can tell a badge from a ticket's number */
    const val PREFIX = "TMBADGE:"

    /** Crockford's base 32: no I, L, O or U, so it reads back without mix ups */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** 16 characters, 80 random bits */
    const val LENGTH = 16

    /** What goes in the QR for [code] */
    fun qrText(code: String): String = PREFIX + code

    /** The code in a scanned QR, null when it isn't a badge */
    fun parse(text: String): String? =
        text.trim().takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
            ?.uppercase()?.takeIf { code -> code.length == LENGTH && code.all { it in ALPHABET } }

    /** [code] split in fours to read off, like `7K3M-9QPA-...` */
    fun readable(code: String): String = code.chunked(4).joinToString("-")
}
