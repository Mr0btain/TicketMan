package org.mr0btain.ticketman.components.utils

/** Character between name parts in stored names, matches User.cleanName and the name_divider_character string */
const val NAME_DIVIDER = ":"

/**
 * Helper function to split a name up based on the divider we have chosen.
 */
fun splitName(name: String): List<String> =
    name.split(NAME_DIVIDER)

/** Opposite of [splitName] */
fun joinName(parts: List<String>): String =
    parts.joinToString(NAME_DIVIDER)