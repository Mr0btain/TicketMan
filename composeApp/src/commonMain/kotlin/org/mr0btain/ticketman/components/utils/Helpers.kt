package org.mr0btain.ticketman.components.utils

import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.name_divider_character

/**
 * Helper function to split a name up based on the divider we have chosen.
 */
fun splitName(name: String): List<String> {
    println(Res.string.name_divider_character)
    println(Res.string.name_divider_character.toString())
    return name.split(Res.string.name_divider_character.toString())
}