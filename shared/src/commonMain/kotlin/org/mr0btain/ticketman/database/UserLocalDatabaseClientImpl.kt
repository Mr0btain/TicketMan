package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.identifier
import org.mr0btain.ticketman.database.tables.UserTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class UserLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<User> {
    override val TAG = "UserLocalDatabaseClient"
    override val GET_ALL_TAG = "$TAG:GetAll"
    override val GET_BY_ID_TAG = "$TAG:GetById"
    override val ADD_ALL_TAG = "$TAG:AddAll"
    override val REMOVE_TAG = "$TAG:Remove"
    override val GET_NEXT_ID_TAG = "$TAG:GetNextId"
    override val ADD_TAG = "$TAG:Add"
    override val UPDATE_TAG = "$TAG:Update"

    override val IdentifierColumn = UserTable.identifier

    /** Gets all items from database */
    override fun getAll(): List<User>? {
        var result = listOf<User>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserTable.selectAll().map { item ->
                    User(
                        identifier = item[UserTable.identifier],
                        username = item[UserTable.username],
                        password = item[UserTable.password],
                        name = PersonName(item[UserTable.firstName], item[UserTable.lastName]),
                        notes = item[UserTable.notes].notes,
                        contactInfo = item[UserTable.contactInfo],
                        tickets = item[UserTable.tickets].list,
                        accessLevel = item[UserTable.access],
                        time = item[UserTable.time],
                    )
                }

                AppLogger.m(GET_ALL_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

    /** Gets an item by its identifier */
    override fun getById(identifier: String): User? {
        var result: User? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserTable.selectAll().where { IdentifierColumn eq identifier }.map { item ->
                    User(
                        identifier = item[UserTable.identifier],
                        username = item[UserTable.username],
                        password = item[UserTable.password],
                        name = PersonName(item[UserTable.firstName], item[UserTable.lastName]),
                        notes = item[UserTable.notes].notes,
                        contactInfo = item[UserTable.contactInfo],
                        tickets = item[UserTable.tickets].list,
                        accessLevel = item[UserTable.access],
                        time = item[UserTable.time],
                    )
                }.firstOrNull()

                AppLogger.m(GET_BY_ID_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(GET_BY_ID_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

    /** Adds all items to the database */
    override fun addAll(data: List<User>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable.batchInsert(data) { item ->
                    this[UserTable.identifier] = item.identifier
                    this[UserTable.username] = item.username
                    this[UserTable.password] = item.password
                    this[UserTable.firstName] = item.name.firstName
                    this[UserTable.lastName] = item.name.lastName
                    this[UserTable.tickets] = TicketList(item.tickets)
                    this[UserTable.contactInfo] = item.contactInfo
                    this[UserTable.notes] = Notes(item.notes)
                    this[UserTable.access] = item.accessLevel
                    this[UserTable.time] = item.time
                }

                AppLogger.m(ADD_ALL_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Removes an item from the database */
    override fun remove(id: String): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)
                UserTable.deleteWhere { IdentifierColumn eq id }
                AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Gets the next identifier */
    override fun getNextId(): String? {
        var result = 0
        var error = false

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable
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
    override fun add(data: User): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable.insert { item ->
                    item[UserTable.identifier] = data.identifier
                    item[UserTable.username] = data.username
                    item[UserTable.password] = data.password
                    item[UserTable.firstName] = data.name.firstName
                    item[UserTable.lastName] = data.name.lastName
                    item[UserTable.tickets] = TicketList(data.tickets)
                    item[UserTable.contactInfo] = data.contactInfo
                    item[UserTable.notes] = Notes(data.notes)
                    item[UserTable.access] = data.accessLevel
                    item[UserTable.time] = data.time
                }

                AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Updates an item in the database */
    override fun update(data: User): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable.update({ IdentifierColumn eq data.identifier }) { item ->
                    item[UserTable.identifier] = data.identifier
                    item[UserTable.username] = data.username
                    item[UserTable.password] = data.password
                    item[UserTable.firstName] = data.name.firstName
                    item[UserTable.lastName] = data.name.lastName
                    item[UserTable.tickets] = TicketList(data.tickets)
                    item[UserTable.contactInfo] = data.contactInfo
                    item[UserTable.notes] = Notes(data.notes)
                    item[UserTable.access] = data.accessLevel
                    item[UserTable.time] = data.time
                }

                AppLogger.m(UPDATE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(UPDATE_TAG, SimpleError.FAIL)
            return false
        }
    }
}