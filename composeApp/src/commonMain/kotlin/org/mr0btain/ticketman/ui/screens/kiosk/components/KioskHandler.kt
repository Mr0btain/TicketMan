package org.mr0btain.ticketman.ui.screens.kiosk.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.ui.AppWindow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Kiosk mode for an approved station: the counter fullscreen, signing in by PIN, and locking when left alone
 * @param onSignedIn Loads whoever just PINned in
 * @param onSignedOut Forgets whoever was signed in and anything loaded for them
 * @param isAdmin Whether whoever is signed in now is an admin
 * @param now Current epoch millis, swappable so tests can move time along
 */
class KioskHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val station: StateFlow<Station?>,
    private val onSignedIn: suspend () -> Unit,
    private val onSignedOut: () -> Unit,
    private val isAdmin: () -> Boolean,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val _active = MutableStateFlow(false)
    /** Whether the app is showing the kiosk instead of its normal screens */
    val active = _active.asStateFlow()

    private val _locked = MutableStateFlow(true)
    val locked = _locked.asStateFlow()

    private val _staff = MutableStateFlow<List<StaffMember>>(emptyList())
    val staff = _staff.asStateFlow()

    // An admin left kiosk mode on this device, until the app restarts or they go back
    private var suspended = false

    private var lastActivity = now()
    private var lastKeepAlive = 0L
    private var idleJob: Job? = null

    init {
        // Station settings come from the server, follow them as they change
        scope.launch {
            station.collect { update() }
        }
    }

    /** Anything touched or typed, pushes the idle lock back */
    fun recordActivity() {
        lastActivity = now()
    }

    /** Ends whoever is signed in and shows the PIN screen */
    fun lock() {
        if (!_active.value) return

        _locked.value = true
        onSignedOut()

        scope.launch {
            remoteHandler.endSession()
            refreshStaff()
        }
    }

    fun refreshStaff() {
        scope.launch {
            when (val result = remoteHandler.stationStaff()) {
                is TmResult.Success -> _staff.value = result.data
                is TmResult.Error -> _staff.value = emptyList()
            }
        }
    }

    /**
     * Signs someone in with their PIN
     * @param onResult Gets null when they're in, otherwise what went wrong
     */
    fun unlock(member: StaffMember, pin: String, onResult: (String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.pinLogin(member.identifier, pin)) {
                is TmResult.Success -> {
                    onSignedIn()
                    lastActivity = now()
                    _locked.value = false
                    onResult(null)
                }

                is TmResult.Error -> {
                    onResult(result.error.message)
                    refreshStaff()
                }
            }
        }
    }

    /**
     * Leaves kiosk mode on this device. Needs an admin's PIN, anyone else is signed straight back out
     * @param onResult Gets null when kiosk mode is off, otherwise what went wrong
     */
    fun exit(member: StaffMember, pin: String, onResult: (String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.pinLogin(member.identifier, pin)) {
                is TmResult.Success -> {
                    onSignedIn()

                    if (isAdmin()) {
                        suspended = true
                        _locked.value = false
                        update()
                        remoteHandler.leaveKiosk()
                        onResult(null)
                    } else {
                        lock()
                        onResult("Only an admin can leave kiosk mode")
                    }
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Turns kiosk mode back on after an admin left it */
    fun resume() {
        suspended = false
        update()
        lock()
    }

    // Works out whether kiosk mode should be on from the station and whether an admin left it
    private fun update() {
        val current = station.value
        val shouldBeActive = !suspended && current?.status == StationStatus.APPROVED && current.kioskEnabled

        if (shouldBeActive == _active.value) {
            if (shouldBeActive) startIdleWatch(current.idleTimeoutSeconds)
            return
        }

        _active.value = shouldBeActive
        remoteHandler.credentialFallback = !shouldBeActive
        AppWindow.setFullscreen(shouldBeActive)

        if (shouldBeActive) {
            startIdleWatch(current.idleTimeoutSeconds)

            // Whoever logged the device in doesn't get to stay signed in at the counter
            _locked.value = false
            lock()
        } else {
            idleJob?.cancel()
            _locked.value = false
        }
    }

    private fun startIdleWatch(timeoutSeconds: Int) {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (true) {
                delay(IDLE_CHECK_MILLIS.milliseconds)

                if (_locked.value) continue

                val time = now()
                when {
                    time - lastActivity > timeoutSeconds * 1_000L -> lock()

                    // The server ends a kiosk's PIN session once nothing has reached it for a while, so tell it
                    // someone's still here even when they aren't loading anything
                    time - lastActivity < KEEP_ALIVE_MILLIS && time - lastKeepAlive >= KEEP_ALIVE_MILLIS -> {
                        lastKeepAlive = time
                        launch { remoteHandler.keepKioskAlive() }
                    }
                }
            }
        }
    }

    companion object {
        private const val IDLE_CHECK_MILLIS = 1_000L
        private const val KEEP_ALIVE_MILLIS = 60_000L
    }
}
