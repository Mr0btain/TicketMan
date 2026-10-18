package org.mr0btain.ticketman.ui.kit.inputs

import kotlin.test.*

class PasswordStrengthTest {
    @Test
    fun strongNeedsEightWithUpperLowerNumberAndSymbol() {
        assertEquals(PasswordStrength.STRONG, passwordStrength("Bench#2026"))
        assertEquals(PasswordStrength.WEAK, passwordStrength("Bn#2026"))
        assertEquals(PasswordStrength.WEAK, passwordStrength("bench#2026"))
        assertEquals(PasswordStrength.WEAK, passwordStrength("BENCH#2026"))
        assertEquals(PasswordStrength.WEAK, passwordStrength("Bench#Shop"))
        assertEquals(PasswordStrength.WEAK, passwordStrength("Bench2026x"))
        assertEquals(PasswordStrength.WEAK, passwordStrength(""))
    }
}
