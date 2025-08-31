package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.image.Image
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

// TODO: Add a way to remove images

@Suppress("PropertyName")
@OptIn(ExperimentalUuidApi::class)
abstract class LocalDatabaseClient<T: MainDataItem>(
    tag: String,
    private val shouldLog: Boolean
) {
    protected val GET_ALL_TAG = "$tag:GetAll"
    protected val GET_BY_ID_TAG = "$tag:GetById"
    protected val ADD_ALL_TAG = "$tag:AddAll"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val ADD_TAG = "$tag:Add"
    protected val UPDATE_TAG = "$tag:Update"
    protected val ADD_NOTE_TAG = "$tag:AddNote"
    protected val REMOVE_NOTE_TAG = "$tag:RemoveNote"
    protected val ADD_IMAGE_TO_NOTE_TAG = "$tag:AddImageToNote"

    /** Gets all items from database */
    abstract fun getAll(): List<T>?

    /** Gets an item by its identifier */
    abstract fun getById(identifier: Uuid): T?

    /** Adds all items to the database */
    abstract fun addAll(data: List<T>): Boolean

    /** Removes an item from the database */
    abstract fun remove(identifier: Uuid): Boolean

    /** Adds an item to the database */
    abstract fun add(data: T): Boolean

    /** Updates an item in the database */
    abstract fun update(data: T): Boolean

    /** Adds a note to the database */
    protected fun insertNote(note: Note, images: List<Image>): NoteEntity {
        return transaction {
            // Add new note to database
            val entity = NoteEntity.new(note.identifier.toJavaUuid()) {
                author = note.author
                message = note.message
                time = note.time
            }

            // Add images and keep a list of them to add to note image table
            val imageCollection = images.map { image ->
                ImageEntity.new {
                    path = image.path
                    time = image.time
                }
            }

            // Add images to the note image table
            entity.images = SizedCollection(imageCollection)

            entity
        }
    }

    /** Adds a note to an item and linking it to that item */
    abstract fun addNote(identifier: Uuid, note: Note, images: List<Image>): Boolean

    /** Removes a note and its images from the database */
    abstract fun removeNote(identifier: Uuid): Boolean

    fun addImagesToNote(identifier: Uuid, images: List<Image>) {
        try {
            transaction {
                NoteEntity.findById(identifier.toJavaUuid())?.let { noteEntity ->
                    val imageCollection = images.map { image ->
                        ImageEntity.new {
                            path = image.path
                            time = image.time
                        }
                    }

                    noteEntity.images = SizedCollection(noteEntity.images + imageCollection)

                    AppLogger.m(ADD_IMAGE_TO_NOTE_TAG, SimpleError.SUCCESS)
                } ?: AppLogger.i(ADD_IMAGE_TO_NOTE_TAG, "Note not found!")
            }
        } catch (ex: Exception) {
            AppLogger.i(REMOVE_NOTE_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }
    }
}