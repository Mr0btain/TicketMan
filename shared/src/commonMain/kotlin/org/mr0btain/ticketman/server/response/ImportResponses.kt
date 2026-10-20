package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/** What happens to one row of an import */
@Serializable
enum class ImportOutcome {
    /** It's new, and is (or would be) added */
    ADD,

    /** It's already there, on the server or earlier in the file, so it's skipped */
    MATCH,

    /** Something's wrong with it, so it's skipped */
    ERROR
}

/**
 * @param row The record's position in the list sent, from 0
 * @param message Which record it matched, or what's wrong
 */
@Serializable
data class ImportRowResult(
    val row: Int,
    val outcome: ImportOutcome,
    val message: String = ""
)

/**
 * What an import did, or with `?dryRun=true` what it would do
 * @param added How many were added, 0 on a dry run
 */
@Serializable
data class ImportResult(
    val rows: List<ImportRowResult>,
    val added: Int,
    val dryRun: Boolean
)
