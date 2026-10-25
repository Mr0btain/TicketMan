package org.mr0btain.ticketman.data.invoice

import org.mr0btain.ticketman.data.money.Money

/**
 * Works out invoice totals the same way on the client (for previews) and server (for what's saved)
 */
object InvoiceMath {
    /**
     * @param taxLines The tax on each group of lines, the shop's main rate first then each class used. Empty when
     * nothing is taxed
     */
    data class Totals(
        val subtotalCents: Long,
        val discountCents: Long,
        val taxCents: Long,
        val totalCents: Long,
        val taxLines: List<TaxLine> = emptyList()
    )

    /**
     * The discount comes off the taxable lines first, then tax is charged on what's left of them. Lines are taxed
     * at their class's rate from [taxRates], or [taxRateBasisPoints] when they have no class or one that isn't
     * there; when there's more than one rate the discount is shared between them by how much each is charged on.
     * A discount can't take the total below zero
     */
    fun totals(charges: List<Charge>, discountCents: Long, taxRateBasisPoints: Int, taxRates: List<TaxRate> = emptyList()): Totals {
        val subtotal = charges.sumOf { it.totalCents }
        val taxableLines = charges.filter { it.taxable }
        val taxable = taxableLines.sumOf { it.totalCents }
        val discount = discountCents.coerceIn(0, subtotal)

        // The main rate's group first, then the classes in the shop's order
        val groups = taxableLines
            .groupBy { line -> taxRates.firstOrNull { it.identifier == line.taxClass } }
            .entries
            .sortedBy { (rate, _) -> rate?.let { taxRates.indexOf(it) + 1 } ?: 0 }

        val taxDiscount = discount.coerceAtMost(taxable)
        var discountLeft = taxDiscount
        val taxLines = groups.mapIndexed { index, (rate, lines) ->
            val base = lines.sumOf { it.totalCents }
            // The last group takes what rounding left, so the shares add up to the discount
            val share = if (index == groups.lastIndex) discountLeft else if (taxable == 0L) 0 else taxDiscount * base / taxable
            discountLeft -= share

            val after = (base - share).coerceAtLeast(0)
            val basisPoints = rate?.basisPoints ?: taxRateBasisPoints
            TaxLine(rate?.name ?: "Tax", basisPoints, after, Money.applyRate(after, basisPoints))
        }
        val tax = taxLines.sumOf { it.taxCents }

        return Totals(
            subtotalCents = subtotal,
            discountCents = discount,
            taxCents = tax,
            totalCents = subtotal - discount + tax,
            taxLines = taxLines
        )
    }
}
