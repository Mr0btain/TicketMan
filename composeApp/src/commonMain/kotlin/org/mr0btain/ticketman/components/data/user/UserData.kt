package org.mr0btain.ticketman.components.data.user

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.User

data class UserData(
    val user: User? = null,
    val notes: List<NoteData> = emptyList()
)
