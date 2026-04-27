package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID

/**
 * Small wrapper class for a basic person and the identifier for that person in the system
 * @param identifier The UUID pointing to the person.
 * @param text The name of the person in plain text form.
 */
@Serializable
data class Identity(
    @Serializable(UUIDSerializer:: class)
    val identifier: UUID,
    val text: String,
    ) {
    override fun toString(): String = text
}