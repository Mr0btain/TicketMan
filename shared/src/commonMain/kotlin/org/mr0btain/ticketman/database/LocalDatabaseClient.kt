package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.tables.*
import org.mr0btain.ticketman.database.tables.CustomerDataTable.city
import org.mr0btain.ticketman.database.tables.CustomerDataTable.contactInfo
import org.mr0btain.ticketman.database.tables.CustomerDataTable.firstName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.lastName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.notes
import org.mr0btain.ticketman.database.tables.CustomerDataTable.state
import org.mr0btain.ticketman.database.tables.CustomerDataTable.street
import org.mr0btain.ticketman.database.tables.CustomerDataTable.tickets
import org.mr0btain.ticketman.database.tables.CustomerDataTable.time
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Handles interaction between a local database
 */

@Suppress("PropertyName")
interface LocalDatabaseClient {
    val TAG: String
    val GET_ALL_TAG: String
    val GET_BY_ID_TAG: String
    val ADD_ALL_TAG: String
    val REMOVE_TAG: String
    val GET_NEXT_ID_TAG: String
    val ADD_TAG: String
    val UPDATE_TAG: String

    /** Gets all items from database */
    fun getAll(): List<MainDataItem>?

    /** Gets an item by its identifier */
    fun getById(identifier: String): MainDataItem?

    /** Adds all items to the database */
    fun addAll(data: List<MainDataItem>): Boolean

    /** Removes an item from the database */
    fun remove(id: String): Boolean

    /** Gets the next identifier */
    fun getNextId(): String?

    /** Adds an item to the database */
    fun add(data: MainDataItem): Boolean

    /** Updates an item in the database */
    fun update(data: MainDataItem): Boolean
}