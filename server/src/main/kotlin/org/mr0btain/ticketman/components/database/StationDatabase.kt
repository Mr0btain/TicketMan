package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.station.StationTable
import org.mr0btain.ticketman.components.service.PasswordHasher
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.error.appLogTryCatch
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Registered stations and their secrets
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class StationDatabase {
    private val TAG = "StationDatabase"
    private val REGISTER_TAG = "$TAG:Register"
    private val GET_TAG = "$TAG:Get"
    private val APPROVE_TAG = "$TAG:Approve"
    private val REMOVE_TAG = "$TAG:Remove"

    private val random = SecureRandom()

    /**
     * Registers a device as a pending station. A device registering again starts over with a new secret
     * @return The station and its credentials, the secret is only ever handed out here
     */
    fun register(deviceId: String, name: String, requestedBy: String): Pair<Station, StationCredentials>? {
        val secret = ByteArray(32).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val identifier = Uuid.generateV7()

        return appLogTryCatch<Pair<Station, StationCredentials>>(REGISTER_TAG) {
            transaction {
                StationTable.deleteWhere { StationTable.deviceId eq deviceId }
                StationTable.insert { row ->
                    row[StationTable.id] = identifier
                    row[StationTable.deviceId] = deviceId
                    row[StationTable.name] = name
                    row[StationTable.status] = StationStatus.PENDING.name
                    row[StationTable.secretHash] = PasswordHasher.hash(secret)
                    row[StationTable.requestedBy] = requestedBy
                    row[StationTable.requestedAt] = Clock.System.now().toEpochMilliseconds()
                }

                val station = StationTable.selectAll().where { StationTable.id eq identifier }.single().toStation()
                Pair(Pair(station, StationCredentials(identifier, secret)), true)
            }
        }
    }

    fun getById(identifier: Uuid): Station? =
        appLogTryCatch<Station?>(GET_TAG) {
            transaction {
                val station = StationTable.selectAll().where { StationTable.id eq identifier }.firstOrNull()?.toStation()
                Pair(station, station != null)
            }
        }

    /** Every station, newest first */
    fun getAll(): List<Station> =
        appLogTryCatch<List<Station>>(GET_TAG) {
            transaction {
                Pair(
                    StationTable.selectAll().orderBy(StationTable.requestedAt to SortOrder.DESC).map { it.toStation() },
                    true
                )
            }
        }.orEmpty()

    /**
     * The station these credentials belong to if the secret is right, whatever its status
     */
    fun verify(credentials: StationCredentials): Station? =
        appLogTryCatch<Station?>(GET_TAG) {
            transaction {
                val row = StationTable.selectAll().where { StationTable.id eq credentials.stationId }.firstOrNull()
                val station = row?.takeIf { PasswordHasher.verify(credentials.secret, it[StationTable.secretHash]) }?.toStation()

                Pair(station, station != null)
            }
        }

    fun approve(identifier: Uuid, approvedBy: String): Boolean =
        appLogTryCatch<Boolean>(APPROVE_TAG) {
            transaction {
                val updated = StationTable.update(where = { StationTable.id eq identifier }) { row ->
                    row[status] = StationStatus.APPROVED.name
                    row[StationTable.approvedBy] = approvedBy
                    row[approvedAt] = Clock.System.now().toEpochMilliseconds()
                } > 0

                Pair(updated, updated)
            }
        } ?: false

    fun remove(identifier: Uuid): Boolean =
        appLogTryCatch<Boolean>(REMOVE_TAG) {
            transaction {
                val removed = StationTable.deleteWhere { StationTable.id eq identifier } > 0
                Pair(removed, removed)
            }
        } ?: false

    private fun ResultRow.toStation(): Station =
        Station(
            identifier = this[StationTable.id].value,
            name = this[StationTable.name],
            deviceId = this[StationTable.deviceId],
            status = StationStatus.valueOf(this[StationTable.status]),
            requestedBy = this[StationTable.requestedBy],
            requestedAt = this[StationTable.requestedAt],
            approvedBy = this[StationTable.approvedBy],
            approvedAt = this[StationTable.approvedAt]
        )
}
