package org.mr0btain.ticketman.components.database.tables.user

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Setup codes for invited users, one per user. Only a hash of the code is kept
 */
object InviteTable : Table("user_invites") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val codeHash = text("code_hash")
    val expiresAt = long("expires_at")
    val createdBy = text("created_by")

    override val primaryKey = PrimaryKey(user)
}
