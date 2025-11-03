package org.mr0btain.ticketman.components.data.user

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.user.User

data class UserData(
    val user: User = User(),
    val notes: List<NoteData> = emptyList()
)
