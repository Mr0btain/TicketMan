package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable

/**
 * What `GET /api/invoice/export` writes
 * @param fileName Start of the saved file's name, the days are added
 */
@Serializable
enum class ExportFormat(val label: String, val fileName: String) {
    /** One row per invoice made in the days, with its totals and what's been paid. For a spreadsheet */
    INVOICES("Invoices", "invoices"),

    /** One row per line of each invoice paid in the days, in QuickBooks Online's invoice import columns */
    QUICKBOOKS("QuickBooks", "quickbooks-invoices"),

    /** One row per line of each invoice paid in the days, in Xero's sales invoice import columns */
    XERO("Xero", "xero-invoices"),

    /** One row per payment or refund received in the days, for matching against the bank */
    PAYMENTS("Payments", "payments")
}

/** How dates are written in the accounting exports, to match what the books expect */
@Serializable
enum class ExportDateFormat(val label: String) {
    /** 10/27/2026, QuickBooks and Xero in the US */
    MONTH_FIRST("MM/DD/YYYY"),

    /** 27/10/2026, Xero and QuickBooks most other places */
    DAY_FIRST("DD/MM/YYYY"),

    /** 2026-10-27 */
    ISO("YYYY-MM-DD");

    fun format(year: Int, month: Int, day: Int): String {
        val mm = month.toString().padStart(2, '0')
        val dd = day.toString().padStart(2, '0')
        return when (this) {
            MONTH_FIRST -> "$mm/$dd/$year"
            DAY_FIRST -> "$dd/$mm/$year"
            ISO -> "$year-$mm-$dd"
        }
    }
}

/**
 * The names the shop's books use, for the QuickBooks and Xero exports. Kept in the shop profile
 * @param laborItem QuickBooks product or service for labor lines
 * @param partsItem For part lines
 * @param otherItem For other lines, including storage fees
 * @param giftCardItem For gift cards sold
 * @param salesAccount Xero account code sales go to
 * @param giftCardAccount Xero account code for gift cards sold, money held for customers. Blank uses [salesAccount]
 * @param taxType Xero tax rate name on taxed lines
 * @param exemptTaxType Xero tax rate name on lines that aren't taxed
 * @param classTaxTypes Xero tax rate name for each of the shop's tax classes, by class id, when it isn't [taxType]
 * @param walkInCustomer The customer name on counter sales without one
 * @param dueDays Days after the invoice date it's due, 0 for the same day
 */
@Serializable
data class AccountingSettings(
    val laborItem: String = "Labor",
    val partsItem: String = "Parts",
    val otherItem: String = "Other",
    val giftCardItem: String = "Gift card",
    val salesAccount: String = "200",
    val giftCardAccount: String = "",
    val taxType: String = "Tax on Sales",
    val exemptTaxType: String = "Tax Exempt",
    val classTaxTypes: Map<String, String> = emptyMap(),
    val walkInCustomer: String = "Walk-in",
    val dueDays: Int = 0,
    val dateFormat: ExportDateFormat = ExportDateFormat.MONTH_FIRST
) {
    /** What's wrong with them, null when they're fine */
    fun problem(): String? = when {
        dueDays !in 0..MAX_DUE_DAYS -> "Due within 0 to $MAX_DUE_DAYS days"
        listOf(laborItem, partsItem, otherItem, giftCardItem, salesAccount, taxType, exemptTaxType, walkInCustomer).any { it.isBlank() } ->
            "Fill in every name the books need"
        (listOf(laborItem, partsItem, otherItem, giftCardItem, salesAccount, giftCardAccount, taxType, exemptTaxType, walkInCustomer) + classTaxTypes.values)
            .any { it.length > MAX_NAME } -> "Names can be up to $MAX_NAME characters"
        else -> null
    }

    /** The QuickBooks item for a line */
    fun item(type: ChargeType): String = when (type) {
        ChargeType.LABOR -> laborItem
        ChargeType.PART -> partsItem
        ChargeType.OTHER -> otherItem
        ChargeType.GIFT_CARD -> giftCardItem
    }

    /** The Xero account for a line */
    fun account(type: ChargeType): String =
        if (type == ChargeType.GIFT_CARD) giftCardAccount.ifBlank { salesAccount } else salesAccount

    /** The Xero tax rate for a line */
    fun taxType(charge: Charge): String = when {
        !charge.taxable || charge.type == ChargeType.GIFT_CARD -> exemptTaxType
        else -> charge.taxClass?.let { classTaxTypes[it] }?.takeIf { it.isNotBlank() } ?: taxType
    }

    companion object {
        const val MAX_DUE_DAYS = 365
        const val MAX_NAME = 100
    }
}
