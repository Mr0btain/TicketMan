package org.mr0btain.ticketman.components.database.tables

import org.jetbrains.exposed.v1.core.Table

/** Data fixes that only run once, by name, so they can change data a person could later have typed the same way */
object DataFixTable : Table("data_fix") {
    val name = varchar("name", 64)
    val ranAt = long("ran_at")

    override val primaryKey = PrimaryKey(name)
}
