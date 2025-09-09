package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class PersonName(
    val firstName: String = "",
    val lastName: String = ""
): SubItem

fun PersonName.toStringName(): String {
    return "$firstName $lastName"
}