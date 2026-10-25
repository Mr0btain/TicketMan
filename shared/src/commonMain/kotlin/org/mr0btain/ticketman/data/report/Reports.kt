package org.mr0btain.ticketman.data.report

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.invoice.TaxLine
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * How the shop did between two days, both included, in the shop's time zone. Sales are invoices paid in those days
 * (void ones left out); money taken and refunds are payments received in them; techs are the work log's lines on
 * those invoices; turnaround is tickets picked up in them
 * @param from First day, like 2026-10-01
 * @param to Last day
 */
@Serializable
data class PeriodReport(
    val from: String,
    val to: String,
    val sales: SalesSummary = SalesSummary(),
    val days: List<DaySales> = emptyList(),
    val taxes: List<TaxLine> = emptyList(),
    val takenByMethod: Map<PaymentMethod, Long> = emptyMap(),
    val refunds: RefundSummary = RefundSummary(),
    val techs: List<TechSummary> = emptyList(),
    val turnaround: Turnaround = Turnaround()
) {
    /** Money taken, leaving out store credit and gift cards spent, which were money when they were bought */
    val takenCents: Long
        get() = takenByMethod.filterKeys { !it.storedValue }.values.sum()
}

/**
 * Paid invoices added up. Line totals are before the discount; [discountCents] is the discounts given
 * @param giftCardCents Gift cards sold, which are money held for the customer rather than sales
 * @param partsCostCents What the parts sold cost the shop, from each item's cost when the invoice was paid
 * @param partsWithoutCost Part lines whose cost isn't known (paid before costs were kept, or the item has none)
 */
@Serializable
data class SalesSummary(
    val invoices: Int = 0,
    val totalCents: Long = 0,
    val taxCents: Long = 0,
    val discountCents: Long = 0,
    val laborCents: Long = 0,
    val partsCents: Long = 0,
    val otherCents: Long = 0,
    val giftCardCents: Long = 0,
    val partsCostCents: Long = 0,
    val partsWithoutCost: Int = 0
) {
    /** Sales before tax, after discounts, without gift cards */
    val netCents: Long
        get() = totalCents - taxCents - giftCardCents

    /** Net sales less what the parts cost. Refunds aren't taken off, they're in [PeriodReport.refunds] */
    val grossProfitCents: Long
        get() = netCents - partsCostCents
}

/** One day's paid invoices */
@Serializable
data class DaySales(
    val date: String,
    val invoices: Int = 0,
    val totalCents: Long = 0
)

/**
 * Money given back in the period
 * @param cents A positive amount
 */
@Serializable
data class RefundSummary(
    val count: Int = 0,
    val cents: Long = 0,
    val refunds: List<RefundLine> = emptyList()
)

/**
 * One refund
 * @param amountCents Positive
 * @param receivedAt Epoch millis
 */
@Serializable
data class RefundLine(
    val invoiceLabel: String,
    val amountCents: Long,
    val reason: String = "",
    val method: PaymentMethod,
    val receivedAt: Long = 0,
    val receivedBy: String = ""
)

/**
 * A tech's billed work: work log lines (by who logged them) on invoices paid in the period, at the price they were
 * billed at, so free warranty work counts its hours and nothing else
 * @param userIdentifier Null for work by a user since deleted
 * @param commissionBasisPoints Their commission on labor, 1000 is 10%, see [CommissionRate]
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TechSummary(
    val userIdentifier: Uuid? = null,
    val name: String = "",
    val tickets: Int = 0,
    val laborHours: Double = 0.0,
    val laborCents: Long = 0,
    val partsCents: Long = 0,
    val commissionBasisPoints: Int = 0
) {
    /** Their commission on the labor billed, rounded to the cent */
    val commissionCents: Long
        get() = (laborCents * commissionBasisPoints / 10_000.0).roundToLong()
}

/**
 * How long tickets picked up in the period took, in hours. Ready is the first time a ticket went to a status that
 * counts as done; tickets never marked ready are left out of those numbers
 */
@Serializable
data class Turnaround(
    val pickedUp: Int = 0,
    val readyCount: Int = 0,
    val averageHoursToReady: Double? = null,
    val medianHoursToReady: Double? = null,
    val averageHoursToPickup: Double? = null,
    val medianHoursToPickup: Double? = null
)

/**
 * A user's commission on the labor they bill, kept apart from users so only admins see it
 * @param basisPoints 1000 is 10%, from 0 to [MAX_BASIS_POINTS]
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CommissionRate(
    val userIdentifier: Uuid,
    val basisPoints: Int
) {
    companion object {
        const val MAX_BASIS_POINTS = 10_000
    }
}

/** Limits on a report's days */
object ReportRules {
    /** The most days one report covers */
    const val MAX_DAYS = 732

    /** How many refunds the report lists, newest first */
    const val REFUND_LIST_LIMIT = 100
}
