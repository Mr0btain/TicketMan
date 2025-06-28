package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.customer.TicketList
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.database.tables.UserTable
import org.mr0btain.ticketman.data.user.User
import java.util.*

class UserDatabase(
    private val shouldLog: Boolean
) {
    fun getAll(): List<User>? {
        var result = listOf<User>()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserTable.selectAll().map { user -> user.toModal() }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun getByUsername(username: String): User? {
        var result: User? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserTable.selectAll().where { UserTable.username eq username }.map { user ->
                    user.toModal()
                }.firstOrNull()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun usernameAlreadyExists(username: String): Boolean {
        var result = false

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            val userExists = UserTable.select(UserTable.username).where { UserTable.username eq username }.firstOrNull()

            if (userExists != null) result = true
        }

        return result
    }

    fun getById(identifier: UUID): User? {
        var result: User? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserTable.selectAll().where { UserTable.identifier eq  identifier.toString() }.map { user ->
                    user.toModal()
                }.firstOrNull()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun addAll(items: List<User>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable.batchInsert(items) { user ->
                    this[UserTable.identifier] = user.identifier.toString()
                    this[UserTable.username] = user.username
                    this[UserTable.password] = user.password
                    this[UserTable.firstName] = user.name.firstName
                    this[UserTable.lastName] = user.name.lastName
                    this[UserTable.tickets] = TicketList(user.tickets)
                    this[UserTable.contactInfo] = user.contactInfo
                    this[UserTable.notes] = Notes(user.notes)
                    this[UserTable.access] = user.accessLevel
                    this[UserTable.time] = user.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun getNextId(): UUID? {
        var result = UUID.randomUUID()
        var isUnique = false

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                val identifiers = UserTable.select(UserTable.identifier).map { value -> UUID.fromString(value[UserTable.identifier]) }

                // Just a double check to make sure ID is unique
                while (!isUnique) {
                    if (identifiers.contains(result)) result = UUID.randomUUID() else isUnique = true
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun remove(identifier: UUID): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Delete employee
                UserTable.deleteWhere { this.identifier eq identifier.toString() }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun add(user: User): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable.insert { item ->
                    item[UserTable.identifier] = user.identifier.toString()
                    item[UserTable.username] = user.username
                    item[UserTable.password] = user.password
                    item[UserTable.firstName] = user.name.firstName
                    item[UserTable.lastName] = user.name.lastName
                    item[UserTable.tickets] = TicketList(user.tickets)
                    item[UserTable.contactInfo] = user.contactInfo
                    item[UserTable.notes] = Notes(user.notes)
                    item[UserTable.access] = user.accessLevel
                    item[UserTable.time] = user.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun update(user: User): Boolean {
        val uuid = user.identifier.toString()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserTable.update(where = { UserTable.identifier eq uuid}) { item ->
                    item[UserTable.identifier] = user.identifier.toString()
                    item[UserTable.username] = user.username
                    item[UserTable.password] = user.password
                    item[UserTable.firstName] = user.name.firstName
                    item[UserTable.lastName] = user.name.lastName
                    item[UserTable.tickets] = TicketList(user.tickets)
                    item[UserTable.contactInfo] = user.contactInfo
                    item[UserTable.notes] = Notes(user.notes)
                    item[UserTable.access] = user.accessLevel
                    item[UserTable.time] = user.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }
}

private fun ResultRow.toModal(): User {
    return User(
        identifier = UUID.fromString(this[UserTable.identifier]),
        username = this[UserTable.username],
        password = this[UserTable.password],
        name = PersonName(this[UserTable.firstName], this[UserTable.lastName]),
        notes = this[UserTable.notes].notes,
        contactInfo = this[UserTable.contactInfo],
        tickets = this[UserTable.tickets].list,
        accessLevel = this[UserTable.access],
        time = this[UserTable.time],
    )
}