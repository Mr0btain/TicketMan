package org.mr0btain.ticketman.components.utils

import org.mr0btain.ticketman.data.user.User
import kotlin.test.Test
import kotlin.test.assertEquals

class NameHelpersTest {
    @Test
    fun splitAndJoinRoundTrip() {
        assertEquals(listOf("jane", "doe"), splitName("jane:doe"))
        assertEquals("jane:doe", joinName(listOf("jane", "doe")))
    }

    @Test
    fun joinedNamesMatchUserCleanName() {
        // The server side model splits on the same divider
        assertEquals("Jane Doe", User(name = joinName(listOf("jane", "doe"))).cleanName())
    }
}
