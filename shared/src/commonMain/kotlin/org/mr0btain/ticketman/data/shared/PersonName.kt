package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class PersonName(
    val firstName: String = "",
    val lastName: String = ""
): SubItem {
    override fun toString(): String {
        return "$firstName $lastName"
    }

    companion object {
        fun fromString(name: String): PersonName {
            val split = name.split(" ")
                .filter { it.isNotBlank() }

            val first = split.getOrNull(0) ?: ""
            val last = split.getOrNull(1) ?: ""

            return PersonName(first, last)
        }
    }
}
