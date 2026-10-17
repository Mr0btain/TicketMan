package org.mr0btain.ticketman.components.database.tables.audit

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object AuditLogTable : UuidTable("audit_log") {
    val actorIdentifier = text("actor_identifier").nullable()
    val actorName = text("actor_name")
    val action = text("action")
    val entityType = text("entity_type")
    val entityIdentifier = text("entity_identifier").nullable()
    val details = text("details")
    val time = long("time")
}
