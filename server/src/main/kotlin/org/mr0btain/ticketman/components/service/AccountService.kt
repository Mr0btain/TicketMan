package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InviteDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.dbWrite
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.data.user.PasswordRules
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.AccountSetupRequest
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import org.mr0btain.ticketman.server.request.SetPinRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.server.response.PendingKind
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.server.response.SessionKind
import java.security.SecureRandom
import java.util.Base64
import kotlin.uuid.Uuid

/** Outcome of an account action, [message] says what was wrong */
sealed interface AccountResult<out T> {
    data class Done<T>(val value: T) : AccountResult<T>
    data class Invalid(val message: String) : AccountResult<Nothing>
    data class Conflict(val message: String) : AccountResult<Nothing>
    data object NotFound : AccountResult<Nothing>
}

/**
 * New accounts: requests from the login screen, admin invites, approving and first login setup
 */
class AccountService(
    private val users: UserDatabase,
    private val invites: InviteDatabase,
    private val userService: UserService,
    private val jwtService: JwtService,
    private val auditLog: AuditLogDatabase
) {
    private val random = SecureRandom()

    /**
     * Saves a request as a pending user. They can't do anything until an admin approves it
     */
    fun requestAccount(request: AccountRequest): AccountResult<User> {
        UsernameRules.problem(request.username)?.let { return AccountResult.Invalid(it) }
        PasswordRules.problem(request.password)?.let { return AccountResult.Invalid(it) }

        if (users.getByUsername(request.username) != null) return AccountResult.Conflict("Username is already taken")

        // Stops someone filling the admin's list with junk
        val waiting = users.getPending().count { !invites.exists(it.identifier) }
        if (waiting >= MAX_WAITING_REQUESTS) return AccountResult.Conflict("Too many requests are waiting, ask an admin")

        val user = User(
            username = request.username,
            password = request.password,
            name = request.name.trim(),
            email = request.email.trim(),
            phone = request.phone.trim(),
            accessLevel = AccessLevel.RESTRICTED,
            status = UserStatus.PENDING
        )

        if (!users.add(user)) return AccountResult.Invalid(SAVE_FAILED)
        auditLog.log(null, AuditAction.CREATE, "account request", user.identifier.toString(), "Requested ${user.username}", actorName = user.username)

        return AccountResult.Done(user.withoutPassword())
    }

    /** Pending requests and invites, oldest first */
    fun pending(): List<PendingUserResponse> =
        users.getPending().map { user ->
            val expires = invites.expiresAt(user.identifier)

            PendingUserResponse(
                user = user.withoutPassword(),
                kind = if (expires != null) PendingKind.INVITE else PendingKind.REQUEST,
                inviteExpiresAt = expires
            )
        }

    /**
     * Activates a requested account. They finish setting up on their first login
     */
    fun approve(identifier: Uuid, accessLevel: AccessLevel, approvedBy: Uuid?): AccountResult<Unit> {
        if (accessLevel == AccessLevel.NONE) return AccountResult.Invalid("Pick an access level")

        val user = users.getById(identifier)?.takeIf { it.status == UserStatus.PENDING } ?: return AccountResult.NotFound
        if (invites.exists(identifier)) return AccountResult.Conflict("Invited users activate themselves with their code")

        if (!users.setState(identifier, UserStatus.ACTIVE, accessLevel, needsSetup = true)) return AccountResult.NotFound
        auditLog.log(approvedBy, AuditAction.UPDATE, "user", identifier.toString(), "Approved ${user.username} as $accessLevel")

        return AccountResult.Done(Unit)
    }

    /** Turns down a request or cancels an invite, removing the pending user */
    fun reject(identifier: Uuid, rejectedBy: Uuid?): AccountResult<Unit> {
        val user = users.getById(identifier)?.takeIf { it.status == UserStatus.PENDING } ?: return AccountResult.NotFound

        if (!users.remove(identifier)) return AccountResult.NotFound
        auditLog.log(rejectedBy, AuditAction.DELETE, "user", identifier.toString(), "Rejected ${user.username}")

        return AccountResult.Done(Unit)
    }

    /**
     * Makes a pending user with a setup code. The code is shown to the admin once and goes in place of a password
     */
    fun invite(request: InviteRequest, invitedBy: Uuid?, invitedByName: String): AccountResult<InviteResponse> {
        UsernameRules.problem(request.username)?.let { return AccountResult.Invalid(it) }
        if (request.accessLevel == AccessLevel.NONE) return AccountResult.Invalid("Pick an access level")
        if (users.getByUsername(request.username) != null) return AccountResult.Conflict("Username is already taken")

        val user = User(
            username = request.username,
            // Nobody knows this, they set a real password when they use their code
            password = randomSecret(),
            name = request.name.trim(),
            email = request.email.trim(),
            phone = request.phone.trim(),
            accessLevel = request.accessLevel,
            status = UserStatus.PENDING
        )

        if (!users.add(user)) return AccountResult.Invalid(SAVE_FAILED)

        val (code, expiresAt) = invites.create(user.identifier, invitedByName) ?: return AccountResult.Invalid("Couldn't make an invite")
        auditLog.log(invitedBy, AuditAction.CREATE, "user", user.identifier.toString(), "Invited ${user.username} as ${user.accessLevel}")

        return AccountResult.Done(InviteResponse(users.getById(user.identifier)!!.withoutPassword(), code, expiresAt))
    }

    /** New code for an invite, the old one stops working */
    fun renewInvite(identifier: Uuid, renewedBy: Uuid?, renewedByName: String): AccountResult<InviteResponse> {
        val user = users.getById(identifier)?.takeIf { it.status == UserStatus.PENDING } ?: return AccountResult.NotFound
        if (!invites.exists(identifier)) return AccountResult.Conflict("This user requested an account, approve it instead")

        val (code, expiresAt) = invites.create(identifier, renewedByName) ?: return AccountResult.Invalid("Couldn't make an invite")
        auditLog.log(renewedBy, AuditAction.UPDATE, "user", identifier.toString(), "New setup code for ${user.username}")

        return AccountResult.Done(InviteResponse(user.withoutPassword(), code, expiresAt))
    }

    /**
     * First login for an approved request. They're already logged in, this confirms their details
     */
    fun completeSetup(identifier: Uuid, request: AccountSetupRequest): AccountResult<User> {
        val user = users.getById(identifier) ?: return AccountResult.NotFound
        if (!user.needsSetup) return AccountResult.Conflict("Your account is already set up")
        if (request.name.isBlank()) return AccountResult.Invalid("Your name is required")
        if (request.password.isNotBlank()) PasswordRules.problem(request.password)?.let { return AccountResult.Invalid(it) }
        PinRules.problem(request.pin)?.let { return AccountResult.Invalid(it) }

        if (!saveSetup(user, request.name, request.email, request.phone, request.password, request.pin)) return AccountResult.Invalid(SAVE_FAILED)
        auditLog.log(identifier, AuditAction.UPDATE, "user", identifier.toString(), "Finished account setup")

        return AccountResult.Done(users.getById(identifier)!!.withoutPassword())
    }

    /**
     * Finishes an invite: sets their password and details, activates them, and logs them in
     */
    fun completeInviteSetup(request: InviteSetupRequest): AccountResult<AuthResponse> {
        val identifier = jwtService.verifySetupToken(request.setupToken)
            ?: return AccountResult.Invalid("Setup has expired, log in with your code again")

        val user = users.getById(identifier)?.takeIf { it.status == UserStatus.PENDING && invites.exists(identifier) }
            ?: return AccountResult.NotFound

        if (request.name.isBlank()) return AccountResult.Invalid("Your name is required")
        PasswordRules.problem(request.password)?.let { return AccountResult.Invalid(it) }
        PinRules.problem(request.pin)?.let { return AccountResult.Invalid(it) }

        if (!saveSetup(user, request.name, request.email, request.phone, request.password, request.pin)) return AccountResult.Invalid(SAVE_FAILED)
        invites.remove(identifier)

        val activated = users.getById(identifier)!!
        auditLog.log(identifier, AuditAction.UPDATE, "user", identifier.toString(), "Finished invite setup")

        val session = userService.startSession(activated, request.deviceId, request.deviceName, SessionKind.PASSWORD)
            ?: return AccountResult.Invalid("Couldn't log in")

        return AccountResult.Done(session)
    }

    /** Changes your own PIN, checked against your password so a borrowed session can't do it */
    fun setOwnPin(identifier: Uuid, request: SetPinRequest): AccountResult<Unit> {
        val user = users.getById(identifier) ?: return AccountResult.NotFound

        if (!PasswordHasher.verify(request.currentPassword, user.password)) return AccountResult.Invalid("Password is wrong")
        PinRules.problem(request.pin)?.let { return AccountResult.Invalid(it) }

        users.setPin(identifier, request.pin)
        auditLog.log(identifier, AuditAction.UPDATE, "user", identifier.toString(), "Changed PIN")

        return AccountResult.Done(Unit)
    }

    /**
     * Saves someone's details, PIN and activation from finishing setup, all together or not at all
     * @param password Blank keeps the one they have
     */
    private fun saveSetup(user: User, name: String, email: String, phone: String, password: String, pin: String): Boolean =
        dbWrite(SAVE_SETUP_TAG) {
            val saved = users.update(user.copy(name = name.trim(), email = email.trim(), phone = phone.trim(), password = password)) &&
                users.setPin(user.identifier, pin) &&
                users.setState(user.identifier, UserStatus.ACTIVE, needsSetup = false)

            if (!saved) rollback()
            saved
        }

    private fun randomSecret(): String =
        ByteArray(24).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().encodeToString(it) }

    companion object {
        private const val MAX_WAITING_REQUESTS = 50
        private const val SAVE_SETUP_TAG = "AccountService:SaveSetup"
        private const val SAVE_FAILED = "Your account couldn't be saved, try again"
    }
}
