package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.shop.ShopTable
import org.mr0btain.ticketman.data.shop.ShopProfile

/**
 * Stores the shop profile, there is only ever one
 */
@Suppress("PrivatePropertyName")
class ShopDatabase {
    private val TAG = "ShopDatabase"
    private val GET_TAG = "$TAG:Get"
    private val UPDATE_TAG = "$TAG:Update"

    private val ROW_ID = 1

    /** Saved profile, or a blank one if it hasn't been set yet */
    fun get(): ShopProfile =
        dbQuery(GET_TAG) {
            ShopTable
                .selectAll()
                .where { ShopTable.id eq ROW_ID }
                .firstOrNull()
                ?.get(ShopTable.profile)
                ?: ShopProfile()
        } ?: ShopProfile()

    fun update(profile: ShopProfile): Boolean =
        dbWrite(UPDATE_TAG) {
            val updated = ShopTable.update(where = { ShopTable.id eq ROW_ID }) { row ->
                row[ShopTable.profile] = profile
            }

            if (updated == 0) {
                ShopTable.insert { row ->
                    row[ShopTable.id] = ROW_ID
                    row[ShopTable.profile] = profile
                }
            }

            true
        }
}
