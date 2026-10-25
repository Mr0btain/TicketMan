package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.PaymentMethod

/**
 * How a day went, for closing up. Days are in the server's time zone
 * @param date The day, as yyyy-mm-dd
 * @param takenByMethod Money taken that day by payment method, deposits included, less refunds given that way
 * @param refundedCents Refunds given that day, as a positive amount
 * @param salesCents Totals of invoices paid off that day
 * @param overdueNow Open tickets past their promised date when the report was made
 * @param waitingForPickupNow Finished tickets not picked up yet when the report was made
 */
@Serializable
data class DailyReport(
    val date: String,
    val takenByMethod: Map<PaymentMethod, Long> = emptyMap(),
    val paymentCount: Int = 0,
    val refundedCents: Long = 0,
    val refundCount: Int = 0,
    val salesCents: Long = 0,
    val taxCents: Long = 0,
    val invoicesPaid: Int = 0,
    val ticketsCheckedIn: Int = 0,
    val ticketsPickedUp: Int = 0,
    val overdueNow: Int = 0,
    val waitingForPickupNow: Int = 0
) {
    /** Money taken, leaving out store credit and gift cards spent, which were money when they were bought */
    val takenCents: Long
        get() = takenByMethod.filterKeys { !it.storedValue }.values.sum()
}
