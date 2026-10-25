package org.mr0btain.ticketman.components.database.tables.report

import org.jetbrains.exposed.v1.core.Table

/**
 * Each user's commission on the labor they bill, apart from the user table so it only goes to admins. [user] isn't
 * a foreign key; rates of deleted users are left out when read
 */
object CommissionRateTable : Table("commission_rates") {
    val user = uuid("user_id")
    val basisPoints = integer("basis_points")

    override val primaryKey = PrimaryKey(user)
}
