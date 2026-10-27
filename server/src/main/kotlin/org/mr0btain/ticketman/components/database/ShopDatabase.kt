package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.shop.ShopTable
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.location.at
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Stores the shop profile, there is only ever one
 */
@Suppress("PrivatePropertyName")
class ShopDatabase(private val locations: LocationDatabase = LocationDatabase()) {
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

    /**
     * The profile as it applies at [location]: its address, contacts and tax rates, and its name after the shop's
     * when there's more than one location. The main location for null
     */
    @OptIn(ExperimentalUuidApi::class)
    fun at(location: Uuid?): ShopProfile {
        val all = locations.all()
        val here = all.firstOrNull { it.identifier == location } ?: all.firstOrNull { it.main }
        return get().at(here, named = all.count { !it.retired } > 1)
    }

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
