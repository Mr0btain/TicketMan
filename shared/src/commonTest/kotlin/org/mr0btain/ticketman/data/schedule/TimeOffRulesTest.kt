package org.mr0btain.ticketman.data.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeOffRulesTest {
    private fun carried(allowance: TimeOffAllowance, taken: (Int) -> Double = { 0.0 }) = TimeOffRules.carriedOver(2026, allowance, taken)

    @Test
    fun noCapStartsOverEachYear() {
        assertEquals(0.0, carried(TimeOffAllowance(80.0)))
        assertEquals(0.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 0.0)))
    }

    @Test
    fun whatsUnusedCarriesUpToTheCap() {
        assertEquals(24.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 24.0)))
        // Used 70 of 80 last year and everything before that, the 10 left is under the cap
        assertEquals(10.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 40.0)) { if (it == 2025) 70.0 else 80.0 })
        // Carried hours go first, so a little left each year builds up until it reaches the cap
        assertEquals(40.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 40.0)) { 70.0 })
    }

    @Test
    fun usingAllOfItCarriesNothing() {
        assertEquals(0.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 40.0)) { 200.0 })
    }

    @Test
    fun floatingHolidaysGoFirstAndDontCarry() {
        // 8 taken, all floating: the whole allowance is unused but the cap keeps 16
        assertEquals(16.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 16.0, floatingHours = 8.0)) { 8.0 })
        // Nothing taken: the unused floating hours aren't added to what carries
        assertEquals(40.0, carried(TimeOffAllowance(40.0, carryOverMaxHours = 100.0, floatingHours = 8.0)))
    }
}
