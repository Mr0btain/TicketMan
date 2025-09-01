package org.mr0btain.ticketman.components.database

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.network.database.ServerDatabaseClient
import org.mr0btain.ticketman.components.utils.toByteArray
import org.mr0btain.ticketman.components.utils.toImageBitmap
import org.mr0btain.ticketman.data.MainData
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.database.LocalDatabaseClient
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base class for a MainDataItem handler
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
abstract class DatabaseHandler<T: MainData>(
    tag: String,
    private val localDatabaseClient: LocalDatabaseClient<T>,
    private val serverDatabaseClient: ServerDatabaseClient,
    private val imageDir: String
) {
    // TODO: Don't know if we even have to handle logging as in the clients themselves handle it
    private val ADD_TAG = "$tag:Add"
    private val ADD_ALL_TAG = "$tag:AddAll"
    private val UPDATE_TAG = "$tag:Update"
    private val REMOVE_TAG = "$tag:Remove"
    private val UPDATE_OR_ADD_TAG = "$tag:UpdateOrAdd"

    /** Adds or updates an item depending on weather it exists in the database or not */
    suspend fun addOrUpdate(data: T) {
        coroutineScope {
            // Check if customer exists or not, if not then add it
            if (_dataList.value.firstOrNull { it.identifier == data.identifier } != null) {
                // Get index of the item
                val index = _dataList.value.indexOfFirst { it.identifier == data.identifier }

                // update list with new data
                _dataList.value = dataList.value.toMutableList().also { it[index] = data }

                // Update database
                if (localDatabaseClient.update(data)) {
                    serverDatabaseClient.update(data)
                    AppLogger.m(UPDATE_OR_ADD_TAG, SimpleError.SUCCESS)
                } else AppLogger.i(UPDATE_OR_ADD_TAG, SimpleError.FAIL)
            } else {
                // Else add to list in view model
                _dataList.value = dataList.value + data

                // Add to database
                if (localDatabaseClient.add(data)) {
                    serverDatabaseClient.send(data)
                    AppLogger.m(UPDATE_OR_ADD_TAG, SimpleError.SUCCESS)
                } else AppLogger.i(UPDATE_OR_ADD_TAG, SimpleError.FAIL)
            }

            // Set selected customer
            _selectedId.value = data.identifier
        }
    }

    /** Syncs data between server and local databases */
    suspend fun sync() {
        localDatabaseClient.getAll()?.let { serverDatabaseClient.sync(it) }
    }

    /** Gets an item by identifier */
    fun get(identifier: Uuid): T? = localDatabaseClient.getById(identifier)

    fun getNotes(identifier: Uuid): List<NoteData> {
        val databaseResult = localDatabaseClient.getNotes(identifier)
        val result = mutableListOf<NoteData>()

        databaseResult.forEach {
            val imageData = mutableListOf<ImageBitmap>()

            it.value.forEach { image ->
                val fileHandler = FileKit.databasesDir / image.path

                if (fileHandler.file.exists()) {
                    imageData.add(
                        fileHandler.file
                            .readBytes()
                            .toImageBitmap()
                    )
                }
            }

            result.add(
                NoteData(
                    note = it.key,
                    images = imageData
                )
            )
        }

        return result
    }

    // TODO: Add serverside
    suspend fun addImagesToNote(identifier: Uuid, images: List<ImageBitmap>) {
        val imageResult = mutableListOf<StoredImage>()

        // Save images to disk first
        images.forEach { imageBitmap ->
            // Get id and file
            val id = Uuid.Companion.random().toString()
            val file = FileKit.databasesDir / imageDir / id

            // Write file
            file.write(imageBitmap.toByteArray())

            // Add result
            imageResult.add(
                StoredImage(
                    path = imageDir + id,
                    time = getTime()
                )
            )
        }

        // Send images to database
        localDatabaseClient.addImagesToNote(identifier, imageResult)
    }

    /** Adds an item to the databases */
    suspend fun add(item: T) {
        coroutineScope {
            try {
                _dataList.value += item
                localDatabaseClient.add(item)
                serverDatabaseClient.send(item)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(ADD_TAG, SimpleError.FAIL)
            }

            AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
        }
    }

    /** Adds a list of items to the database */
    suspend fun addAll(data: List<T>) {
        coroutineScope {
            try {
                _dataList.value += data
                localDatabaseClient.addAll(data)
                serverDatabaseClient.batchSend(data)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            }

            AppLogger.m(ADD_ALL_TAG, SimpleError.SUCCESS)
        }
    }

    /** Removes an item from the database */
    suspend fun remove(identifier: Uuid) {
        coroutineScope {
            try {
                _dataList.value = dataList.value.filter { it.identifier != identifier }
                localDatabaseClient.remove(identifier)
                serverDatabaseClient.remove(identifier)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(REMOVE_TAG, SimpleError.FAIL)
            }

            AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
        }
    }
}