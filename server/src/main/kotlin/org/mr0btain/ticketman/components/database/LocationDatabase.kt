package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.location.LocationTable
import org.mr0btain.ticketman.data.location.Location
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The shop's locations. There's always a main one once the server has started, see [ensureMain]
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class LocationDatabase {
    private val TAG = "LocationDatabase"
    private val LIST_TAG = "$TAG:List"
    private val ADD_TAG = "$TAG:Add"
    private val UPDATE_TAG = "$TAG:Update"
    private val MAIN_TAG = "$TAG:Main"

    /** Every location, retired ones included, main first */
    fun all(): List<Location> =
        dbQuery(LIST_TAG) { LocationTable.selectAll().map { it.toLocation() } }.orEmpty()
            .sortedWith(compareByDescending<Location> { it.main }.thenBy { it.name.lowercase() })

    fun get(identifier: Uuid): Location? =
        dbQuery(LIST_TAG) { LocationTable.selectAll().where { LocationTable.id eq identifier }.firstOrNull()?.toLocation() }

    /** The main location, made if there isn't one yet */
    fun main(): Location = all().firstOrNull { it.main } ?: ensureMain()

    /**
     * Makes the main location when the server has none, called on start before records are given one. Returns it
     * @param name What it's called, "Main" when blank
     */
    fun ensureMain(name: String = ""): Location {
        all().firstOrNull { it.main }?.let { return it }

        val location = Location(name = name.ifBlank { "Main" }, main = true)
        dbWrite(MAIN_TAG) {
            insertRow(location)
            true
        }
        return location
    }

    fun add(location: Location): Boolean =
        dbWrite(ADD_TAG) {
            insertRow(location.copy(main = false))
            true
        }

    /** Saves everything but [Location.main], which only [ensureMain] sets */
    fun update(location: Location): Boolean =
        dbWrite(UPDATE_TAG) {
            LocationTable.update(where = { LocationTable.id eq location.identifier }) { row ->
                row[code] = location.code
                row[name] = location.name.trim()
                row[street] = location.street.trim()
                row[city] = location.city.trim()
                row[state] = location.state.trim()
                row[zip] = location.zip.trim()
                row[phone] = location.phone.trim()
                row[email] = location.email.trim()
                row[taxRate] = location.taxRate?.trim()?.ifBlank { null }
                row[taxClassRates] = location.taxClassRates
                row[retired] = location.retired
            } > 0
        }

    private fun insertRow(location: Location) {
        LocationTable.insert { row ->
            row[LocationTable.id] = location.identifier
            row[code] = location.code
            row[name] = location.name.trim()
            row[street] = location.street.trim()
            row[city] = location.city.trim()
            row[state] = location.state.trim()
            row[zip] = location.zip.trim()
            row[phone] = location.phone.trim()
            row[email] = location.email.trim()
            row[taxRate] = location.taxRate?.trim()?.ifBlank { null }
            row[taxClassRates] = location.taxClassRates
            row[main] = location.main
            row[retired] = location.retired
            row[createdAt] = Clock.System.now().toEpochMilliseconds()
        }
    }

    private fun ResultRow.toLocation(): Location =
        Location(
            identifier = this[LocationTable.id].value,
            code = this[LocationTable.code],
            name = this[LocationTable.name],
            street = this[LocationTable.street],
            city = this[LocationTable.city],
            state = this[LocationTable.state],
            zip = this[LocationTable.zip],
            phone = this[LocationTable.phone],
            email = this[LocationTable.email],
            taxRate = this[LocationTable.taxRate],
            taxClassRates = this[LocationTable.taxClassRates],
            main = this[LocationTable.main],
            retired = this[LocationTable.retired]
        )
}

/**
 * The code numbers at [location] start with, blank for the main location or one that isn't there. Inside a
 * transaction, for numbering
 */
@OptIn(ExperimentalUuidApi::class)
internal fun numberPrefix(location: Uuid?): String =
    location?.let { id -> LocationTable.select(LocationTable.code).where { LocationTable.id eq id }.firstOrNull()?.get(LocationTable.code) }.orEmpty()

/**
 * Rows of [this] location column at [location], every row for null. Rows with no location belong to the main one,
 * so one written before it was given a location still shows there. Inside a transaction
 */
@OptIn(ExperimentalUuidApi::class)
internal fun Column<Uuid?>.at(location: Uuid?): Op<Boolean> {
    if (location == null) return Op.TRUE
    val main = LocationTable.select(LocationTable.main).where { LocationTable.id eq location }.firstOrNull()?.get(LocationTable.main) == true
    return if (main) (this eq location) or isNull() else this eq location
}
