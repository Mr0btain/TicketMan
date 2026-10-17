package org.mr0btain.ticketman.data.invoice

import org.mr0btain.ticketman.data.money.Money

/**
 * Works out invoice totals the same way on the client (for previews) and server (for what's saved)
 */
object InvoiceMath {
    data class Totals(
        val subtotalCents: Long,
        val discountCents: Long,
        val taxCents: Long,
        val totalCents: Long
    )

    /**
     * The discount comes off the taxable lines first, then tax is charged on what's left of them.
     * A discount can't take the total below zero
     */
    fun totals(charges: List<Charge>, discountCents: Long, taxRateBasisPoints: Int): Totals {
        val subtotal = charges.sumOf { it.totalCents }
        val taxable = charges.filter { it.taxable }.sumOf { it.totalCents }
        val discount = discountCents.coerceIn(0, subtotal)

        val taxBase = (taxable - discount).coerceAtLeast(0)
        val tax = Money.applyRate(taxBase, taxRateBasisPoints)

        return Totals(
            subtotalCents = subtotal,
            discountCents = discount,
            taxCents = tax,
            totalCents = subtotal - discount + tax
        )
    }
}
