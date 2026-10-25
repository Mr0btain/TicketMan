package org.mr0btain.ticketman.data.invoice

import kotlin.test.*

class InvoiceMathTest {
    private val screen = Charge(type = ChargeType.PART, quantity = 1.0, unitPriceCents = 12_000)
    private val labor = Charge(type = ChargeType.LABOR, quantity = 1.5, unitPriceCents = 6_000, taxable = false)

    @Test
    fun chargesAreQuantityTimesPrice() {
        assertEquals(9_000, labor.totalCents)
        assertEquals(333, Charge(quantity = 0.33, unitPriceCents = 1_010).totalCents)
    }

    @Test
    fun taxIsOnlyOnTaxableLines() {
        val totals = InvoiceMath.totals(listOf(screen, labor), discountCents = 0, taxRateBasisPoints = 825)

        assertEquals(
            InvoiceMath.Totals(
                subtotalCents = 21_000, discountCents = 0, taxCents = 990, totalCents = 21_990,
                taxLines = listOf(TaxLine("Tax", 825, taxableCents = 12_000, taxCents = 990))
            ),
            totals
        )
    }

    @Test
    fun theDiscountComesOffTheTaxableLinesFirst() {
        val totals = InvoiceMath.totals(listOf(screen, labor), discountCents = 2_000, taxRateBasisPoints = 825)

        // Tax on 120 - 20 = 100
        assertEquals(825, totals.taxCents)
        assertEquals(21_000 - 2_000 + 825, totals.totalCents)
    }

    @Test
    fun aDiscountBiggerThanTheTaxableLinesLeavesNoTax() {
        val totals = InvoiceMath.totals(listOf(screen, labor), discountCents = 15_000, taxRateBasisPoints = 825)

        assertEquals(0, totals.taxCents)
        assertEquals(6_000, totals.totalCents)
    }

    @Test
    fun aDiscountCantGoBelowZeroOrPastTheSubtotal() {
        assertEquals(0, InvoiceMath.totals(listOf(screen), discountCents = -500, taxRateBasisPoints = 0).discountCents)

        val all = InvoiceMath.totals(listOf(screen), discountCents = 50_000, taxRateBasisPoints = 825)
        assertEquals(12_000, all.discountCents)
        assertEquals(0, all.totalCents)
    }

    @Test
    fun aClassTaxesItsLinesAtItsOwnRate() {
        val food = Charge(description = "Snack", unitPriceCents = 1_000, taxClass = "food")
        val totals = InvoiceMath.totals(listOf(screen, food), discountCents = 0, taxRateBasisPoints = 825, taxRates = listOf(TaxRate("food", "Food", 200)))

        assertEquals(listOf("Tax, 8.25%", "Food, 2.0%"), totals.taxLines.map { it.label })
        assertEquals(990 + 20, totals.taxCents)
    }

    @Test
    fun anEmptyInvoiceIsZero() {
        assertEquals(InvoiceMath.Totals(0, 0, 0, 0), InvoiceMath.totals(emptyList(), 0, 825))
    }
}
