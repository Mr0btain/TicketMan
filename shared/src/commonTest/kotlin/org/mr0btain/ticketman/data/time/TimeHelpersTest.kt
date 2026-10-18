package org.mr0btain.ticketman.data.time

import kotlin.test.*
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TimeHelpersTest {
    @Test
    fun aV7IdentifierKnowsWhenItWasMade() {
        val before = Clock.System.now().toEpochMilliseconds()
        val identifier = Uuid.generateV7()
        val after = Clock.System.now().toEpochMilliseconds()

        val created = identifier.createdAt().toEpochMilliseconds()
        assertTrue(created in before..after, "$created not in $before..$after")
        assertEquals(created, identifier.timestampMillis())
    }

    @Test
    fun readsTheTimeFromTheFirstSixBytes() {
        val identifier = Uuid.parse("01a14f48-ae69-705e-bfcf-7d62142a5a8d")

        assertEquals(0x01a14f48ae69L, identifier.timestampMillis())
        assertEquals(0x01a14f48ae69L, identifier.createdAt().toEpochMilliseconds())
    }
}
