package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.CustomerData

@Serializable
data class CustomerSyncResponse(
    val data: List<CustomerData>,
    val requested: List<Int>
)