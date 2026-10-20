package org.mr0btain.ticketman.components.service

import io.ktor.server.config.*
import org.mr0btain.ticketman.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Restarting the server from the admin screen, which a restore needs. The server can't start itself again, so it
 * exits with [EXIT_CODE] and leaves that to whatever runs it (Docker's restart policy, systemd's `Restart=`). Only
 * offered when `RESTART_ON_EXIT=true` says something will
 * @param enabled `env.restartOnExit`
 */
@Suppress("PrivatePropertyName")
class ServerRestart(val enabled: Boolean) {
    private val TAG = "ServerRestart"

    private val requested = AtomicBoolean(false)

    /**
     * Exits after [delayMillis], so the answer saying it's happening gets sent first. Runs on its own thread: the
     * JVM's shutdown hooks stop Ktor and close H2, and stopping Ktor waits on the request threads
     * @return False when it isn't [enabled] or a restart is already on its way
     */
    fun request(delayMillis: Long = EXIT_DELAY_MILLIS): Boolean {
        if (!enabled || !requested.compareAndSet(false, true)) return false

        AppLogger.m(TAG, "Restarting, asked for from the admin screen")
        thread(name = "server-restart", isDaemon = false) {
            Thread.sleep(delayMillis)
            exit(EXIT_CODE)
        }
        return true
    }

    companion object {
        /** Not 0, so a restart policy that only restarts on failure (`on-failure`, systemd's `on-failure`) still does */
        const val EXIT_CODE = 3

        const val EXIT_DELAY_MILLIS = 1000L

        /** How the process ends. Tests replace it so they keep running */
        var exit: (Int) -> Unit = { code -> exitProcess(code) }

        fun from(config: ApplicationConfig): ServerRestart =
            ServerRestart(config.propertyOrNull("env.restartOnExit")?.getString()?.trim().equals("true", ignoreCase = true))
    }
}
