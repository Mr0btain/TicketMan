package org.mr0btain.ticketman.components.database.local

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.PlatformFile
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.utils.saveImageToDatabase
import org.mr0btain.ticketman.components.utils.toImageBitmap
import org.mr0btain.ticketman.data.MainData
import org.mr0btain.ticketman.database.LocalDatabaseClient
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
abstract class ClientLocalDatabase<T: MainData>(
    protected val localDatabaseClient: LocalDatabaseClient<T>
) {
    fun getAll() = localDatabaseClient.getAll()

    /** Gets a customer by its identifier */
    fun getById(identifier: Uuid) = localDatabaseClient.getById(identifier)

    fun add(item: T) = localDatabaseClient.add(item)

    fun remove(identifier: Uuid) = localDatabaseClient.remove(identifier)

    fun getNotes(identifier: Uuid): List<NoteData> {
        val databaseResult = localDatabaseClient.getNotes(identifier)
        val result = mutableListOf<NoteData>()

        databaseResult.forEach { (note, storedImages) ->
            val imageData = mutableListOf<ImageBitmap>()

            storedImages.forEach { storedImage ->
                val file = PlatformFile(storedImage.path)

                if (file.file.exists()) {
                    imageData.add(
                        file.file
                            .readBytes()
                            .toImageBitmap()
                    )
                }
            }

            result.add(
                NoteData(
                    note = note,
                    images = imageData
                )
            )
        }

        return result
    }

    suspend fun addNotes(identifier: Uuid, notes: List<NoteData>) {
        // Add to database
        notes.forEach { (note, images) ->
            // Store images first
            val storedImages = images.map { imageBitmap ->
                saveImageToDatabase(identifier, imageBitmap)
            }

            localDatabaseClient.addNote(identifier, note, storedImages)
        }
    }

    fun removeNote(identifier: Uuid) = localDatabaseClient.removeNote(identifier)
}