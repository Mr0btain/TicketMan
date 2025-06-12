package org.mr0btain.ticketman.components.data.components.invoice

import kotlinx.serialization.Serializable

@Serializable
data class Charge(
    val type: ChargeType,
    val cost: Int,
    val description: String
)

