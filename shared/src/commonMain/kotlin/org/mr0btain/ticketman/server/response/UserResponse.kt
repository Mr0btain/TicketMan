package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class UserResponse @OptIn(ExperimentalUuidApi::class) constructor(
    val identifier: Uuid,
    val accessLevel: AccessLevel
)