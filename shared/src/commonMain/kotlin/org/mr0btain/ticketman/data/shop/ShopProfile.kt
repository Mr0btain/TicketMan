package org.mr0btain.ticketman.data.shop

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.CustomStatus

/**
 * Shop details used on invoices and anything else customer facing
 * @param publicUrl Where customers reach this server from outside the shop, like `https://repairs.example.com`,
 * used for status links. Blank until the shop sets it
 * @param statuses The shop's own ticket statuses, retired ones included (see [CustomStatus])
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
    val currencySymbol: String = Money.DEFAULT_SYMBOL,
    /** Per hour, for time logged on a ticket's work log. 0 until it's set */
    val laborRateCents: Long = 0,
    val publicUrl: String = "",
    val statuses: List<CustomStatus> = emptyList()
) {
    companion object {
        const val DEFAULT_REPEAT_WINDOW_DAYS = 90

        private val PUBLIC_URL = Regex("""https?://[^\s/?#]+(/[^\s?#]*)?""")

        /** Problem with a public address, or null if it's fine. Blank is fine, it just isn't set */
        fun publicUrlProblem(url: String): String? = when {
            url.isBlank() -> null
            !PUBLIC_URL.matches(url.trim()) -> "Public address has to look like https://repairs.example.com"
            else -> null
        }
    }
}

/** Paper the counter can print slips and receipts on */
@Serializable
enum class PaperSize(val label: String) {
    RECEIPT_80MM("80mm receipt"),
    LETTER("Letter")
}
