package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.shared.Note

@Serializable
data class Invoice(
    val invoiceNumber: Int = 0,
    val ticketId: Int = 0,
    val note: List<Note> = listOf(),
    val charges: List<Charge> = listOf()
)