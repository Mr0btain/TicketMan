package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.user.UserEntity
import org.mr0btain.ticketman.components.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.components.service.PasswordHasher
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.error.NoteError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.UserError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class UserDatabaseImpl(tag: String = "UserLocalDatabase") : Database<User>(tag) {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val UPDATE_PASSWORD_TAG = "$tag:UpdatePassword"
    private val COUNT_BY_ACCESS_TAG = "$tag:CountByAccess"
    private val MIGRATE_TAG = "$tag:MigrateDeactivated"
    private val SET_STATE_TAG = "$tag:SetState"
    private val GET_PENDING_TAG = "$tag:GetPending"
    private val SET_PIN_TAG = "$tag:SetPin"
    private val CHECK_PIN_TAG = "$tag:CheckPin"
    private val GET_PIN_USERS_TAG = "$tag:GetPinUsers"

    /** Any active admin, null when there are none */
    fun getAdmin(): User? {
        var result: User? = null

        appLogTryCatch("GETADMIN") {
            transaction {
                result = UserEntity
                    .find { (UserTable.access eq AccessLevel.ADMIN) and (UserTable.status eq UserStatus.ACTIVE.name) }
                    .firstOrNull()
                    ?.toUser()

                result != null
            }
        }

        return result
    }

    override fun getById(identifier: Uuid): User? {
        var result: User? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    result = userEntity.toUser()
                } ?: AppLogger.i(GET_BY_ID_TAG, UserError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    fun getByUsername(username: String): User? {
        var result: User? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                result = UserEntity
                    .find { UserTable.username eq username.trim().lowercase() }
                    .firstOrNull()
                    ?.toUser()
            }

            result != null
        }

        return result
    }

    fun getAccessLevel(identifier: Uuid): AccessLevel {
        getById(identifier)?.accessLevel?.let { return it }

        return AccessLevel.NONE
    }

    override fun getPagedItems(request: PagedRequest): List<User>? {
        var result: List<User>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = UserEntity
                    .all()
                    .orderBy(UserTable.name to SortOrder.ASC, UserTable.id to SortOrder.ASC)
                    .offset(request.offset)
                    .limit(request.itemsPerPage)
                    .map { it.toUser() }
            }

            result != null
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = UserEntity.count()
                true
            }
        }
        return count
    }

    override fun addAll(data: List<User>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    UserEntity.new(item.identifier) {
                        username = item.username.lowercase()
                        password = PasswordHasher.hashIfNeeded(item.password)
                        name = item.name.lowercase()
                        email = item.email.lowercase()
                        phone = item.phone
                        access = item.accessLevel
                        status = item.status.name
                        needsSetup = item.needsSetup
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
                UserEntity.findById(identifier)?.let { userEntity ->
                    // Join rows reference the user, so they have to go first. Tickets stay, they just lose the link
                    val notes = userEntity.notes.toList()
                    UserNoteTable.deleteWhere { UserNoteTable.user eq identifier }
                    UserTicketTable.deleteWhere { UserTicketTable.user eq identifier }
                    notes.forEach { noteEntity -> deleteNoteEntity(noteEntity) }

                    userEntity.delete()
                    result = true
                } ?: AppLogger.i(REMOVE_TAG, UserError.NOT_FOUND)

                result
            }
        }
    }

    override fun search(query: String, limit: Int): List<SearchResult>? {
        return appLogTryCatch<List<SearchResult>?>(SEARCH_TAG) {
            transaction {
                val pattern = "${query.lowercase()}%"

                Pair(
                    UserEntity
                        .find {
                            (UserTable.name like pattern) or
                                    (UserTable.id.castTo(VarCharColumnType()) like pattern) or
                                    (UserTable.username like pattern)
                        }
                        .orderBy(UserTable.name to SortOrder.ASC, UserTable.id to SortOrder.ASC)
                        .limit(limit)
                        .map { userEntity ->
                            SearchResult(
                                identifier = userEntity.id.value,
                                text = when {
                                    userEntity.name.startsWith(query) -> userEntity.name
                                    userEntity.username.startsWith(query) -> userEntity.username
                                    userEntity.id.value.toString().startsWith(query) -> userEntity.id.value.toString()
                                    else -> userEntity.name
                                }
                            )
                        },
                    true
                )
            }
        }
    }

    override fun add(data: User) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                UserEntity.new(data.identifier) {
                    username = data.username.lowercase()
                    password = PasswordHasher.hashIfNeeded(data.password)
                    name = data.name.lowercase()
                    email = data.email.lowercase()
                    phone = data.phone
                    access = data.accessLevel
                    status = data.status.name
                    needsSetup = data.needsSetup
                }

                true
            }
        }
    }

    override fun update(data: User) {
        appLogTryCatch(UPDATE_TAG) {
            var result = false

            transaction {
                UserEntity
                    .findById(data.identifier)
                    ?.apply {
                        username = data.username.lowercase()
                        // Clients never get the password back, so a blank one means leave it alone
                        if (data.password.isNotBlank()) password = PasswordHasher.hashIfNeeded(data.password)
                        name = data.name.lowercase()
                        email = data.email.lowercase()
                        phone = data.phone
                        access = data.accessLevel
                        status = data.status.name

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, UserError.NOT_FOUND)
            }

            result
        }
    }

    /**
     * Replaces a user's password
     * @return Whether the user was found
     */
    fun updatePassword(identifier: Uuid, newPassword: String): Boolean {
        var result = false

        appLogTryCatch(UPDATE_PASSWORD_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    userEntity.password = PasswordHasher.hash(newPassword)
                    result = true
                } ?: AppLogger.i(UPDATE_PASSWORD_TAG, UserError.NOT_FOUND)
            }

            result
        }

        return result
    }

    /**
     * Changes a user's status, access level and whether they need setup in one go. Used by approving and setup,
     * since a normal update leaves setup alone
     * @return Whether the user was found
     */
    fun setState(identifier: Uuid, status: UserStatus, accessLevel: AccessLevel? = null, needsSetup: Boolean) : Boolean {
        var result = false

        appLogTryCatch(SET_STATE_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    userEntity.status = status.name
                    accessLevel?.let { userEntity.access = it }
                    userEntity.needsSetup = needsSetup
                    result = true
                } ?: AppLogger.i(SET_STATE_TAG, UserError.NOT_FOUND)
            }

            result
        }

        return result
    }

    /** Sets a user's station PIN and clears any lock */
    fun setPin(identifier: Uuid, pin: String): Boolean {
        var result = false

        appLogTryCatch(SET_PIN_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    userEntity.pinHash = PasswordHasher.hash(pin)
                    userEntity.pinFailedAttempts = 0
                    userEntity.pinLocked = false
                    result = true
                } ?: AppLogger.i(SET_PIN_TAG, UserError.NOT_FOUND)
            }

            result
        }

        return result
    }

    /** Removes a user's PIN, they have to set a new one before using a station */
    fun clearPin(identifier: Uuid): Boolean {
        var result = false

        appLogTryCatch(SET_PIN_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    userEntity.pinHash = ""
                    userEntity.pinFailedAttempts = 0
                    userEntity.pinLocked = false
                    result = true
                } ?: AppLogger.i(SET_PIN_TAG, UserError.NOT_FOUND)
            }

            result
        }

        return result
    }

    /** Lets a locked PIN be used again */
    fun unlockPin(identifier: Uuid): Boolean {
        var result = false

        appLogTryCatch(SET_PIN_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    userEntity.pinFailedAttempts = 0
                    userEntity.pinLocked = false
                    result = true
                } ?: AppLogger.i(SET_PIN_TAG, UserError.NOT_FOUND)
            }

            result
        }

        return result
    }

    /**
     * Checks a PIN, counting wrong ones. The PIN locks after [PinRules.MAX_ATTEMPTS] wrong tries in a row
     */
    fun checkPin(identifier: Uuid, pin: String): PinCheck {
        var result = PinCheck.NO_PIN

        appLogTryCatch(CHECK_PIN_TAG) {
            transaction {
                val userEntity = UserEntity.findById(identifier)

                result = when {
                    userEntity == null || userEntity.pinHash.isBlank() -> PinCheck.NO_PIN
                    userEntity.pinLocked -> PinCheck.LOCKED

                    PasswordHasher.verify(pin, userEntity.pinHash) -> {
                        userEntity.pinFailedAttempts = 0
                        PinCheck.OK
                    }

                    else -> {
                        userEntity.pinFailedAttempts += 1
                        if (userEntity.pinFailedAttempts >= PinRules.MAX_ATTEMPTS) {
                            userEntity.pinLocked = true
                            PinCheck.LOCKED
                        } else PinCheck.WRONG
                    }
                }
            }

            result == PinCheck.OK
        }

        return result
    }

    fun getPinFailedAttempts(identifier: Uuid): Int {
        var result = 0

        appLogTryCatch(CHECK_PIN_TAG) {
            transaction {
                result = UserEntity.findById(identifier)?.pinFailedAttempts ?: 0
            }

            true
        }

        return result
    }

    /** Active, set up users with a PIN that isn't locked, for a station's list of who can sign in */
    fun getPinUsers(): List<User> {
        var result: List<User> = emptyList()

        appLogTryCatch(GET_PIN_USERS_TAG) {
            transaction {
                result = UserEntity
                    .find {
                        (UserTable.status eq UserStatus.ACTIVE.name) and
                            (UserTable.needsSetup eq false) and
                            (UserTable.pinHash neq "") and
                            (UserTable.pinLocked eq false)
                    }
                    .orderBy(UserTable.name to SortOrder.ASC)
                    .map { it.toUser() }
            }

            true
        }

        return result
    }

    /** Users waiting on approval or an invite */
    fun getPending(): List<User> {
        var result: List<User> = emptyList()

        appLogTryCatch(GET_PENDING_TAG) {
            transaction {
                result = UserEntity
                    .find { UserTable.status eq UserStatus.PENDING.name }
                    .orderBy(UserTable.id to SortOrder.ASC)
                    .map { it.toUser() }
            }

            true
        }

        return result
    }

    /** Active admins, at least one has to be kept */
    fun countActiveAdmins(): Long {
        var count = 0L

        appLogTryCatch(COUNT_BY_ACCESS_TAG) {
            transaction {
                count = UserTable.selectAll()
                    .where { (UserTable.access eq AccessLevel.ADMIN) and (UserTable.status eq UserStatus.ACTIVE.name) }
                    .count()
            }

            true
        }

        return count
    }

    /**
     * Users from before statuses existed were deactivated by setting their access to NONE.
     * Moves them to the DEACTIVATED status with RESTRICTED access
     */
    fun migrateDeactivatedUsers() {
        appLogTryCatch(MIGRATE_TAG) {
            transaction {
                UserTable.update(where = { UserTable.access eq AccessLevel.NONE }) { row ->
                    row[UserTable.status] = UserStatus.DEACTIVATED.name
                    row[UserTable.access] = AccessLevel.RESTRICTED
                }
            }

            true
        }
    }

    /** Number of users with this access level */
    fun countByAccessLevel(accessLevel: AccessLevel): Long {
        var count = 0L

        appLogTryCatch(COUNT_BY_ACCESS_TAG) {
            transaction {
                count = UserTable.selectAll().where { UserTable.access eq accessLevel }.count()
            }

            true
        }

        return count
    }

    private fun deleteNoteEntity(noteEntity: NoteEntity) {
        noteEntity.images.forEach { imageEntity -> imageEntity.delete() }
        noteEntity.delete()
    }

    override fun addNote(identifier: Uuid, note: Note) {
        appLogTryCatch(ADD_NOTE_TAG) {
            var result = false

            transaction {
                // Insert the note into database and get its entity
                insertNote(note)?.let { noteEntity ->
                    // Add note to customer notes
                    UserEntity.findByIdAndUpdate(identifier) { userEntity ->
                        userEntity.notes = SizedCollection(userEntity.notes + noteEntity)

                        result = true
                    } ?: AppLogger.i(ADD_NOTE_TAG, NoteError.NOT_FOUND)
                }
            }

            result
        }
    }

    override fun getNotes(identifier: Uuid): List<Note>? {
        var result: List<Note>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    result = userEntity.notes.map { noteEntity ->
                        noteEntity.toNote()
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, UserError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    /**
     * Adds a ticket to a user, assuming that ticket and user already exist in the database
     * @param userId Identifier of the user
     * @param ticketId Identifier of the ticket
     */
    fun addTicket(userId: Uuid, ticketId: Uuid) {
        appLogTryCatch(ADD_TICKET_TAG) {
            var result = false
            transaction {
                // Get ticket entity
                TicketEntity.findById(ticketId)?.let { ticketEntity ->
                    // Get user entity
                    UserEntity.findById(userId)?.let { userEntity ->
                        // Insert ticket into user links
                        userEntity.tickets = SizedCollection(userEntity.tickets + ticketEntity)

                        result = true
                    } ?: AppLogger.i(ADD_TICKET_TAG, UserError.NOT_FOUND)
                } ?: AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    /**
     *  Gets all tickets for a user
     *  @param identifier Identifier of the user
     */
    fun getTickets(identifier: Uuid): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_TICKETS_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    result = userEntity.tickets
                        .map { ticketEntity -> ticketEntity.toTicket() }
                } ?: AppLogger.i(GET_TICKETS_TAG, UserError.NOT_FOUND)
            }

            result != null
        }

        return result
    }
}

/** Result of checking a PIN */
enum class PinCheck {
    OK,
    WRONG,

    /** Locked from too many wrong tries, or was already */
    LOCKED,

    /** No such user or they haven't set a PIN */
    NO_PIN
}
