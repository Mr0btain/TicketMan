package org.mr0btain.ticketman.ui.screens.dashboard.components

import androidx.compose.material3.SnackbarHostState
import org.mr0btain.ticketman.components.network.database.RemoteRepository
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.error.Result
import kotlin.uuid.Uuid

/**
 * Makes the server's notes on [identifier] match [notes]: adds new ones, updates edited ones and removes ones that
 * were deleted. Shows a snackbar if any of that failed
 */
suspend fun syncNotes(identifier: Uuid, notes: List<Note>, repo: RemoteRepository<*>, snackBarHost: SnackbarHostState) {
    val existing = repo.getNotes(identifier)
    val existingById = existing.associateBy { it.identifier }
    val keptIds = notes.map { it.identifier }.toSet()

    val saves = notes.mapNotNull { note ->
        val saved = existingById[note.identifier]

        when {
            saved == null -> repo.addNote(identifier, note)
            saved != note -> repo.updateNote(note)
            else -> null
        }
    }
    val removals = existing.filter { it.identifier !in keptIds }.map { note -> repo.removeNote(note.identifier) }

    val failed = (saves + removals).count { it is Result.Error }
    if (failed > 0) snackBarHost.showSnackbar("$failed ${if (failed == 1) "note change wasn't" else "note changes weren't"} saved")
}
