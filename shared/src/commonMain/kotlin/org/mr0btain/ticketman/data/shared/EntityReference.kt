package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Small wrapper class for a basic entity that has an identifier in the system
 * @param identifier The UUID pointing to the person.
 * @param text The name of the person in plain text form.
 */
@Serializable
data class EntityReference(
    val identifier: Uuid,
    val text: String,
    ) {
    override fun toString(): String = text
}