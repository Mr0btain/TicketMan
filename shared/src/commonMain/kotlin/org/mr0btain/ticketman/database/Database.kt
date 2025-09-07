package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainData
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

// TODO: Add a way to remove images

@Suppress("PropertyName")
@OptIn(ExperimentalUuidApi::class)
abstract class Database<T: MainData>(
    tag: String,
    private val shouldLog: Boolean
) {
    protected val GET_ALL_TAG = "$tag:GetAll"
    protected val GET_BY_ID_TAG = "$tag:GetById"
    protected val GET_PAGED_ITEMS_TAG = "$tag:GetPagedItems"
    protected val ADD_ALL_TAG = "$tag:AddAll"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val ADD_TAG = "$tag:Add"
    protected val UPDATE_TAG = "$tag:Update"
    protected val INSERT_NOTE_TAG = "$tag:InsertNote"
    protected val ADD_NOTE_TAG = "$tag:AddNote"
    protected val GET_NOTES_TAG = "$tag:GetNotes"
    protected val REMOVE_NOTE_TAG = "$tag:RemoveNote"
    protected val ADD_IMAGE_TO_NOTE_TAG = "$tag:AddImageToNote"
    protected val UPDATE_NOTE_TAG = "$tag:UpdateNote"
    protected val INSERT_IMAGE_TAG = "$tag:AddImage"
    protected val UPDATE_IMAGE_TAG = "$tag:UpdateImage"
    protected val REMOVE_IMAGE_TAG = "$tag:RemoveImage"

    /** Gets all items from database */
    abstract fun all(): List<T>?

    /** Gets an item by its identifier */
    abstract fun getById(identifier: Uuid): T?

    abstract fun getPagedItems(offset: Long, pageSize: Int): List<T>

    abstract fun count(): Long

    /** Adds all items to the database */
    abstract fun addAll(data: List<T>): Boolean

    /** Removes an item from the database */
    abstract fun remove(identifier: Uuid): Boolean

    /** Adds an item to the database */
    abstract fun add(data: T): Boolean

    /** Updates an item in the database */
    abstract fun update(data: T): Boolean

    /** Adds a note to the database */
    protected fun insertNote(note: Note, storedImages: List<StoredImage>): NoteEntity? {
        return try {
            transaction {
                // Add new note to database
                val entity = NoteEntity.new(note.identifier.toJavaUuid()) {
                    author = note.author
                    message = note.message
                    time = note.time
                }

                if (storedImages.isNotEmpty()) {
                    // Add images and keep a list of them to add to note image table
                    val imageCollection = storedImages.map { image ->
                        ImageEntity.new {
                            path = image.path
                            time = image.time
                        }
                    }

                    entity.images = SizedCollection(imageCollection)
                }

                entity
            }
        } catch (ex: Exception) {
            AppLogger.i(INSERT_NOTE_TAG, SimpleResult.FAIL)
            null
        }
    }

    /** Adds a note to an item and linking it to that item */
    abstract fun addNote(identifier: Uuid, note: Note, storedImages: List<StoredImage>): Boolean

    fun updateNote(note: Note, storedImages: List<StoredImage>) {
        try {
            transaction {
                // Add new note to database
                val noteEntity = NoteEntity.findByIdAndUpdate(note.identifier.toJavaUuid()) { entity ->
                    entity.author = note.author
                    entity.message = note.message
                    entity.time = note.time
                }

                val existingImageIds = noteEntity?.images?.map { it.id.value.toKotlinUuid() }?: emptyList()
                val newImages = mutableListOf<StoredImage>()

                if (storedImages.isNotEmpty()) {
                    storedImages.forEach { storedImage ->
                        if (existingImageIds.contains(storedImage.identifier)) {
                            updateImage(storedImage)
                        } else newImages.add(storedImage)
                    }

                    addImagesToNote(note.identifier, newImages)
                }
            }

            AppLogger.m(UPDATE_NOTE_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.i(INSERT_NOTE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }
    }

    fun insertImage(storedImage: StoredImage): ImageEntity? {
        return try {
            val entity = transaction {
                ImageEntity.new(storedImage.identifier.toJavaUuid()) {
                    path = storedImage.path
                    time = storedImage.time
                }
            }

            AppLogger.m(INSERT_IMAGE_TAG, SimpleResult.SUCCESS)

            entity

        } catch (ex: Exception) {
            AppLogger.i(INSERT_IMAGE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
            null
        }
    }

    fun updateImage(storedImage: StoredImage) {
        try {
            transaction {
                ImageEntity.findByIdAndUpdate(storedImage.identifier.toJavaUuid()) { imageEntity ->
                    imageEntity.path = storedImage.path
                    imageEntity.time = storedImage.time

                    AppLogger.m(UPDATE_IMAGE_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(UPDATE_IMAGE_TAG, "Image not found!")
            }
        } catch (ex: Exception) {
            AppLogger.i(UPDATE_IMAGE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }
    }

    fun removeImage(noteId:Uuid, imageId: Uuid) {
        try {
            transaction {
                ImageEntity.findById(imageId.toJavaUuid())?.let { imageEntity ->
                    NoteEntity.findByIdAndUpdate(noteId.toJavaUuid()) { noteEntity ->
                        noteEntity.images = SizedCollection(noteEntity.images.filter { it.id != imageEntity.id })

                        AppLogger.m(REMOVE_IMAGE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(REMOVE_IMAGE_TAG, "Note not found!")
                } ?: AppLogger.i(REMOVE_IMAGE_TAG, "Image not found!")
            }
        } catch (ex: Exception) {
            AppLogger.i(REMOVE_IMAGE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }
    }

    /** Returns a list of pairs of note and their respective images as bytearrays to be converted to a usable image */
    abstract fun getNotes(identifier: Uuid): Map<Note, List<StoredImage>>

    /** Removes a note and its images from the database */
    abstract fun removeNote(identifier: Uuid): Boolean

    fun addImagesToNote(identifier: Uuid, images: List<StoredImage>) {
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

                    AppLogger.m(ADD_IMAGE_TO_NOTE_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(ADD_IMAGE_TO_NOTE_TAG, "Note not found!")
            }
        } catch (ex: Exception) {
            AppLogger.i(REMOVE_NOTE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }
    }
}