package org.mr0btain.ticketman.components.database.local

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.MainData
import org.mr0btain.ticketman.database.LocalDatabase
import kotlin.collections.forEach
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
abstract class ClientLocalDatabase<T: MainData>(
    protected val localDatabase: LocalDatabase<T>
) {
    fun getAll() = localDatabase.getAll()

    /** Gets a customer by its identifier */
    fun getById(identifier: Uuid) = localDatabase.getById(identifier)

    fun add(item: T) = localDatabase.add(item)

    fun remove(identifier: Uuid) = localDatabase.remove(identifier)

    fun getNotes(identifier: Uuid): List<NoteData> =
        localDatabase.getNotes(identifier)
            .map { NoteData(it.key, it.value) }

    fun addNotes(identifier: Uuid, notes: List<NoteData>) {
        // Add to database
        notes.forEach { (note, images) ->
            localDatabase.addNote(identifier, note, images)
        }
    }

    fun removeNote(identifier: Uuid) = localDatabase.removeNote(identifier)
}