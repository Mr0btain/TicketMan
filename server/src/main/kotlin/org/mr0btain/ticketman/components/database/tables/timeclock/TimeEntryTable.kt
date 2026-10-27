package org.mr0btain.ticketman.components.database.tables.timeclock

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/**
 * Time on the clock, one row per stretch. [user] isn't a foreign key, so a deleted user's hours stay for the books
 */
object TimeEntryTable : UuidTable("time_entries") {
    val user = uuid("user_id").index()
    val userName = text("user_name").default("")
    val clockedInAt = long("clocked_in_at").index()
    val clockedOutAt = long("clocked_out_at").nullable()
    val station = text("station").default("")
    val note = text("note").default("")
    val editedBy = text("edited_by").default("")

    // Added with locations: the location it belongs to. Null only until LocalDatabase gives older rows the main one
    val location = uuid("location_id").nullable().index()
}
