package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val author: String = "",
    val text: String = "",
    val time: Long = Long.MAX_VALUE
)

@Serializable
data class Notes(
    val notes: List<Note> = listOf()
)