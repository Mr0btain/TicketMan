package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.image.ImageEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.user.UserEntity
import org.mr0btain.ticketman.components.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.components.service.PasswordHasher
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.BadgeCode
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.UserError
import org.mr0btain.ticketman.logging.AppLogger
import java.security.SecureRandom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class UserDatabase(tag: String = "UserLocalDatabase") : Database<User>(tag) {
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val UPDATE_PASSWORD_TAG = "$tag:UpdatePassword"
    private val COUNT_BY_ACCESS_TAG = "$tag:CountByAccess"
    private val MIGRATE_TAG = "$tag:MigrateDeactivated"
    private val MIGRATE_NAMES_TAG = "$tag:MigrateNames"
    private val SET_STATE_TAG = "$tag:SetState"
    private val GET_PENDING_TAG = "$tag:GetPending"
    private val GET_ADMIN_TAG = "$tag:GetAdmin"
    private val GET_BY_USERNAME_TAG = "$tag:GetByUsername"
    private val SET_PIN_TAG = "$tag:SetPin"
    private val CHECK_PIN_TAG = "$tag:CheckPin"
    private val GET_PIN_USERS_TAG = "$tag:GetPinUsers"
    private val SET_PHOTO_TAG = "$tag:SetPhoto"
    private val BADGE_TAG = "$tag:Badge"

    /** Any active admin, null when there are none */
    fun getAdmin(): User? =
        dbQuery(GET_ADMIN_TAG) {
            UserEntity
                .find { (UserTable.access eq AccessLevel.ADMIN) and (UserTable.status eq UserStatus.ACTIVE.name) }
                .firstOrNull()
                ?.toUser()
        }

    override fun getById(identifier: Uuid): User? =
        dbQuery(GET_BY_ID_TAG) {
            UserEntity.findById(identifier)?.toUser()
                ?: null.also { AppLogger.i(GET_BY_ID_TAG, UserError.NOT_FOUND) }
        }

    fun getByUsername(username: String): User? =
        dbQuery(GET_BY_USERNAME_TAG) {
            UserEntity
                .find { UserTable.username eq username.trim().lowercase() }
                .firstOrNull()
                ?.toUser()
        }

    override fun getPagedItems(request: PagedRequest): List<User>? =
        dbQuery(GET_PAGED_ITEMS_TAG) {
            UserEntity
                .all()
                .orderBy(UserTable.name to SortOrder.ASC, UserTable.id to SortOrder.ASC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toUser() }
        }

    override fun count(): Long =
        dbQuery(COUNT_TAG) { UserEntity.count() } ?: 0L

    override fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) {
            val userEntity = UserEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_TAG, UserError.NOT_FOUND) }

            // Join rows reference the user, so they have to go first. Tickets stay, they just lose the link
            val notes = userEntity.notes.toList()
            UserNoteTable.deleteWhere { UserNoteTable.user eq identifier }
            UserTicketTable.deleteWhere { UserTicketTable.user eq identifier }
            deleteNotes(notes)
            markImagesDeleted(listOfNotNull(userEntity.photo?.let { ImageEntity.findById(it) }))

            userEntity.delete()
            true
        }

    /**
     * Makes [photo] the user's profile photo, or takes it off when null. Only one just uploaded or the one they have
     * is taken (see [linkImages]); the one it replaces is marked deleted
     * @return Whether the user was found
     */
    fun setPhoto(identifier: Uuid, photo: StoredImage?): Boolean =
        dbWrite(SET_PHOTO_TAG) {
            val userEntity = UserEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(SET_PHOTO_TAG, UserError.NOT_FOUND) }

            val current = listOfNotNull(userEntity.photo?.let { ImageEntity.findById(it) })
            userEntity.photo = linkImages(listOfNotNull(photo), current).firstOrNull()?.id?.value
            true
        }

    /** The user's ID card code, null when they have none or aren't there */
    fun badgeCode(identifier: Uuid): String? =
        dbQuery(BADGE_TAG) { UserEntity.findById(identifier)?.badgeCode }

    /**
     * Gives the user a new ID card code, which stops the old card working
     * @return The code, null when the user isn't there
     */
    fun issueBadge(identifier: Uuid): String? {
        val code = newBadgeCode()
        val saved = dbWrite(BADGE_TAG) {
            val userEntity = UserEntity.findById(identifier) ?: return@dbWrite false
            userEntity.badgeCode = code
            true
        }
        return code.takeIf { saved }
    }

    private fun newBadgeCode(): String {
        val random = SecureRandom()
        return (1..BadgeCode.LENGTH).map { BadgeCode.ALPHABET[random.nextInt(BadgeCode.ALPHABET.length)] }.joinToString("")
    }

    override fun search(query: String, limit: Int): List<SearchResult>? =
        dbQuery(SEARCH_TAG) {
            val pattern = "${query.lowercase()}%"

            UserEntity
                .find {
                    (UserTable.name.lowerCase() like pattern) or
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
                }
        }

    override fun add(data: User): Boolean =
        dbWrite(ADD_TAG) {
            insertUser(data)
            true
        }

    // Must be called inside a transaction
    private fun insertUser(data: User) {
        UserEntity.new(data.identifier) {
            username = data.username.trim().lowercase()
            password = PasswordHasher.hashIfNeeded(data.password)
            name = data.name.trim()
            email = data.email.lowercase()
            phone = data.phone
            access = data.accessLevel
            status = data.status.name
            needsSetup = data.needsSetup
        }
    }

    override fun update(data: User): Boolean =
        updateUser(UPDATE_TAG, data.identifier) {
            username = data.username.trim().lowercase()
            // Clients never get the password back, so a blank one means leave it alone
            if (data.password.isNotBlank()) password = PasswordHasher.hashIfNeeded(data.password)
            name = data.name.trim()
            email = data.email.lowercase()
            phone = data.phone
            access = data.accessLevel
            status = data.status.name
        }

    /**
     * Replaces a user's password
     * @return Whether the user was found
     */
    fun updatePassword(identifier: Uuid, newPassword: String): Boolean =
        updateUser(UPDATE_PASSWORD_TAG, identifier) {
            password = PasswordHasher.hash(newPassword)
        }

    /**
     * Changes a user's status, access level and whether they need setup in one go. Used by approving and setup,
     * since a normal update leaves setup alone
     * @return Whether the user was found
     */
    fun setState(identifier: Uuid, status: UserStatus, accessLevel: AccessLevel? = null, needsSetup: Boolean) : Boolean =
        updateUser(SET_STATE_TAG, identifier) {
            this.status = status.name
            accessLevel?.let { access = it }
            this.needsSetup = needsSetup
        }

    /** Sets a user's station PIN and clears any lock */
    fun setPin(identifier: Uuid, pin: String): Boolean =
        updateUser(SET_PIN_TAG, identifier) {
            pinHash = PasswordHasher.hash(pin)
            pinFailedAttempts = 0
            pinLocked = false
        }

    /** Removes a user's PIN, they have to set a new one before using a station */
    fun clearPin(identifier: Uuid): Boolean =
        updateUser(SET_PIN_TAG, identifier) {
            pinHash = ""
            pinFailedAttempts = 0
            pinLocked = false
        }

    /** Lets a locked PIN be used again */
    fun unlockPin(identifier: Uuid): Boolean =
        updateUser(SET_PIN_TAG, identifier) {
            pinFailedAttempts = 0
            pinLocked = false
        }

    /**
     * Applies [change] to a user
     * @return Whether the user was found
     */
    private fun updateUser(tag: String, identifier: Uuid, change: UserEntity.() -> Unit): Boolean =
        dbWrite(tag) {
            val userEntity = UserEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(tag, UserError.NOT_FOUND) }

            userEntity.change()
            true
        }

    /**
     * Checks a PIN, counting wrong ones. The PIN locks after [PinRules.MAX_ATTEMPTS] wrong tries in a row
     */
    fun checkPin(identifier: Uuid, pin: String): PinCheck =
        dbQuery(CHECK_PIN_TAG) {
            val userEntity = UserEntity.findById(identifier)

            when {
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
        } ?: PinCheck.NO_PIN

    fun getPinFailedAttempts(identifier: Uuid): Int =
        dbQuery(CHECK_PIN_TAG) { UserEntity.findById(identifier)?.pinFailedAttempts ?: 0 } ?: 0

    /** Active, set up users with a PIN that isn't locked, for a station's list of who can sign in */
    fun getPinUsers(): List<User> =
        dbQuery(GET_PIN_USERS_TAG) {
            UserEntity
                .find {
                    (UserTable.status eq UserStatus.ACTIVE.name) and
                        (UserTable.needsSetup eq false) and
                        (UserTable.pinHash neq "") and
                        (UserTable.pinLocked eq false)
                }
                .orderBy(UserTable.name to SortOrder.ASC)
                .map { it.toUser() }
        } ?: emptyList()

    /** Users waiting on approval or an invite */
    fun getPending(): List<User> =
        dbQuery(GET_PENDING_TAG) {
            UserEntity
                .find { UserTable.status eq UserStatus.PENDING.name }
                .orderBy(UserTable.id to SortOrder.ASC)
                .map { it.toUser() }
        } ?: emptyList()

    /** Active admins, at least one has to be kept */
    fun countActiveAdmins(): Long =
        dbQuery(COUNT_BY_ACCESS_TAG) {
            UserTable.selectAll()
                .where { (UserTable.access eq AccessLevel.ADMIN) and (UserTable.status eq UserStatus.ACTIVE.name) }
                .count()
        } ?: 0L

    /**
     * Users from before statuses existed were deactivated by setting their access to NONE.
     * Moves them to the DEACTIVATED status with RESTRICTED access
     */
    fun migrateDeactivatedUsers() {
        dbWrite(MIGRATE_TAG) {
            UserTable.update(where = { UserTable.access eq AccessLevel.NONE }) { row ->
                row[UserTable.status] = UserStatus.DEACTIVATED.name
                row[UserTable.access] = AccessLevel.RESTRICTED
            }

            true
        }
    }

    /** Rewrites names older versions stored as `first:last` or lowercased, see [legacyName]. Run once */
    fun migrateLegacyNames(): Boolean =
        dbWrite(MIGRATE_NAMES_TAG) {
            UserEntity.all().filter { isLegacyName(it.name) }.forEach { it.name = legacyName(it.name) }
            true
        }

    /** Number of users with this access level */
    fun countByAccessLevel(accessLevel: AccessLevel): Long =
        dbQuery(COUNT_BY_ACCESS_TAG) {
            UserTable.selectAll().where { UserTable.access eq accessLevel }.count()
        } ?: 0L

    override val noteLinkColumn: Column<EntityID<Uuid>> = UserNoteTable.note

    override fun addNote(identifier: Uuid, note: Note): Boolean =
        attachNote(note, UserError.NOT_FOUND, { UserEntity.findById(identifier) }) { userEntity, noteEntity ->
            userEntity.notes = SizedCollection(userEntity.notes + noteEntity)
        }

    override fun getNotes(identifier: Uuid): List<Note>? =
        dbQuery(GET_NOTES_TAG) {
            UserEntity.findById(identifier)?.notes?.map { noteEntity -> noteEntity.toNote() }
                ?: null.also { AppLogger.i(GET_NOTES_TAG, UserError.NOT_FOUND) }
        }

    /**
     * Adds a ticket to a user, assuming that ticket and user already exist in the database
     * @param userId Identifier of the user
     * @param ticketId Identifier of the ticket
     * @return Whether both were found
     */
    fun addTicket(userId: Uuid, ticketId: Uuid): Boolean =
        dbWrite(ADD_TICKET_TAG) {
            val ticketEntity = TicketEntity.findById(ticketId)
                ?: return@dbWrite false.also { AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND) }
            val userEntity = UserEntity.findById(userId)
                ?: return@dbWrite false.also { AppLogger.i(ADD_TICKET_TAG, UserError.NOT_FOUND) }

            userEntity.tickets = SizedCollection(userEntity.tickets + ticketEntity)
            true
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
