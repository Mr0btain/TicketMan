package org.mr0btain.ticketman.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Everything this process logs, from [AppLogger] (and on the server, its libraries through logback). Keeps the
 * newest [capacity] lines in memory for following the log live, and writes each to a [LogFileStore] once one is
 * [attach]ed. Lines logged before that are written when it is, so start up isn't lost
 */
object LogBook {
    /** Changes each time the process starts, see [LogTail.run] */
    val run: String = Random.nextLong().toULong().toString(16)

    private val lock = LogLock()
    private val lines = ArrayDeque<LogLine>()
    private var sequence = 0L
    private var store: LogFileStore? = null

    /** Lines kept in memory */
    var capacity: Int = DEFAULT_CAPACITY
        set(value) {
            lock.withLock {
                field = value.coerceAtLeast(1)
                while (lines.size > field) lines.removeFirst()
            }
        }

    private val _last = MutableStateFlow(0L)

    /** The newest line's [LogLine.sequence], changes with each line so a screen can follow along */
    val last: StateFlow<Long> = _last.asStateFlow()

    /** Whether lines are being saved to files */
    val storing: Boolean get() = lock.withLock { store != null }

    /** The file settings in use, or null when nothing is saved */
    val settings: LogSettings? get() = lock.withLock { store?.settings }

    fun record(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val text = if (throwable == null) message else "$message\n${throwable.stackTraceToString().trimEnd()}"
        val line = lock.withLock {
            val line = LogLine(
                sequence = ++sequence,
                time = Clock.System.now().toEpochMilliseconds(),
                level = level,
                tag = tag,
                message = if (text.length > MAX_MESSAGE) text.take(MAX_MESSAGE) + " ..." else text
            )
            lines.addLast(line)
            if (lines.size > capacity) lines.removeFirst()
            store?.write(line)
            line
        }
        _last.value = line.sequence
    }

    /** Starts saving lines to [store], first the ones still in memory. Replaces any store attached before */
    fun attach(store: LogFileStore) = lock.withLock {
        this.store?.close()
        this.store = store
        lines.forEach(store::write)
    }

    /** Stops saving lines to files */
    fun detach() = lock.withLock {
        store?.close()
        store = null
    }

    fun updateSettings(settings: LogSettings) = lock.withLock { store?.update(settings) }

    /** The lines after [after], at most the newest [limit] of them */
    fun since(after: Long, limit: Int = capacity): LogTail = lock.withLock {
        val newer = lines.filter { it.sequence > after }
        LogTail(run = run, lines = newer.takeLast(limit.coerceAtLeast(0)), last = sequence)
    }

    /** Saved log files, oldest first. Empty when nothing is saved */
    fun files(): List<LogFileInfo> = lock.withLock { store?.files() ?: emptyList() }

    /**
     * Writes the lines logged between [from] and [to] (epoch milliseconds, both included, null for no limit) to [out]:
     * from the files when they're saved, else the ones in memory
     * @return How many lines were written
     */
    fun export(from: Long?, to: Long?, out: Sink): Int {
        val (saved, files) = lock.withLock { store?.also { it.flush() } to (store?.files() ?: emptyList()) }
        // Read outside the lock, so logging carries on while a big download is written
        if (saved != null) return saved.export(files, from, to, out)

        val zone = TimeZone.currentSystemDefault()
        val inRange = lock.withLock { lines.filter { (from == null || it.time >= from) && (to == null || it.time <= to) } }
        inRange.forEach { out.writeString(LogFormat.format(it, zone) + "\n") }
        return inRange.size
    }

    /** Forgets every line and the store, for tests */
    fun reset() = lock.withLock {
        store?.close()
        store = null
        lines.clear()
        sequence = 0
        _last.value = 0
    }

    const val DEFAULT_CAPACITY = 3000

    /** Longer messages are cut, a whole response body logged by mistake shouldn't fill the file */
    const val MAX_MESSAGE = 16_000
}
