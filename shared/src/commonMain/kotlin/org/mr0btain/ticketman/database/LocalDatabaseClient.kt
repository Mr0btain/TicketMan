package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.database.tables.CustomerDataTable.identifier
import org.mr0btain.ticketman.database.tables.DataIntTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

@Suppress("PropertyName")
abstract class LocalDatabaseClient<T: MainDataItem>(
    tag: String,
    private val table: DataIntTable,
    private val shouldLog: Boolean
) {
    protected val GET_ALL_TAG = "$tag:GetAll"
    protected val GET_BY_ID_TAG = "$tag:GetById"
    protected val ADD_ALL_TAG = "$tag:AddAll"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val GET_NEXT_ID_TAG = "$tag:GetNextId"
    protected val ADD_TAG = "$tag:Add"
    protected val UPDATE_TAG = "$tag:Update"

    // We have to explicitly set this, or we may run into what i think is a race condition sort of. When hitting the database
    // the columns were ending up being columns from other tables. EX: when updating a ticket after updating a customer it would
    // try to use CustomerTable.number and not TicketTable.number
    protected abstract val IdentifierColumn: Column<String>

    /** Gets all items from database */
    abstract fun getAll(): List<T>?

    /** Gets an item by its identifier */
    abstract fun getById(identifier: String): T?

    /** Adds all items to the database */
    abstract fun addAll(data: List<T>): Boolean

    /** Removes an item from the database */
    abstract fun remove(id: String): Boolean

    /** Gets the next identifier */
    fun getNextId(): String? {
        var result = 0
        var error = false

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                table
                    .select(IdentifierColumn)
                    .map { it[identifier] }
                    .let { list ->
                        list.maxOfOrNull { it }?.let {
                            result = it.toInt()
                            AppLogger.m(GET_NEXT_ID_TAG, SimpleError.SUCCESS)
                        } ?: {
                            error = true
                            AppLogger.i(GET_NEXT_ID_TAG, SimpleError.FAIL)
                        }
                    }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.m(GET_NEXT_ID_TAG, SimpleError.FAIL)
            return null
        }

        return if (error) null else (result + 1).toString()
    }

    /** Adds an item to the database */
    abstract fun add(data: T): Boolean

    /** Updates an item in the database */
    abstract fun update(data: T): Boolean
}