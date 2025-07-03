package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable

@Serializable
sealed interface MainDataItem {
    val identifier: String
}