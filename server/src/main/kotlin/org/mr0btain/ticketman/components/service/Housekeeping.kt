package org.mr0btain.ticketman.components.service

import kotlinx.coroutines.CancellationException
import org.mr0btain.ticketman.logging.AppLogger
import io.ktor.server.config.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * How long the hourly sweep keeps things, from application.yaml's `housekeeping` section. 0 days keeps them forever
 * @param auditLogDays Audit entries older than this are deleted
 * @param restoreLeftoverDays What a restore set aside, and restores that failed, are deleted after this
 */
data class Housekeeping(
    val auditLogDays: Int = DEFAULT_AUDIT_LOG_DAYS,
    val restoreLeftoverDays: Int = RestoreService.LEFTOVER_AGE.inWholeDays.toInt()
) {
    /** Epoch millis audit entries before which are deleted, null to keep them all */
    fun auditCutoff(now: Long = Clock.System.now().toEpochMilliseconds()): Long? =
        if (auditLogDays > 0) now - auditLogDays.days.inWholeMilliseconds else null

    val restoreLeftoverAge: Duration
        get() = if (restoreLeftoverDays > 0) restoreLeftoverDays.days else Duration.INFINITE

    companion object {
        const val DEFAULT_AUDIT_LOG_DAYS = 730

        fun from(config: ApplicationConfig): Housekeeping {
            fun days(key: String, default: Int) =
                (config.propertyOrNull("housekeeping.$key")?.getString()?.trim()?.toIntOrNull() ?: default).coerceAtLeast(0)

            val defaults = Housekeeping()
            return Housekeeping(
                auditLogDays = days("auditLogDays", defaults.auditLogDays),
                restoreLeftoverDays = days("restoreLeftoverDays", defaults.restoreLeftoverDays)
            )
        }
    }
}

/**
 * Runs one step of a background loop, so whatever goes wrong in it is logged and the loop carries on to the next step
 * and its next round, rather than backups and session clean up quietly stopping until a restart. Being stopped still
 * stops it
 */
suspend fun sweepStep(name: String, step: suspend () -> Unit) {
    try {
        step()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        AppLogger.e("SERVER:Sweep", "Couldn't do $name, trying again next time", ex)
    }
}
