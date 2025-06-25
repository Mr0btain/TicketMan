package org.mr0btain.ticketman.components.database

import java.util.UUID

data class RefreshToken(
    val identifier: UUID,
    val token: String
)
