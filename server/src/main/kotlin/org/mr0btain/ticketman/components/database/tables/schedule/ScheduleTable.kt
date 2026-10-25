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

    // Added with time off requests: what it takes from the allowance, and the request it came from
    val timeOffHours = double("time_off_hours").default(0.0)
    val request = uuid("request_id").nullable()
}

/** Each person's time off allowance a year, in hours. No row is none */
object TimeOffAllowanceTable : org.jetbrains.exposed.v1.core.Table("time_off_allowances") {
    val user = uuid("user_id")
    val hours = double("hours").default(0.0)

    override val primaryKey = PrimaryKey(user)
}

/** Time off asked for, answered or not */
object TimeOffRequestTable : UuidTable("time_off_requests") {
    val user = uuid("user_id").index()
    val startsAt = long("starts_at").index()
    val endsAt = long("ends_at")
    val hours = double("hours")
    val note = text("note").default("")
    val status = text("status").index()
    val createdAt = long("created_at")
    val decidedBy = text("decided_by").default("")
    val decidedAt = long("decided_at").nullable()
    val reason = text("reason").default("")
    val entry = uuid("entry_id").nullable()
}
