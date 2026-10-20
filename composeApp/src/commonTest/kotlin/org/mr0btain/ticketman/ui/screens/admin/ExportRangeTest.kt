package org.mr0btain.ticketman.ui.screens.admin

import kotlinx.datetime.LocalDate
import org.mr0btain.ticketman.ui.screens.admin.invoices.ExportRange
import kotlin.test.*

class ExportRangeTest {
    private val today = LocalDate(2026, 3, 15)

    @Test
    fun rangesCoverWholeMonthsAndYears() {
        assertEquals(LocalDate(2026, 3, 1) to today, ExportRange.THIS_MONTH.days(today))
        assertEquals(LocalDate(2026, 2, 1) to LocalDate(2026, 2, 28), ExportRange.LAST_MONTH.days(today))
        assertEquals(LocalDate(2026, 1, 1) to today, ExportRange.THIS_YEAR.days(today))
        assertEquals(LocalDate(2025, 1, 1) to LocalDate(2025, 12, 31), ExportRange.LAST_YEAR.days(today))
        assertNull(ExportRange.CUSTOM.days(today))
    }

    @Test
    fun lastMonthInJanuaryIsLastDecember() {
        assertEquals(LocalDate(2025, 12, 1) to LocalDate(2025, 12, 31), ExportRange.LAST_MONTH.days(LocalDate(2026, 1, 9)))
    }
}
