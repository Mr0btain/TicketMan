package org.mr0btain.ticketman.components.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.Uuid

class RecordSelectionTest {
    private val a = Uuid.random()
    private val b = Uuid.random()

    @Test
    fun aSlowFirstPickNeverReplacesTheLatest() = runTest {
        val selection = RecordSelection(this)
        val slow = CompletableDeferred<Unit>()
        var shown: Uuid? = null

        selection.select(a) {
            slow.await()
            return@select { shown = a }
        }
        selection.select(b) { { shown = b } }
        advanceUntilIdle()

        slow.complete(Unit)
        advanceUntilIdle()

        assertEquals(b, shown)
        assertNull(selection.pending.value)
    }

    @Test
    fun aCancelledLoadThatSwallowsTheCancelStillDoesNothing() = runTest {
        val selection = RecordSelection(this)
        val slow = CompletableDeferred<Unit>()
        var shown: Uuid? = null

        // Like networkErrorTryCatch, which catches the cancel and hands back nothing
        selection.select(a) {
            try { slow.await() } catch (_: Exception) { }
            return@select { shown = a }
        }
        advanceUntilIdle()
        selection.cancel()
        advanceUntilIdle()

        assertNull(shown)
        assertNull(selection.pending.value)
    }

    @Test
    fun pendingShowsThePickUntilItLoads() = runTest {
        val selection = RecordSelection(this)
        val slow = CompletableDeferred<Unit>()

        selection.select(a) { slow.await(); null }
        advanceUntilIdle()
        assertEquals(a, selection.pending.value)

        slow.complete(Unit)
        advanceUntilIdle()
        assertNull(selection.pending.value)
    }

    @Test
    fun pickingTheSameRecordAgainKeepsItPending() = runTest {
        val selection = RecordSelection(this)
        val slow = CompletableDeferred<Unit>()

        selection.select(a) { slow.await(); null }
        advanceUntilIdle()
        selection.select(a) { slow.await(); null }
        advanceUntilIdle()

        assertEquals(a, selection.pending.value)

        slow.complete(Unit)
        advanceUntilIdle()
        assertNull(selection.pending.value)
    }
}
