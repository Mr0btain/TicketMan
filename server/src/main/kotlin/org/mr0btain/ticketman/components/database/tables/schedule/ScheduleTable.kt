package org.mr0btain.ticketman.components.database.tables.schedule

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/**
 * The staff schedule: shifts, time off and the like, one row per entry. A weekly repeat is its rows sharing a
 * [series]. [user] isn't a foreign key, so a deleted user's past schedule stays readable
 */
object ScheduleTable : UuidTable("schedule_entries") {
    val user = uuid("user_id").index()
    val kind = text("kind")
    val startsAt = long("starts_at").index()
    val endsAt = long("ends_at")
    val allDay = bool("all_day").default(false)
    val note = text("note").default("")
    val series = uuid("series_id").nullable().index()
    val location = uuid("location_id").nullable().index()
    val createdBy = text("created_by").default("")
    val createdAt = long("created_at").default(0)
}
