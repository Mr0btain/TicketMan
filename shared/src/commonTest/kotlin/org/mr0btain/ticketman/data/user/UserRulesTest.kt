package org.mr0btain.ticketman.data.user

import kotlin.test.*

class UserRulesTest {
    @Test
    fun usernames() {
        assertNull(UsernameRules.problem("sam"))
        assertNull(UsernameRules.problem(" Sam.Tech-2_ "))
        assertNotNull(UsernameRules.problem("sa"))
        assertNotNull(UsernameRules.problem("a".repeat(UsernameRules.MAX_LENGTH + 1)))
        assertNull(UsernameRules.problem("a".repeat(UsernameRules.MAX_LENGTH)))
        assertNotNull(UsernameRules.problem("sam tech"))
        assertNotNull(UsernameRules.problem("sam@shop"))
    }

    @Test
    fun pins() {
        assertNull(PinRules.problem("482913"))
        assertNotNull(PinRules.problem("48291"))
        assertNotNull(PinRules.problem("48291a"))
        assertNotNull(PinRules.problem("777777"))
        assertNotNull(PinRules.problem("123456"))
        assertNotNull(PinRules.problem("654321"))
        assertNull(PinRules.problem("123457"))
    }

    @Test
    fun levelsMeetTheOnesBelowThem() {
        assertTrue(AccessLevel.ADMIN.meets(AccessLevel.NORMAL))
        assertTrue(AccessLevel.NORMAL.meets(AccessLevel.NORMAL))
        assertFalse(AccessLevel.RESTRICTED.meets(AccessLevel.NORMAL))
        assertTrue(AccessLevel.RESTRICTED.meets(AccessLevel.RESTRICTED))
        // The old deactivated level never gets in, even though its number is highest
        assertFalse(AccessLevel.NONE.meets(AccessLevel.RESTRICTED))
    }

    @Test
    fun onlyActiveSetUpUsersGetIn() {
        val admin = User(accessLevel = AccessLevel.ADMIN)

        assertTrue(admin.canAccess(AccessLevel.ADMIN))
        assertFalse(admin.copy(status = UserStatus.PENDING).canAccess(AccessLevel.RESTRICTED))
        assertFalse(admin.copy(status = UserStatus.DEACTIVATED).canAccess(AccessLevel.RESTRICTED))
        assertFalse(admin.copy(needsSetup = true).canAccess(AccessLevel.RESTRICTED))
        assertFalse(User(accessLevel = AccessLevel.NORMAL).canAccess(AccessLevel.ADMIN))
    }
}
