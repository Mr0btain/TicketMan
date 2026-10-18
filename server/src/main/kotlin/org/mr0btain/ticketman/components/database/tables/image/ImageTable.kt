package org.mr0btain.ticketman.components.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/**
 * Photo metadata. The files themselves are in [org.mr0btain.ticketman.components.image.ImageStore], found by id
 */
object ImageTable : UuidTable("image_table") {
    /** Folder under the image store, kept for old rows. Files are always found by id */
    val directory = varchar("path", 512).default("")
    val description = text("description")
    val tags = array<String>("tags")

    // Added with uploads
    val width = integer("width").default(0)
    val height = integer("height").default(0)
    val sizeBytes = long("size_bytes").default(0)
    val sha256 = varchar("sha256", 64).default("")
    val uploadedBy = uuid("uploaded_by").nullable()
    /** An [ImageStatus] name. Rows from before uploads were linked to something */
    val status = varchar("status", 16).default(ImageStatus.LINKED.name)
    /** Epoch millis of the last status change, the sweeper purges from this */
    val statusChangedAt = long("status_changed_at").default(0)
}

/**
 * Where a photo is in its life. Files are only removed by the sweeper, a day after a photo is deleted or left
 * pending, so a backup taken mid delete still finds every file its database points to
 */
enum class ImageStatus {
    /** Uploaded but not saved onto a note or item yet */
    PENDING,

    /** On a note or item */
    LINKED,

    /** Removed, its files go with the next sweep a day on */
    DELETED
}
