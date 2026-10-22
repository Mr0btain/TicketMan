package org.mr0btain.ticketman.data.invoice

import kotlin.test.*

/** Invoice edits sent as changes, so two people on one invoice don't undo each other */
class InvoiceChangesTest {
    private val screen = Charge(description = "Screen", unitPriceCents = 12_000)
    private val labor = Charge(type = ChargeType.LABOR, description = "Install", quantity = 0.5, unitPriceCents = 6_000)
    private val logged = Charge(type = ChargeType.LABOR, description = "Diagnosis", quantity = 1.0, unitPriceCents = 6_000)

    private fun applied(outcome: InvoiceChanges.Outcome) = assertIs<InvoiceChanges.Outcome.Applied>(outcome)

    @Test
    fun onlyWhatChangedIsSent() {
        val cable = Charge(description = "Cable", unitPriceCents = 900)
        val cheaper = screen.copy(unitPriceCents = 11_000)
        val changes = InvoiceChanges.between(listOf(screen, labor), 0, listOf(cheaper, cable), 500)

        assertEquals(listOf(cable), changes.added)
        assertEquals(screen, changes.changed.single().before)
        assertEquals(cheaper, changes.changed.single().after)
        assertEquals(listOf(labor), changes.removed)
        assertEquals(500, changes.discountCents)

        assertTrue(InvoiceChanges.between(listOf(screen), 0, listOf(screen), 0).isEmpty)
    }

    @Test
    fun aLineAddedMeanwhileStays() {
        // The counter opened it with the screen and labor, then work was logged before it saved
        val cable = Charge(description = "Cable", unitPriceCents = 900)
        val changes = InvoiceChanges.between(listOf(screen, labor), 0, listOf(screen.copy(unitPriceCents = 11_000), cable), 0)

        val result = applied(InvoiceChanges.apply(listOf(screen, labor, logged), 0, changes))
        assertEquals(listOf(screen.copy(unitPriceCents = 11_000), logged, cable), result.charges)
    }

    @Test
    fun aLineChangedOnBothSidesIsAConflict() {
        val changes = InvoiceChanges.between(listOf(screen), 0, listOf(screen.copy(unitPriceCents = 11_000)), 0)

        val other = screen.copy(unitPriceCents = 10_000)
        assertIs<InvoiceChanges.Outcome.Conflict>(InvoiceChanges.apply(listOf(other), 0, changes))
        assertIs<InvoiceChanges.Outcome.Conflict>(InvoiceChanges.apply(emptyList(), 0, changes), "Removed meanwhile")

        val removing = InvoiceChanges.between(listOf(screen), 0, emptyList(), 0)
        assertIs<InvoiceChanges.Outcome.Conflict>(InvoiceChanges.apply(listOf(other), 0, removing))
        assertEquals(emptyList(), applied(InvoiceChanges.apply(emptyList(), 0, removing)).charges, "Gone already is fine")
    }

    @Test
    fun theDiscountIsCheckedLikeALine() {
        val changes = InvoiceChanges.between(listOf(screen), 0, listOf(screen), 500)

        assertEquals(500, applied(InvoiceChanges.apply(listOf(screen), 0, changes)).discountCents)
        assertIs<InvoiceChanges.Outcome.Conflict>(InvoiceChanges.apply(listOf(screen), 300, changes))
        assertEquals(500, applied(InvoiceChanges.apply(listOf(screen), 500, changes)).discountCents, "Same change both sides")

        // Not touched here, so someone else's discount stays
        val lineOnly = InvoiceChanges.between(listOf(screen), 0, listOf(screen, labor), 0)
        assertEquals(300, applied(InvoiceChanges.apply(listOf(screen), 300, lineOnly)).discountCents)
    }

    @Test
    fun sendingTheSameAddTwiceAddsItOnce() {
        val changes = InvoiceChanges.between(emptyList(), 0, listOf(screen), 0)
        assertEquals(listOf(screen), applied(InvoiceChanges.apply(listOf(screen), 0, changes)).charges)
    }
}
