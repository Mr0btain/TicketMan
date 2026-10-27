package org.mr0btain.ticketman.data.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CardExtrasTest {
    @Test
    fun surchargesRoundToTheNearestCent() {
        assertEquals(300, CardExtras.surchargeCents(10_000, "3"))
        assertEquals(250, CardExtras.surchargeCents(10_000, "2.5"))
        assertEquals(42, CardExtras.surchargeCents(1_699, "2.5")) // 42.475
        assertEquals(43, CardExtras.surchargeCents(1_700, "2.5")) // 42.5, half up
        assertEquals(0, CardExtras.surchargeCents(10_000, ""))
        assertEquals(0, CardExtras.surchargeCents(10_000, "nope"))
        assertEquals(300, CardExtras.surchargeCents(10_000, " 3 % "))
    }

    @Test
    fun surchargesAreCapped() {
        assertNull(CardExtras.surchargeProblem("3"))
        assertNull(CardExtras.surchargeProblem(""))
        assertNotNull(CardExtras.surchargeProblem("3.01"))
        assertNotNull(CardExtras.surchargeProblem("abc"))
        assertNotNull(CardExtras.surchargeProblem("1.234"))
    }

    @Test
    fun tipsArePercentsOfTheAmount() {
        assertEquals(750, CardExtras.tipCents(5_000, 15))
        assertEquals(300, CardExtras.tipCents(1_499, 20)) // 299.8
        assertEquals(0, CardExtras.tipCents(5_000, 0))
    }
}
