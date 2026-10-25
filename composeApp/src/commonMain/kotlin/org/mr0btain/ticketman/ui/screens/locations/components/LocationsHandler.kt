package org.mr0btain.ticketman.ui.screens.locations.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.location.active
import org.mr0btain.ticketman.data.location.byId
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.server.ALL_LOCATIONS
import kotlin.uuid.Uuid

/**
 * Where this device works, among the shop's locations
 * @param identifier The location, null with [all]
 * @param all An admin looking at every location at once
 */
data class WorkingLocation(val identifier: Uuid? = null, val all: Boolean = false) {
    /** What [RemoteHandler.location] sends */
    val header: String?
        get() = if (all) ALL_LOCATIONS else identifier?.toString()
}

/**
 * The shop's locations and the one this device works at, sent with every request so lists, stock and new work
 * keep to it. A station is fixed to its own location; anyone else can pick, and admins can look at all of them.
 * With only one location nothing about this shows
 * @param onChanged Run when the device starts working somewhere else, so lists and the counter load again
 */
class LocationsHandler(
    private val remoteHandler: RemoteHandler,
    private val localStorage: LocalStorage,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val onChanged: () -> Unit
) {
    private val locations get() = remoteHandler.locationRepo

    private val _all = MutableStateFlow<List<Location>>(emptyList())
    /** Every location, retired ones too */
    val all = _all.asStateFlow()

    /** The ones that can be worked at, main first */
    val active: StateFlow<List<Location>> = _all.map { it.active }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** More than one location, so the app shows where it's working */
    val several: StateFlow<Boolean> = active.map { it.size > 1 }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _working = MutableStateFlow(WorkingLocation())
    val working = _working.asStateFlow()

    private val _fixed = MutableStateFlow(false)
    /** This device is a station, which works at its own location only */
    val fixed = _fixed.asStateFlow()

    private var user: User? = null
    private var station: Station? = null

    /**
     * Picks where to work once someone is logged in, straight away so the first requests carry it: the station's
     * location on a station, then what was picked last, then their home location. The list loads after
     */
    fun start(user: User?, station: Station?) {
        this.user = user
        this.station = station
        _fixed.value = station?.locationIdentifier != null
        apply(firstChoice(), notify = false)

        scope.launch { reload() }
    }

    /** This device was registered as a station, or stopped being one */
    fun stationChanged(station: Station?) {
        if (station?.locationIdentifier == this.station?.locationIdentifier) {
            this.station = station
            return
        }
        start(user, station)
        onChanged()
    }

    /** Loads the list again, and moves off a location that was retired meanwhile */
    suspend fun reload() {
        _all.value = locations.all().valueOr { return }

        val working = _working.value
        when {
            // Left to the server until now, which works at the home location
            !working.all && working.identifier == null -> apply(WorkingLocation(home()), notify = false)
            !working.all && _all.value.active.byId(working.identifier) == null -> apply(WorkingLocation(home()), notify = true)
        }
    }

    /** Works at [identifier] from now on, every location for null (admins only) */
    fun choose(identifier: Uuid?) {
        if (_fixed.value) return
        val next = if (identifier == null) {
            if (user?.accessLevel?.meets(AccessLevel.ADMIN) != true) return
            WorkingLocation(all = true)
        } else WorkingLocation(identifier)

        if (next == _working.value) return
        localStorage.workingLocation = next.header.orEmpty()
        apply(next, notify = true)
    }

    /** The location being worked at, null for every one or before the list loads */
    fun current(): Location? = _all.value.byId(_working.value.identifier)

    /** Its name for showing, "All locations" when looking at every one */
    fun label(): String = if (_working.value.all) "All locations" else current()?.name.orEmpty()

    // ------------------------------------------ Admin ------------------------------------------

    /**
     * Adds or changes a location
     * @param onResult Gets null when saved, otherwise what went wrong
     */
    fun save(location: Location, isNew: Boolean, onResult: (String?) -> Unit) {
        scope.launch {
            val result = if (isNew) locations.add(location) else locations.update(location)
            when (result) {
                is TmResult.Success -> {
                    reload()
                    onResult(null)
                    snackBarHost.showSnackbar(if (isNew) "${result.data.name} added" else "${result.data.name} saved")
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun clear() {
        user = null
        station = null
        _all.value = emptyList()
        _fixed.value = false
        apply(WorkingLocation(), notify = false)
    }

    private fun firstChoice(): WorkingLocation {
        station?.locationIdentifier?.let { return WorkingLocation(it) }

        val saved = localStorage.workingLocation
        if (saved == ALL_LOCATIONS && user?.accessLevel?.meets(AccessLevel.ADMIN) == true) return WorkingLocation(all = true)
        Uuid.parseOrNull(saved)?.let { return WorkingLocation(it) }

        return WorkingLocation(home())
    }

    // Their home, or none and the server uses the main one
    private fun home(): Uuid? = user?.homeLocation ?: _all.value.firstOrNull { it.main }?.identifier

    private fun apply(working: WorkingLocation, notify: Boolean) {
        _working.value = working
        remoteHandler.location = working.header
        if (notify) onChanged()
    }
}
