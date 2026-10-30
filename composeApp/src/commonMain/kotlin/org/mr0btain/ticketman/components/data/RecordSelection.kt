package org.mr0btain.ticketman.components.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Loads the record picked in a list. Picking another one cancels the load before it, so only the latest pick ever
 * fills the seed, no matter which request comes back last.
 * @param scope Scope the loads run in
 */
class RecordSelection(private val scope: CoroutineScope) {
    private val _pending = MutableStateFlow<Uuid?>(null)

    /** Record being loaded right now, so the list can highlight it before its details arrive. Null when idle */
    val pending: StateFlow<Uuid?> = _pending.asStateFlow()

    private val _revealing = MutableStateFlow<Uuid?>(null)

    /** Record the list should scroll to, see [reveal]. Null when there's nothing to find */
    val revealing: StateFlow<Uuid?> = _revealing.asStateFlow()

    private var job: Job? = null

    /** Asks the list to scroll to [identifier], once it's on screen, like one picked from search or another screen */
    fun reveal(identifier: Uuid) {
        _revealing.value = identifier
    }

    /** The list scrolled to it, or found it isn't in the list */
    fun revealed() {
        _revealing.value = null
    }

    /**
     * Starts loading [identifier], dropping any load still running
     * @param load Fetches the record and returns what puts it in the seed, or null when it couldn't be found.
     * What it returns only runs if nothing was picked since
     */
    fun select(identifier: Uuid, load: suspend () -> (() -> Unit)?) {
        job?.cancel()
        _pending.value = identifier

        // Started after it's stored, so a load that finishes without suspending (a cache hit) still knows it's current
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val apply = load()

                // Network helpers swallow cancellation and hand back empty results, check before touching the seed
                ensureActive()
                apply?.invoke()
            } finally {
                if (job === coroutineContext[Job]) _pending.value = null
            }
        }

        job = next
        next.start()
    }

    /** Stops any load in flight, like when the seed is cleared for a new record */
    fun cancel() {
        job?.cancel()
        job = null
        _pending.value = null
    }
}
