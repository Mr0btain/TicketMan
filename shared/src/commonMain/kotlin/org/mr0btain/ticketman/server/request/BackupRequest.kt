package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

/**
 * Asks the server to build a backup archive
 * @param includeImages Photos too, which is most of the size on a busy shop. Off gives a quick database only copy
 */
@Serializable
data class BackupRequest(
    val includeImages: Boolean = true
)
