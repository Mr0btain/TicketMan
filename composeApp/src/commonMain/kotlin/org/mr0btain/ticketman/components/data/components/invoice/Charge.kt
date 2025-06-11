package org.mr0btain.ticketman.components.data.components.invoice

data class Charge(
    val type: ChargeType,
    val cost: Int,
    val description: String
)

