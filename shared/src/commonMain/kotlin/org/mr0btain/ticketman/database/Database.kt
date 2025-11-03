package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.error.ImageError
import org.mr0btain.ticketman.error.NoteError
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import java.util.*

// TODO: Add a way to remove images from disk as well

/**
 * Base dDatabase class to inherit from for managing a table of MainItem data type.
 * @param tag Tag used for logging. Example: TAG:AddAll will be printed in console
 */
@Suppress("PropertyName")
abstract class Database<T : MainItem>(protected val tag: String) {
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
    protected val COUNT_TAG = "$tag:Count"
    protected val SEARCH_TAG = "$tag:Search"

    /**
     * Adds an item to the database
     * @param data Item to add
     */
    abstract fun add(data: T)

    /**
     * Adds all items
     * @param data Items to add
     */
    abstract fun addAll(data: List<T>)

    /**
     * Adds a list of images to the ImageTable and links them a note
     * @param identifier Identifier of note to link images to
     * @param images List of images to add
     */
    fun addImagesToNote(identifier: UUID, images: List<StoredImage>) {
        appLogTryCatch(ADD_IMAGE_TO_NOTE_TAG) {
            var result = false

            transaction {
                NoteEntity.findById(identifier)?.let { noteEntity ->
                    val imageCollection = images.map { image ->
                        ImageEntity.new {
                            path = image.path
                        }
                    }

                    noteEntity.images = SizedCollection(noteEntity.images + imageCollection)

                    result = true
                } ?: AppLogger.i(ADD_IMAGE_TO_NOTE_TAG, NoteError.NOT_FOUND)
            }

            result
        }
    }

    /**
     * Adds a note to an item and linking it to that item
     * @param identifier Identifier of item to link note to
     * @param note Note to insert and link to item
     * @param storedImages Images to insert and link to note
     */
    abstract fun addNote(identifier: UUID, note: Note, storedImages: List<StoredImage>)

    /** Gets all items */
    abstract fun all(): List<T>?

    /** Returns the total number of items */
    abstract fun count(): Long

    /**
     * Gets an item by its identifier
     * @param identifier The identifier of the item to retrieve
     */
    abstract fun getById(identifier: UUID): T?

    /**
     * Gets a list of items limited by the page size at the designated offset
     * @param offset The starting offset to get items from
     * @param pageSize The amount of items to fetch
     */
    abstract fun getPagedItems(offset: Long, pageSize: Int): List<T>?

    /**
     * Returns a list of notes and their respective images
     * @param identifier Identifier of item to get notes for
     */
    abstract fun getNotes(identifier: UUID): Map<Note, List<StoredImage>>?

    /**
     * Adds a note to the database
     * @param note Note to insert
     * @param storedImages Stored images to add and link to note
     */
    protected fun insertNote(note: Note, storedImages: List<StoredImage>): NoteEntity? {
        return appLogTryCatch<NoteEntity?>(INSERT_NOTE_TAG) {
            transaction {
                // Add new note to database
                val entity = NoteEntity.new(note.identifier) {
                    author = note.author
                    message = note.message
                }

                if (storedImages.isNotEmpty()) {
                    // Add images and keep a list of them to add to note image table
                    val imageCollection = storedImages.map { image ->
                        ImageEntity.new {
                            path = image.path
                        }
                    }

                    entity.images = SizedCollection(imageCollection)
                }

                Pair(entity, true)
            }
        }
    }

    /**
     * Inserts and image into the ImageTable
     * @param storedImage Image to insert
     */
    fun insertImage(storedImage: StoredImage): ImageEntity? {
        return appLogTryCatch<ImageEntity?>(INSERT_IMAGE_TAG) {
            val entity = transaction {
                ImageEntity.new(storedImage.identifier) {
                    path = storedImage.path
                }
            }

            Pair(entity, true)
        }
    }

    /**
     * Updates an item in the database
     * @param data Item to update
     */
    abstract fun update(data: T)


    /**
     * Updates a note with this data. Essentially replaces the note with the ID with the data passed
     * NOTE: This will delete image entities from the database but NOT the images themselves. for now i guess
     *  @param note Note data
     *  @param storedImages List of images for note
     */
    fun updateNote(note: Note, storedImages: List<StoredImage>) {
        appLogTryCatch(UPDATE_NOTE_TAG) {
            var result = false

            transaction {
                // Add new note to database
                NoteEntity.findByIdAndUpdate(note.identifier) { noteEntity ->
                    noteEntity.author = note.author
                    noteEntity.message = note.message

                    val existingImageIds = noteEntity.images
                        .map { it.id.value }
                        .toMutableList()


                    val updatedImages = mutableListOf<ImageEntity>()

                    storedImages.map { storedImage ->
                        if (existingImageIds.contains(storedImage.identifier))
                            updateImage(storedImage)?.let { updatedImages.add(it) }
                        else
                            insertImage(storedImage)?.let { updatedImages.add(it) }
                    }

                    noteEntity.images.forEach { imageEntity ->
                        if (!updatedImages.contains(imageEntity)) imageEntity.delete()
                    }

                    noteEntity.images = SizedCollection(updatedImages)

                    result = true
                } ?: AppLogger.i(UPDATE_NOTE_TAG, NoteError.NOT_FOUND)
            }

            result
        }
    }

    /**
     * Updates and image in the ImageTable
     * @param storedImage Image to update
     */
    fun updateImage(storedImage: StoredImage): ImageEntity? {
        return appLogTryCatch<ImageEntity?>(UPDATE_IMAGE_TAG) {
            transaction {
                val entity = ImageEntity.findByIdAndUpdate(storedImage.identifier) { imageEntity ->
                    imageEntity.path = storedImage.path
                }

                Pair(entity, entity != null)
            }
        }
    }

    /**
     * Removes an image from ImageTable.
     * @param identifier Identifier of image to remove
     */
    fun removeImage(identifier: UUID) {
        appLogTryCatch(REMOVE_IMAGE_TAG) {
            var result = false

            transaction {
                ImageEntity.findById(identifier)?.let { imageEntity ->
                    imageEntity.delete()

                    result = true
                } ?: AppLogger.i(REMOVE_IMAGE_TAG, ImageError.NOT_FOUND)
            }

            result
        }
    }

    /**
     * Removes a note from the NoteTab;e and its images from the ImageTable
     * @param identifier Identifier of the Note to remove
     */
    fun removeNote(identifier: UUID) {
        appLogTryCatch(REMOVE_NOTE_TAG) {
            var result = false

            transaction {
                NoteEntity.findById(identifier)?.let { noteEntity ->
                    noteEntity.images.forEach { imageEntity ->
                        imageEntity.delete()
                    }

                    noteEntity.delete()

                    result = true
                } ?: AppLogger.i(REMOVE_NOTE_TAG, SimpleResult.FAIL)
            }

            result
        }
    }

    /**
     * Removes an item from the database
     * @param identifier Identifier of item to remove
     */
    abstract fun remove(identifier: UUID)

    /**
     * Search the database. Returns a list of matches
     * @param query The search criteria
     * @param limit The limit of results to return
     */
    abstract fun search(query: String, limit: Int = 20): List<SearchResult>?
}