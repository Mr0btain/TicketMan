package org.mr0btain.ticketman.data.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeOffRulesTest {
    // Carrying over since long enough ago that the start year doesn't matter, unless it says
    private fun carried(allowance: TimeOffAllowance, taken: (Int) -> Double = { 0.0 }) =
        TimeOffRules.carriedOver(2026, if (allowance.carryOverFromYear == null) allowance.copy(carryOverFromYear = 2000) else allowance, taken)

    @Test
    fun nothingCarriesFromBeforeTheCapWasSet() {
        // Set this year: no year has ended under it, so a new hire starts with none of the cap
        assertEquals(0.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 40.0, carryOverFromYear = 2026)))
        // Set next year, for a year being looked at from before then
        assertEquals(0.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 40.0, carryOverFromYear = 2027)))
        // Set last year: only what's unused from it carries
        assertEquals(10.0, carried(TimeOffAllowance(80.0, carryOverMaxHours = 40.0, carryOverFromYear = 2025)) { if (it == 2025) 70.0 else 0.0 })
        // No start year saved carries nothing
        assertEquals(0.0, TimeOffRules.carriedOver(2026, TimeOffAllowance(80.0, carryOverMaxHours = 40.0)) { 0.0 })
    }

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
