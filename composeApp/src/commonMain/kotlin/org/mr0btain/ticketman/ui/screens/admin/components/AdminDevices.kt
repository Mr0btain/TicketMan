package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.response.SessionResponse

/**
 * The Stations and Sessions tabs: counter stations and their kiosk mode, and who's logged in where
 * @param reloadOverview Session counts are on the overview
 */
class AdminDevices(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val reloadOverview: suspend () -> Unit
) {
    private val admin get() = remoteHandler.adminRepo
    private val scope get() = support.scope

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations = _stations.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionResponse>>(emptyList())
    val sessions = _sessions.asStateFlow()

    suspend fun reloadStations() {
        _stations.value = admin.getStations()
    }

    suspend fun reloadSessions() {
        _sessions.value = admin.getSessions()
    }

    // ------------------------------------------ Stations ------------------------------------------

    fun approveStation(station: Station) {
        scope.launch {
            support.report(admin.approveStation(station.identifier), "Approved ${station.name}") { reloadStations() }
        }
    }

    /** Turns kiosk mode on or off for a station. The device follows the next time it checks its station */
    fun setKiosk(station: Station, settings: KioskSettings) {
        val message = if (settings.enabled) "Kiosk mode on for ${station.name}" else "Kiosk mode off for ${station.name}"

        scope.launch {
            support.report(admin.setKiosk(station.identifier, settings), message) { reloadStations() }
        }
    }

    /** Removes a station, which also signs out whoever is PINned in there */
    fun removeStation(station: Station) {
        scope.launch {
            support.report(admin.removeStation(station.identifier), "Removed ${station.name}") {
                reloadStations()
                reloadSessions()
            }
        }
    }

    // ------------------------------------------ Sessions ------------------------------------------

    /** Session this device is using, so it can be labelled */
    fun currentSessionId(): String? = remoteHandler.currentSessionId()

    /** Logs one device out. Its tokens stop working straight away */
    fun revokeSession(session: SessionResponse) {
        val device = session.deviceName.ifBlank { "a device" }

        scope.launch {
            support.report(admin.revokeSession(session.sessionId), "Logged out ${session.username} on $device") {
                reloadSessions()
                reloadOverview()
            }
        }
    }

    /** Logs [user] out of every device */
    fun revokeAllSessions(user: User) {
        scope.launch {
            support.report(admin.revokeUserSessions(user.identifier), "Logged ${user.username} out everywhere") {
                reloadSessions()
                reloadOverview()
            }
        }
    }

    fun clear() {
        _stations.value = emptyList()
        _sessions.value = emptyList()
    }
}
