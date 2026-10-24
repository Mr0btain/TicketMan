package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Customer(
    override val identifier: Uuid = Uuid.generateV7(),
    val name: String = "",
    val address: Address = Address(),
    val email: String = "",
    val phone: String = "",
    /** Agreed to messages about their repairs by email */
    val notifyEmail: Boolean = false,
    /** Agreed to messages about their repairs by text */
    val notifySms: Boolean = false
): MainItem()