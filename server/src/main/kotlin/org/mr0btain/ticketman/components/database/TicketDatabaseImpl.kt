package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.user.UserEntity
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class TicketDatabaseImpl() : Database<Ticket>("TicketLocalDatabase") {
    private val GET_CUSTOMER_TAG = "$tag:GetCustomer"
    private val GET_USER_TAG = "$tag:GetUser"
    private val UPDATE_DEVICE_TAG = "$tag:UpdateDevice"
    private val UPDATE_STATUS_TAG = "$tag:UpdateStatus"
    private val UPDATE_PRIORITY_TAG = "$tag:UpdatePriority"

    override fun all(): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_ALL_TAG) {
            transaction {
                result = TicketEntity.all().map { item -> item.toTicket() }
            }

            result != null
        }

        return result
    }

    override fun getById(identifier: Uuid): Ticket? {
        var result: Ticket? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    result = ticketEntity.toTicket()
                } ?: AppLogger.i(GET_BY_ID_TAG, TicketError.NOT_FOUND)

                result != null
            }
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = TicketEntity.all()
                    .orderBy(TicketTable.id to SortOrder.DESC) // or ASC
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toTicket() }
            }

            result != null
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = TicketEntity.count()
                true
            }
        }

        return count
    }

    override fun addAll(data: List<Ticket>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    TicketEntity.new(item.identifier) {
                        device = item.device
                        status = item.status
                        priority = item.priority
                    }
                }

                true
            }
        }
    }

    override fun remove(identifier: Uuid) {
        appLogTryCatch(REMOVE_TAG) {
            var result = false

            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    ticketEntity.delete()
                    result = true
                } ?: AppLogger.i(REMOVE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    override fun search(query: String, limit: Int): List<SearchResult>? {
        return appLogTryCatch<List<SearchResult>?>(SEARCH_TAG) {
            transaction {
                val pattern = "${query.lowercase()}%"

                Pair(TicketEntity
                    .find { (TicketTable.id.castTo(VarCharColumnType()) like pattern) }
                    .orderBy(TicketTable.id to SortOrder.DESC)
                    .limit(limit)
                    .map { ticketEntity ->
                        SearchResult(
                            identifier = ticketEntity.id.value,
                            text = ticketEntity.id.value.toString()
                        )
                    },
                    true
                    )
            }
        }
    }

    override fun add(data: Ticket) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                TicketEntity.new(data.identifier) {
                    device = data.device
                    status = data.status
                    priority = data.priority
                }

                true
            }
        }
    }

    override fun update(data: Ticket) {
        appLogTryCatch(UPDATE_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(data.identifier) { item ->
                        item.device = data.device
                        item.status = data.status
                        item.priority = data.priority

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    fun updateDevice(identifier: Uuid, device: Device) {
        appLogTryCatch(UPDATE_DEVICE_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(identifier) { item ->
                        item.device = device

                        result = true
                    } ?: AppLogger.i(UPDATE_DEVICE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    fun updateStatus(identifier: Uuid, status: TicketStatus) {
        appLogTryCatch(UPDATE_STATUS_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(identifier) { item ->
                        item.status = status

                        result = true
                    } ?: AppLogger.i(UPDATE_STATUS_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    fun updatePriority(identifier: Uuid, priority: TicketPriority) {
        appLogTryCatch(UPDATE_PRIORITY_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(identifier) { item ->
                        item.priority = priority

                        result = true
                    } ?: AppLogger.i(UPDATE_PRIORITY_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    override fun addNote(identifier: Uuid, note: Note) {
        appLogTryCatch(ADD_NOTE_TAG) {
            var result = false

            transaction {
                // Insert the note into database and get its entity
                insertNote(note)?.let { noteEntity ->
                    // Add note to customer notes
                    TicketEntity.findByIdAndUpdate(identifier) { ticketEntity ->
                        ticketEntity.notes = SizedCollection(ticketEntity.notes + noteEntity)

                        result = true
                    } ?: AppLogger.i(ADD_NOTE_TAG, TicketError.NOT_FOUND)
                }
            }

            result
        }
    }

    override fun getNotes(identifier: Uuid): List<Note>? {
        var result: List<Note>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    result = ticketEntity.notes.map { noteEntity ->
                        noteEntity.toNote()
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, TicketError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    fun getUser(ticketId: Uuid): EntityReference? {
        var result: EntityReference? = null

        appLogTryCatch(GET_USER_TAG) {
            transaction {
                result = UserEntity
                    .wrapRows(
                        UserTable
                            .join(
                                UserTicketTable,
                                JoinType.INNER,
                                UserTable.id,
                                UserTicketTable.user
                            )
                            .selectAll()
                            .where { UserTicketTable.ticket eq ticketId }
                            .limit(1)
                    )
                    .singleOrNull()
                    ?.toIdentity()
            }

            result != null
        }

        return result
    }

    fun getCustomer(ticketId: Uuid): EntityReference? {
        var result: EntityReference? = null

        appLogTryCatch(GET_CUSTOMER_TAG) {
            transaction {
                result = CustomerEntity
                    .wrapRows(
                        CustomerTable
                            .join(
                                CustomerTicketTable,
                                JoinType.INNER,
                                CustomerTable.id,
                                CustomerTicketTable.customer
                            )
                            .selectAll()
                            .where { CustomerTicketTable.ticket eq ticketId }
                            .limit(1)
                    )
                    .singleOrNull()
                    ?.toIdentity()
            }

            result != null
        }

        return result
    }
}