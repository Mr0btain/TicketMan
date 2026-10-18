package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.mr0btain.ticketman.components.database.tables.image.ImageEntity
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.error.Error
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.error.ImageError
import org.mr0btain.ticketman.error.NoteError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base Database class to inherit from for managing a table of MainItem data type.
 * @param tag Tag used for logging. Example: TAG:AddAll will be printed in console
 */
@Suppress("PropertyName")
@OptIn(ExperimentalUuidApi::class)
abstract class Database<T : MainItem>(protected val tag: String) {
    protected val GET_BY_ID_TAG = "$tag:GetById"
    protected val GET_PAGED_ITEMS_TAG = "$tag:GetPagedItems"
    protected val ADD_ALL_TAG = "$tag:AddAll"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val ADD_TAG = "$tag:Add"
    protected val UPDATE_TAG = "$tag:Update"
    protected val ADD_NOTE_TAG = "$tag:AddNote"
    protected val GET_NOTES_TAG = "$tag:GetNotes"
    protected val REMOVE_NOTE_TAG = "$tag:RemoveNote"
    protected val ADD_IMAGE_TO_NOTE_TAG = "$tag:AddImageToNote"
    protected val UPDATE_NOTE_TAG = "$tag:UpdateNote"
    protected val REMOVE_IMAGE_TAG = "$tag:RemoveImage"
    protected val COUNT_TAG = "$tag:Count"
    protected val SEARCH_TAG = "$tag:Search"

    /**
     * The note column of the table linking this kind of item to its notes. Note routes take a note's identifier
     * alone, so changing a note checks it's on this kind of item first, or a customer route could edit a staff note
     */
    protected abstract val noteLinkColumn: Column<EntityID<Uuid>>

    // A note this database's items own, null if there's none or it's on another kind of item. Inside a transaction
    private fun findOwnNote(identifier: Uuid): NoteEntity? =
        NoteEntity.findById(identifier)?.takeIf {
            noteLinkColumn.table.selectAll().where { noteLinkColumn eq identifier }.any()
        }

    /**
     * Adds an item to the database
     * @param data Item to add
     * @return Whether it was saved
     */
    abstract fun add(data: T): Boolean

    /**
     * Adds all items, or none of them if one fails
     * @param data Items to add
     * @return Whether they were saved
     */
    abstract fun addAll(data: List<T>): Boolean

    /**
     * Links uploaded images onto a note, see [linkImages] for which are taken
     * @param identifier Identifier of note to link images to
     * @param images Uploaded images to add
     * @return Whether the note was found
     */
    fun addImagesToNote(identifier: Uuid, images: List<StoredImage>): Boolean =
        dbWrite(ADD_IMAGE_TO_NOTE_TAG) {
            val noteEntity = findOwnNote(identifier)
                ?: return@dbWrite false.also { AppLogger.i(ADD_IMAGE_TO_NOTE_TAG, NoteError.NOT_FOUND) }

            val current = noteEntity.images.toList()
            noteEntity.images = SizedCollection(linkImages(current.map { it.toStoredImage() } + images, current))

            true
        }

    /**
     * Adds a note to an item and linking it to that item
     * @param identifier Identifier of item to link note to
     * @param note Note to insert and link to item
     * @return Whether the item was found and the note saved
     */
    abstract fun addNote(identifier: Uuid, note: Note): Boolean

    /** Returns the total number of items */
    abstract fun count(): Long

    /**
     * Gets an item by its identifier
     * @param identifier The identifier of the item to retrieve
     */

    abstract fun getById(identifier: Uuid): T?

    /**
     * Gets a list of items limited by the page size at the designated offset
     * @param offset The starting offset to get items from
     * @param pageSize The amount of items to fetch
     */
    abstract fun getPagedItems(request: PagedRequest): List<T>?

    /**
     * Returns a list of notes and their respective images
     * @param identifier Identifier of item to get notes for
     */
    abstract fun getNotes(identifier: Uuid): List<Note>?

    /**
     * Adds a note to the database. Its images must have been uploaded first, see [linkImages].
     * Must be called inside a transaction, and throws so that transaction rolls back too
     * @param note Note to insert
     */
    protected fun insertNote(note: Note): NoteEntity {
        val linked = linkImages(note.images, emptyList())

        return NoteEntity.new(note.identifier) {
            author = note.author
            message = note.message
        }.also { entity -> entity.images = SizedCollection(linked) }
    }

    /**
     * Inserts [note] and attaches it to the item [find] returns, for [addNote]
     * @param notFound Logged when there's no such item
     */
    protected fun <E> attachNote(note: Note, notFound: Error, find: () -> E?, attach: (E, NoteEntity) -> Unit): Boolean =
        dbWrite(ADD_NOTE_TAG) {
            val item = find() ?: return@dbWrite false.also { AppLogger.i(ADD_NOTE_TAG, notFound) }

            attach(item, insertNote(note))
            true
        }

    /**
     * Updates an item in the database
     * @param data Item to update
     * @return Whether the item was found and saved
     */
    abstract fun update(data: T): Boolean


    /**
     * Updates a note's text and images. Images left off are marked deleted and their files go with a later sweep
     * @param note Note data
     * @return Whether the note was found
     */
    fun updateNote(note: Note): Boolean =
        dbWrite(UPDATE_NOTE_TAG) {
            val noteEntity = findOwnNote(note.identifier)
                ?: return@dbWrite false.also { AppLogger.i(UPDATE_NOTE_TAG, NoteError.NOT_FOUND) }

            noteEntity.author = note.author
            noteEntity.message = note.message
            noteEntity.images = SizedCollection(linkImages(note.images, noteEntity.images.toList()))

            true
        }

    /**
     * Marks an image deleted, its files go with a later sweep
     * @param identifier Identifier of image to remove
     * @return Whether the image was found
     */
    fun removeImage(identifier: Uuid): Boolean =
        dbWrite(REMOVE_IMAGE_TAG) {
            val imageEntity = ImageEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_IMAGE_TAG, ImageError.NOT_FOUND) }

            markImagesDeleted(listOf(imageEntity))
            true
        }

    /**
     * Removes a note, marking its images deleted
     * @param identifier Identifier of the Note to remove
     * @return Whether the note was found
     */
    fun removeNote(identifier: Uuid): Boolean =
        dbWrite(REMOVE_NOTE_TAG) {
            val noteEntity = findOwnNote(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_NOTE_TAG, NoteError.NOT_FOUND) }

            deleteNotes(listOf(noteEntity))
            true
        }

    /**
     * Deletes notes and marks their images deleted. Must run inside a transaction. When removing an item, take its
     * notes before deleting it: the join rows go with the item, the notes don't
     */
    protected fun deleteNotes(notes: Iterable<NoteEntity>) {
        notes.forEach { noteEntity ->
            markImagesDeleted(noteEntity.images)
            noteEntity.delete()
        }
    }

    /**
     * Removes an item from the database
     * @param identifier Identifier of item to remove
     * @return Whether the item was found and removed
     */
    abstract fun remove(identifier: Uuid): Boolean

    /**
     * Search the database. Returns a list of matches
     * @param query The search criteria
     * @param limit The limit of results to return
     */
    abstract fun search(query: String, limit: Int = 20): List<SearchResult>?
}