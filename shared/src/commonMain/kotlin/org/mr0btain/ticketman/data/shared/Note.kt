package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.image.StoredImage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @param customerVisible Shown to the customer on their ticket's status page. Off unless someone turns it on, so
 * notes stay internal by default
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Note(
    override val identifier: Uuid = Uuid.generateV7(),
    val author: String = "",
    val message: String = "",
    val images: List<StoredImage> = emptyList(),
    val customerVisible: Boolean = false
): MainItem(), SubItem