package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*
import java.util.Locale
import java.util.Locale.getDefault

@Serializable
data class Customer(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val name: String = "",
    val address: Address = Address(),
    val email: String = "",
    val phone: String = ""
): MainItem {
    fun cleanName(): String {
        return name.split(":").joinToString(" ") { n ->
            n.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString()
            }
        }
    }
}