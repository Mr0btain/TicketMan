package org.mr0btain.ticketman.ui.screens.records.components.user

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.RecordSelection
import org.mr0btain.ticketman.components.data.user.UserCache
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.Result
import kotlin.uuid.Uuid

/**
 * Users: the list and the one opened. Changes to users go through the admin screen, which calls [onChanged]
 * @param cacheSize How many opened users to keep
 * @param searchLimit Most search results to ask for
 */
@Suppress("PrivatePropertyName")
class UserRecords(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val isAdmin: () -> Boolean,
    pagerConfig: OffsetPagerConfig,
    cacheSize: Int,
    private val searchLimit: () -> Int
) {
    private val PAGER_TAG = "UserPager"

    val cache = UserCache(cacheSize)

    val pager = OffsetPager<User>(
        tag = PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            remoteHandler.userRepo.fetch(
                offset = (page * pageSize).toLong(),
                limit = pageSize
            )
        }
    )

    val seed = UserSeed()

    /** User being loaded after a click, the list highlights it until it's in the seed */
    val selection = RecordSelection(scope)

    /**
     * Opens a user in the seed, from the cache if it's there
     * @param force Reload even if it's already selected, used after the user was changed
     */
    fun select(identifier: Uuid, force: Boolean = false) {
        if (!force && (seed.identifier == identifier || selection.pending.value == identifier)) return

        selection.select(identifier) {
            val (user, notes) = cache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.userRepo.getNotes(identifier) }
                val user = remoteHandler.userRepo.get(identifier) ?: return@coroutineScope null

                UserCache.UserData(user, notes.await(), emptyList())
                    .also { cache.put(it.user, it.notes) }
            } ?: return@select null

            return@select { seed.setUser(user, notes) }
        }
    }

    /** Empties the seed, dropping any user still loading */
    fun clearSelection() {
        selection.cancel()
        seed.clear()
    }

    /**
     * Adds or edits a staff note on the user showing. It's saved straight away, since users are edited through the
     * admin dialogs rather than this seed. Only admins can, the server checks too
     */
    fun saveNote(note: Note) {
        if (!isAdmin()) return

        val identifier = seed.identifier
        val repo = remoteHandler.userRepo
        val isEdit = seed.getNote(note.identifier) != null

        scope.launch {
            val result = if (isEdit) repo.updateNote(note) else repo.addNote(identifier, note)

            if (result is Result.Error) {
                snackBarHost.showSnackbar("Note not saved: ${result.error.message}")
                return@launch
            }

            // Reloaded so a new note comes back signed by whoever wrote it
            val notes = repo.getNotes(identifier)
            cache.remove(identifier)

            if (seed.identifier == identifier) {
                seed.setNotes(notes)
                seed.markClean()
            }
        }
    }

    /** Deletes a staff note on the user showing straight away, its photos go with it. Only admins can */
    fun removeNote(noteIdentifier: Uuid) {
        if (!isAdmin()) return

        val identifier = seed.identifier
        val repo = remoteHandler.userRepo

        scope.launch {
            val result = repo.removeNote(noteIdentifier)

            if (result is Result.Error) {
                snackBarHost.showSnackbar("Note not deleted: ${result.error.message}")
                return@launch
            }

            cache.remove(identifier)

            if (seed.identifier == identifier) {
                seed.deleteNote(noteIdentifier)
                seed.markClean()
            }
        }
    }

    /** Users matching [query], at most the search limit setting */
    suspend fun search(query: String) =
        remoteHandler.userRepo.search(query, searchLimit())

    /** Drops stale copies of a changed user and reloads the list, [identifier] is null when the user is gone */
    fun onChanged(identifier: Uuid?) {
        scope.launch {
            if (identifier != null) {
                cache.remove(identifier)
                if (seed.identifier == identifier) select(identifier, force = true)
            } else {
                clearSelection()
            }

            pager.refreshCurrentData()
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}
