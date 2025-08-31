package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.image.Image
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.user.UserEntity
import org.mr0btain.ticketman.database.tables.user.toUser
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class UserLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<User>(
    tag = "UserLocalDatabaseClient",
    shouldLog = shouldLog
) {
    /** Gets all items from database */
    override fun getAll(): List<User>? {
        var result = listOf<User>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserEntity.all().map { item -> item.toUser() }

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
    override fun getById(identifier: Uuid): User? {
        var result: User? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserEntity.findById(identifier.toJavaUuid())?.toUser()

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

                data.forEach { item ->
                    // Convert kotlin uuid to java for database
                    val identifier = item.identifier.toJavaUuid()

                    // Insert into database
                    UserEntity.new(identifier) {
                        username = item.username
                        password = item.password
                        name = item.name
                        contactInfo = item.contactInfo
                        access = item.accessLevel
                        time = item.time
                    }
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
    override fun remove(identifier: Uuid): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserEntity.findById(identifier.toJavaUuid())?.delete()

                AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Adds an item to the database */
    override fun add(data: User): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Convert kotlin uuid to java for database
                val identifier = data.identifier.toJavaUuid()

                // Insert into database
                UserEntity.new(identifier) {
                    username = data.username
                    password = data.password
                    name = data.name
                    contactInfo = data.contactInfo
                    access = data.accessLevel
                    time = data.time
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

                UserEntity
                    .findById(data.identifier.toJavaUuid())
                    ?.apply {
                        username = data.username
                        password = data.password
                        name = data.name
                        contactInfo = data.contactInfo
                        access = data.accessLevel
                        time = data.time
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

    override fun addNote(identifier: Uuid, note: Note, images: List<Image>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeNote(identifier: Uuid): Boolean {
        TODO("Not yet implemented")
    }
}