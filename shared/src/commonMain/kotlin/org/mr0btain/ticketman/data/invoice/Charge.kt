package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable

@Serializable
data class Charge(
    val type: ChargeType,
    val name: String,
    val amount: Int,
    val cost: Int,
    val description: String
)

