package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.Column
import org.mr0btain.ticketman.data.MainDataItem

/**
 * Handles interaction between a local database
 */

@Suppress("PropertyName")
interface LocalDatabaseClient<T: MainDataItem> {
    val TAG: String
    val GET_ALL_TAG: String
    val GET_BY_ID_TAG: String
    val ADD_ALL_TAG: String
    val REMOVE_TAG: String
    val GET_NEXT_ID_TAG: String
    val ADD_TAG: String
    val UPDATE_TAG: String


    // We have to explicitly set this, or we may run into what i think is a race condition sort of. When hitting the database
    // the columns were ending up being columns from other tables. EX: when updating a ticket after updating a customer it would
    // try to use CustomerTable.number and not TicketTable.number
    val IdentifierColumn: Column<String>

    /** Gets all items from database */
    fun getAll(): List<T>?

    /** Gets an item by its identifier */
    fun getById(identifier: String): T?

    /** Adds all items to the database */
    fun addAll(data: List<T>): Boolean

    /** Removes an item from the database */
    fun remove(id: String): Boolean

    /** Gets the next identifier */
    fun getNextId(): String?

    /** Adds an item to the database */
    fun add(data: T): Boolean

    /** Updates an item in the database */
    fun update(data: T): Boolean
}