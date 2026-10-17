package org.mr0btain.ticketman.data.shop

import kotlinx.serialization.Serializable

/**
 * Shop details used on invoices and anything else customer facing
 */
@Serializable
data class ShopProfile(
    val name: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val taxRate: String = "",
    val invoiceFooter: String = "",
    /** A device back within this many days of its last repair gets flagged at check in */
    val repeatWindowDays: Int = DEFAULT_REPEAT_WINDOW_DAYS
) {
    companion object {
        const val DEFAULT_REPEAT_WINDOW_DAYS = 90
    }
}
