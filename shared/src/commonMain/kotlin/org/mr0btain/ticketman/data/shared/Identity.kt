package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Small wrapper class for a basic person and the identifier for that person in the system
 * @param identifier The UUID pointing to the person.
 * @param text The name of the person in plain text form.
 */
@Serializable
data class Identity(
    val identifier: Uuid,
    val text: String,
    ) {
    override fun toString(): String = text
}