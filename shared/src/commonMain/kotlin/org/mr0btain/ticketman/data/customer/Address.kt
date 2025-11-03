package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class Address(
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = ""
): SubItem {
    override fun toString(): String {
        return "$street $city $state $zip"
    }

    companion object {
        fun fromString(address: String): Address {
            val result = address.split(" ").map { it.trim() }

            return Address(
                street = result.getOrElse(0, { "" }),
                city = result.getOrElse(1, { "" }),
                state = result.getOrElse(2, { "" }),
                zip = result.getOrElse(3, { "" })
            )
        }
    }
}