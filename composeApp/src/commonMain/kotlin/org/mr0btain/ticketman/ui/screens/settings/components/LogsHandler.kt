package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.data.saveFile
import org.mr0btain.ticketman.components.logging.ClientLogs
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.logging.LogBook
import org.mr0btain.ticketman.logging.LogFileInfo
import org.mr0btain.ticketman.logging.LogLine
import org.mr0btain.ticketman.logging.LogOverview
import org.mr0btain.ticketman.logging.LogSettings

/** Whose log the logs viewer shows */
enum class LogSource(val label: String) {
    DEVICE("This device"),
    SERVER("Server")
}

/**
 * The logs viewer on Settings: following this device's log or (for admins) the server's live, how each keeps its
 * files, and downloading them
 * @param isAdmin Only admins see the server's log, the server refuses anyone else too
 */
class LogsHandler(
    private val remoteHandler: RemoteHandler,
    private val localStorage: LocalStorage,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val isAdmin: () -> Boolean
) {
    private val repo get() = remoteHandler.adminRepo

    /** Whether this device keeps files, the web only keeps what it logged since the page opened */
    val savesFiles: Boolean get() = ClientLogs.savesFiles

    private val _deviceSettings = MutableStateFlow(localStorage.logSettings)
    val deviceSettings = _deviceSettings.asStateFlow()

    private val _deviceFiles = MutableStateFlow<List<LogFileInfo>>(emptyList())
    val deviceFiles = _deviceFiles.asStateFlow()

    private val _serverOverview = MutableStateFlow<LogOverview?>(null)
    /** The server's files and settings, null until loaded */
    val serverOverview = _serverOverview.asStateFlow()

    private val _source = MutableStateFlow(LogSource.DEVICE)
    val source = _source.asStateFlow()

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    /** What the viewer shows, oldest first, the newest [VIEW_LIMIT] */
    val lines = _lines.asStateFlow()

    private val _paused = MutableStateFlow(false)
    /** Paused holds the lines shown still, new ones are picked up again on resuming */
    val paused = _paused.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    /** Why the server's log couldn't be read, while it can't */
    val problem = _problem.asStateFlow()

    private var following: Job? = null

    /** Starts following [source]'s log, called when the viewer opens */
    fun open(source: LogSource = _source.value) {
        val picked = if (source == LogSource.SERVER && !isAdmin()) LogSource.DEVICE else source
        _source.value = picked
        _paused.value = false
        _problem.value = null
        _lines.value = emptyList()
        following?.cancel()
        following = if (picked == LogSource.DEVICE) followDevice() else followServer()
        refreshFiles()
    }

    /** Stops following, when the viewer closes */
    fun close() {
        following?.cancel()
        following = null
        _lines.value = emptyList()
    }

    fun togglePaused() {
        _paused.value = !_paused.value
    }

    /** Empties the viewer, lines logged after still show */
    fun clearView() {
        _lines.value = emptyList()
    }

    /** The device's file list, and the server's for an admin */
    fun refreshFiles() {
        _deviceFiles.value = LogBook.files()
        if (!isAdmin()) return

        scope.launch {
            when (val result = repo.getLogs()) {
                is TmResult.Success -> _serverOverview.value = result.data
                is TmResult.Error -> _serverOverview.value = null
            }
        }
    }

    /** Applies straight away: old files past the new limits are deleted, and the file changes if the time moved */
    fun setDeviceSettings(settings: LogSettings) {
        val value = settings.normalized()
        localStorage.logSettings = value
        _deviceSettings.value = value
        LogBook.updateSettings(value)
        _deviceFiles.value = LogBook.files()
    }

    /** Saves how the server keeps its logs, telling the user if it didn't */
    fun setServerSettings(settings: LogSettings) {
        scope.launch {
            when (val result = repo.updateLogSettings(settings.normalized())) {
                is TmResult.Success -> {
                    _serverOverview.value = result.data
                    snackBarHost.showSnackbar("Server log settings saved")
                }
                is TmResult.Error -> snackBarHost.showSnackbar("Couldn't save the server's log settings: ${result.error.message}")
            }
        }
    }

    /**
     * Saves the lines logged between [from] and [to] (epoch milliseconds, null for no limit) as a `.log` file
     * @param name What the range is called in the file name, like `2026-11-06` or `all`
     * @param onResult What went wrong, or null once it's saved or the save was cancelled
     */
    fun download(source: LogSource, from: Long?, to: Long?, name: String, onResult: (String?) -> Unit) {
        scope.launch {
            val bytes = when (source) {
                LogSource.DEVICE -> withContext(Dispatchers.Default) {
                    val buffer = Buffer()
                    if (LogBook.export(from, to, buffer) == 0) null else buffer.readByteArray()
                }
                LogSource.SERVER -> when (val result = repo.downloadLogs(from, to)) {
                    is TmResult.Success -> result.data.takeIf { it.isNotEmpty() }
                    is TmResult.Error -> return@launch onResult("Couldn't download the server's log: ${result.error.message}")
                }
            }

            if (bytes == null) return@launch onResult("Nothing was logged then")

            val fileName = "ticketman-${if (source == LogSource.SERVER) "server" else "device"}-$name"
            if (saveFile(bytes, fileName, "log")) snackBarHost.showSnackbar("Saved $fileName.log")
            onResult(null)
        }
    }

    private fun followDevice() = scope.launch {
        var after = 0L
        LogBook.last.collect {
            if (_paused.value) return@collect
            val tail = LogBook.since(after, VIEW_LIMIT)
            after = tail.last
            append(tail.lines)
        }
    }

    private fun followServer() = scope.launch {
        var run: String? = null
        var after = 0L

        while (isActive) {
            if (!_paused.value) {
                when (val result = repo.tailLogs(after, VIEW_LIMIT)) {
                    is TmResult.Success -> {
                        val tail = result.data
                        _problem.value = null
                        if (run != null && tail.run != run) {
                            // The server restarted, its lines are counted again from 1
                            run = tail.run
                            after = 0
                            _lines.value = emptyList()
                            continue
                        }
                        run = tail.run
                        after = tail.last
                        append(tail.lines)
                    }
                    is TmResult.Error -> _problem.value = result.error.message
                }
            }
            delay(SERVER_POLL_MILLIS)
        }
    }

    private fun append(newLines: List<LogLine>) {
        if (newLines.isEmpty()) return
        _lines.value = (_lines.value + newLines).takeLast(VIEW_LIMIT)
    }

    companion object {
        /** Lines the viewer holds */
        const val VIEW_LIMIT = 1000

        /** How often the server's log is asked for new lines while it's shown */
        const val SERVER_POLL_MILLIS = 2_000L
    }
}
