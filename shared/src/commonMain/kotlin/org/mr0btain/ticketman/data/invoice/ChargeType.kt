package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable

@Serializable
enum class ChargeType {
    /** Time spent on the repair */
    LABOR,

    /** An inventory item, its stock goes down when the invoice is paid */
    PART,

    OTHER,

    /** Selling or topping up a gift card, loaded when the invoice is paid. Never taxed */
    GIFT_CARD
}
