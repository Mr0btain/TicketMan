package org.mr0btain.ticketman.data.shop

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.money.Money

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
    val repeatWindowDays: Int = DEFAULT_REPEAT_WINDOW_DAYS,
    /** What customers agree to when they sign at check in, blank skips the signature */
    val repairTerms: String = "",
    /** Paper the counter prints on unless someone picks another */
    val defaultPaper: PaperSize = PaperSize.RECEIPT_80MM,
    /** Put before every amount shown or printed, like "$" or "£" */
    val currencySymbol: String = Money.DEFAULT_SYMBOL
) {
    companion object {
        const val DEFAULT_REPEAT_WINDOW_DAYS = 90
    }
}

/** Paper the counter can print slips and receipts on */
@Serializable
enum class PaperSize(val label: String) {
    RECEIPT_80MM("80mm receipt"),
    LETTER("Letter")
}
