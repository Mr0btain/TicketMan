package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.image.ImageEntity
import org.mr0btain.ticketman.components.database.tables.image.ImageStatus
import org.mr0btain.ticketman.components.database.tables.image.ImageTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryImageTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.note.NoteImageTable
import org.mr0btain.ticketman.components.image.ImageProcessor
import org.mr0btain.ticketman.data.image.StoredImage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Photo rows. Linking photos onto notes and items happens in [linkImages], inside those tables' transactions
 * @param recordDatabases Every record type's database, whose notes keep their photos, see [LocalDatabase.recordDatabases]
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class ImageDatabase(private val recordDatabases: () -> List<Database<*>>) {
    private val TAG = "ImageDatabase"
    private val INSERT_TAG = "$TAG:InsertPending"
    private val GET_TAG = "$TAG:Get"
    private val SWEEP_TAG = "$TAG:Sweep"
    private val COUNT_TAG = "$TAG:Count"

    /** A photo with what's needed to serve it */
    data class ImageRecord(
        val image: StoredImage,
        val status: ImageStatus,
        val sha256: String,
        val uploadedBy: Uuid?
    )

    /**
     * Adds a freshly uploaded photo, pending until a note or item is saved with it
     * @param folder Its folder in the image store
     */
    fun insertPending(
        identifier: Uuid,
        description: String,
        uploadedBy: Uuid?,
        processed: ImageProcessor.Processed,
        folder: String
    ): StoredImage? =
        dbQuery(INSERT_TAG) {
            val entity = ImageEntity.new(identifier) {
                this.directory = folder
                this.description = description
                this.tags = emptyList()
                this.width = processed.width
                this.height = processed.height
                this.sizeBytes = processed.sizeBytes
                this.sha256 = processed.sha256
                this.uploadedBy = uploadedBy
                this.status = ImageStatus.PENDING.name
                this.statusChangedAt = System.currentTimeMillis()
            }

            entity.toStoredImage()
        }

    /** A photo that hasn't been deleted, null if there's no such photo */
    fun getServable(identifier: Uuid): ImageRecord? =
        dbQuery(GET_TAG) {
            ImageEntity.findById(identifier)
                ?.takeIf { it.imageStatus != ImageStatus.DELETED }
                ?.let { ImageRecord(it.toStoredImage(), it.imageStatus, it.sha256, it.uploadedBy) }
        }

    /** Ids of every row, deleted ones too, so the sweeper knows which folders on disk still belong to something */
    fun allIdentifiers(): Set<Uuid> =
        dbQuery(GET_TAG) {
            ImageTable.select(ImageTable.id).map { it[ImageTable.id].value }.toSet()
        } ?: emptySet()

    /** Ids of photos that aren't deleted, which are the ones a backup carries */
    fun liveIdentifiers(): List<Uuid> =
        dbQuery(COUNT_TAG) {
            ImageTable.select(ImageTable.id)
                .where { ImageTable.status neq ImageStatus.DELETED.name }
                .map { it[ImageTable.id].value }
        } ?: emptyList()

    /**
     * Housekeeping, run every so often:
     * 1. Linked photos nothing points at any more (their note's record was deleted) become deleted. A note, an
     *    inventory item or a user's profile photo counts
     * 2. Photos deleted or left pending for longer than [graceMillis] lose their rows
     * @return Ids whose files should now be removed
     */
    fun sweep(now: Long, graceMillis: Long): List<Uuid> =
        dbQuery(SWEEP_TAG) {
            markUnreferencedDeleted(now)

            val cutoff = now - graceMillis
            val expired = ImageTable.select(ImageTable.id)
                .where {
                    (ImageTable.status inList listOf(ImageStatus.PENDING.name, ImageStatus.DELETED.name)) and
                        (ImageTable.statusChangedAt less cutoff)
                }
                .map { it[ImageTable.id].value }

            // Join rows go with them through their cascades
            if (expired.isNotEmpty()) ImageTable.deleteWhere { ImageTable.id inList expired }

            expired
        } ?: emptyList()

    private fun markUnreferencedDeleted(now: Long) {
        // Notes still attached to a record of any type
        val attachedNotes = attachedNotes(recordDatabases())

        val referenced = buildSet {
            NoteImageTable.selectAll().forEach { row ->
                if (row[NoteImageTable.note].value in attachedNotes) add(row[NoteImageTable.image].value)
            }
            addAll(InventoryImageTable.select(InventoryImageTable.image).map { it[InventoryImageTable.image].value })
            // Profile photos
            addAll(UserTable.select(UserTable.photo).mapNotNull { it[UserTable.photo] })
        }

        val unreferenced = ImageTable.select(ImageTable.id)
            .where { ImageTable.status eq ImageStatus.LINKED.name }
            .map { it[ImageTable.id].value }
            .filter { it !in referenced }

        if (unreferenced.isNotEmpty()) {
            ImageTable.update({ ImageTable.id inList unreferenced }) { row ->
                row[ImageTable.status] = ImageStatus.DELETED.name
                row[ImageTable.statusChangedAt] = now
            }
        }
    }
}

/**
 * Works out which photos a note or item should have after a save. Must run inside a transaction
 *
 * A photo is taken if it's already on this owner, or it's pending (just uploaded). Anything else the client
 * lists, like a made up id or a photo on another note, is ignored. Photos the owner had but aren't listed any
 * more are marked deleted. Descriptions and tags come from [requested]
 * @param requested What the client wants the owner to have
 * @param current What the owner has now
 * @return The photos to set on the owner
 */
@OptIn(ExperimentalUuidApi::class)
fun linkImages(requested: List<StoredImage>, current: List<ImageEntity>): List<ImageEntity> {
    val now = System.currentTimeMillis()
    val currentIds = current.map { it.id.value }.toSet()

    val linked = requested.distinctBy { it.identifier }.mapNotNull { image ->
        val entity = ImageEntity.findById(image.identifier) ?: return@mapNotNull null
        val allowed = entity.id.value in currentIds || entity.imageStatus == ImageStatus.PENDING

        if (!allowed) return@mapNotNull null

        entity.description = image.description
        entity.tags = image.tags
        entity.changeStatus(ImageStatus.LINKED, now)
        entity
    }

    val linkedIds = linked.map { it.id.value }.toSet()
    current.filter { it.id.value !in linkedIds }.forEach { it.changeStatus(ImageStatus.DELETED, now) }

    return linked
}

/** Marks photos deleted, their files go with a later sweep. Must run inside a transaction */
fun markImagesDeleted(images: Iterable<ImageEntity>) {
    val now = System.currentTimeMillis()
    images.forEach { it.changeStatus(ImageStatus.DELETED, now) }
}
