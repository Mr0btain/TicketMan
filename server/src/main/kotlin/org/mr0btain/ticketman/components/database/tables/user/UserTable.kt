package org.mr0btain.ticketman.components.database.tables.user

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.UserStatus

object UserTable : UuidTable("user") {
    val username = text("username")
    val password = text("password")
    val name = text("name")
    val email = text("email")
    val phone = text("phone")
    val access = json<AccessLevel>("access", Json { ignoreUnknownKeys = true })

    // Added after release. "user" is reserved in H2 so these are added by LocalDatabase.addUserColumns
    val status = text("status").default(UserStatus.ACTIVE.name)
    val needsSetup = bool("needs_setup").default(false)

    // Station PIN, blank when not set
    val pinHash = text("pin_hash").default("")
    val pinFailedAttempts = integer("pin_failed_attempts").default(0)
    val pinLocked = bool("pin_locked").default(false)

    // Profile photo (an image row) and ID card code, null when there's none
    val photo = uuid("photo_id").nullable()
    val badgeCode = text("badge_code").nullable()

    // Added with locations: where they usually work, null for the main location
    val homeLocation = uuid("home_location").nullable()
}