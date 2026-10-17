package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

/**
 * How much mock data to generate when seeding a development server
 */
@Serializable
data class SeedRequest(
    val tickets: Int = 100,
    val customers: Int = 40,
    val inventory: Int = 20,
    val users: Int = 5
)
