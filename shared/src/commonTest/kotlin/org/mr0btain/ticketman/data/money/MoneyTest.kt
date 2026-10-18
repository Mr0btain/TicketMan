package org.mr0btain.ticketman.data.money

import kotlin.test.*

class MoneyTest {
    @Test
    fun formatsCents() {
        assertEquals("$0.00", Money.format(0))
        assertEquals("$0.05", Money.format(5))
        assertEquals("$12.34", Money.format(1234))
        assertEquals("$1234.50", Money.format(123_450))
        assertEquals("-$25.00", Money.format(-2500))
    }

    @Test
    fun readsWhatPeopleType() {
        assertEquals(1250, Money.parse("12.5"))
        assertEquals(1250, Money.parse("$12.50"))
        assertEquals(123_456, Money.parse("$1,234.56"))
        assertEquals(1200, Money.parse(" 12 "))
        assertEquals(-500, Money.parse("-$5"))
        assertEquals(1235, Money.parse("$12.3456"))
    }

    @Test
    fun noNumberIsNull() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("$"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun everyPriceSurvivesTextAndDollars() {
        // Doubles can't hold most cents exactly, make sure none of them drift by a cent on the way back
        for (cents in 0L..200_000L) {
            assertEquals(cents, Money.parse(Money.format(cents)), "text for $cents")
            assertEquals(cents, Money.fromDollars(Money.toDollars(cents)), "dollars for $cents")
        }
    }

    @Test
    fun taxRatesAreBasisPoints() {
        assertEquals(750, Money.percentToBasisPoints("7.5"))
        assertEquals(825, Money.percentToBasisPoints(" 8.25% "))
        assertEquals(0, Money.percentToBasisPoints("0"))
        assertEquals(10_000, Money.percentToBasisPoints("100"))
        assertNull(Money.percentToBasisPoints("100.01"))
        assertNull(Money.percentToBasisPoints("-1"))
        assertNull(Money.percentToBasisPoints("seven"))
    }

    @Test
    fun ratesRoundHalfUpToTheCent() {
        assertEquals(83, Money.applyRate(1000, 825))
        assertEquals(82, Money.applyRate(999, 825))
        assertEquals(0, Money.applyRate(1000, 0))
        assertEquals(1000, Money.applyRate(1000, 10_000))
    }
}
