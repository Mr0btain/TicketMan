package org.mr0btain.ticketman.components.data

import kotlin.test.*

class FormMemoryTest {
    private val memory = FormMemory()

    @Test
    fun theSameKeysGetTheSameValue() {
        val first = memory.keep("form", listOf("a")) { mutableListOf(1) }

        assertSame(first, memory.keep<MutableList<Int>>("form", listOf("a")) { fail("Shouldn't make another") })
    }

    @Test
    fun otherKeysStartItOverAndSlotsAreApart() {
        val first = memory.keep("form", listOf("a")) { "first" }

        assertEquals("other", memory.keep("another", listOf("a")) { "other" })
        assertEquals("second", memory.keep("form", listOf("b")) { "second" })
        assertNotEquals(first, memory.keep("form", listOf("b")) { "unused" })
    }

    @Test
    fun clearForgetsEverything() {
        memory.keep("form", emptyList()) { "typed" }
        memory.clear()

        assertEquals("fresh", memory.keep("form", emptyList()) { "fresh" })
    }
}
