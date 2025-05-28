package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val author: String,
    val text: String
)

@Serializable
data class Notes(
    val notes: List<Note> = listOf()
)

fun Note.toSingleString(): String {
    return this.author + ":" + this.text
}

fun convertStringBackToNote(input: String): Note {
    val result = input.split(":")

    return Note(result.first(), result.subList(1, result.size).toString())
}