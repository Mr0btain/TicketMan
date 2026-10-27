package org.mr0btain.ticketman.components.routing

import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.server.ALL_LOCATIONS
import org.mr0btain.ticketman.server.LOCATION_HEADER
import kotlin.uuid.Uuid

/** Where a request works: one location, or every one for an admin looking across them */
sealed interface LocationScope {
    data class One(val location: Location) : LocationScope

    data object All : LocationScope

    /** The location, null for every one */
    val identifier: Uuid?
        get() = (this as? One)?.location?.identifier
}

// Both only read the database, so one of each does for every request
private val locations = LocationDatabase()
private val users = UserDatabase()

/**
 * Where the caller is working, from the [LOCATION_HEADER] the client sends: an active location's id, or
 * [ALL_LOCATIONS] from an admin. Without one, or with one that isn't usable, it's the caller's home location, then
 * the main one
 */
fun RoutingCall.locationScope(): LocationScope {
    val all = locations.all()
    val header = request.headers[LOCATION_HEADER]?.trim()
    val user = getIdentifierFromToken()?.let { users.getById(it) }

    if (header == ALL_LOCATIONS && user?.accessLevel?.meets(AccessLevel.ADMIN) == true) return LocationScope.All

    fun active(identifier: Uuid?): Location? = identifier?.let { id -> all.firstOrNull { it.identifier == id && !it.retired } }

    active(header?.let { Uuid.parseOrNull(it) })?.let { return LocationScope.One(it) }
    active(user?.homeLocation)?.let { return LocationScope.One(it) }
    return LocationScope.One(all.firstOrNull { it.main } ?: locations.main())
}

/**
 * The one location new work goes to: where the caller is working, or their home location (then the main one)
 * while an admin looks at every location
 */
fun RoutingCall.workingLocation(): Location =
    when (val scope = locationScope()) {
        is LocationScope.One -> scope.location
        LocationScope.All -> {
            val all = locations.all()
            val home = getIdentifierFromToken()?.let { users.getById(it) }?.homeLocation
            all.firstOrNull { it.identifier == home && !it.retired } ?: all.firstOrNull { it.main } ?: locations.main()
        }
    }

/** This page, kept to where the caller is working */
fun PagedRequest.at(scope: LocationScope): PagedRequest = copy(location = scope.identifier)
