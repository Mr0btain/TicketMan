package org.mr0btain.ticketman.ui.screens.records.components

import org.mr0btain.ticketman.error.valueOr
import androidx.compose.material3.SnackbarHostState
import org.mr0btain.ticketman.components.network.database.RemoteRepository
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.error.Result
import kotlin.uuid.Uuid

/**
 * Sends this device's note changes on [identifier] to the server: what differs between [notes] and [loaded], the
 * notes as they were when the record was opened. Notes another device added since are left alone, and a note
 * another device changed or deleted since isn't overwritten; that's said in a snackbar instead, as is anything that
 * failed. Changes nothing if the server's notes can't be read, since it couldn't tell whose change was whose
 */
suspend fun syncNotes(
    identifier: Uuid,
    notes: List<Note>,
    loaded: List<Note>,
    repo: RemoteRepository<*>,
    snackBarHost: SnackbarHostState
) {
    val server = repo.getNotes(identifier).valueOr { error ->
        snackBarHost.showSnackbar("Notes not saved: ${error.message}")
        return
    }.associateBy { it.identifier }
    val loadedById = loaded.associateBy { it.identifier }
    val keptIds = notes.map { it.identifier }.toSet()
    var conflicts = 0

    val saves = notes.mapNotNull { note ->
        val before = loadedById[note.identifier]
        val now = server[note.identifier]

        when {
            // New here. Already on the server only if an earlier save got part way
            before == null -> when (now) {
                null -> repo.addNote(identifier, note)
                note -> null
                else -> repo.updateNote(note)
            }

            note == before -> null

            // Edited here, and changed or deleted elsewhere since it was opened
            now != before -> {
                conflicts++
                null
            }

            else -> repo.updateNote(note)
        }
    }

    val removals = loaded.filter { it.identifier !in keptIds }.mapNotNull { note ->
        when (server[note.identifier]) {
            null -> null
            note -> repo.removeNote(note.identifier)

            // Deleted here, but edited elsewhere since: keep their edit
            else -> {
                conflicts++
                null
            }
        }
    }

    if (conflicts > 0) {
        snackBarHost.showSnackbar(
            "${if (conflicts == 1) "A note was" else "$conflicts notes were"} changed on another device since this " +
                "was opened, so your changes to ${if (conflicts == 1) "it" else "them"} weren't saved"
        )
    }

    val failed = (saves + removals).count { it is Result.Error }
    if (failed > 0) snackBarHost.showSnackbar("$failed ${if (failed == 1) "note change wasn't" else "note changes weren't"} saved")
}
