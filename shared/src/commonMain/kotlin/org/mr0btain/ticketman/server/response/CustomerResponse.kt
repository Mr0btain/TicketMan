package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.CustomerData

@Serializable
data class CustomerResponse(
    val customerData: List<CustomerData>
)