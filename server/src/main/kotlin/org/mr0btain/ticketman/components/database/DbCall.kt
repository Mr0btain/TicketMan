package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult

/**
 * Runs [block] in a transaction, logging how it went under [tag]. A throw rolls the whole transaction back.
 * Called inside someone else's transaction (like check in saving several things together) a throw isn't caught,
 * so it reaches that transaction and undoes all of it
 * @return What [block] returned, or null if it threw
 */
fun <T> dbQuery(tag: String, block: JdbcTransaction.() -> T): T? {
    TransactionManager.currentOrNull()?.let { outer -> return outer.block() }

    return try {
        transaction { block() }.also { result ->
            if (result != null) AppLogger.m(tag, SimpleResult.SUCCESS) else AppLogger.i(tag, SimpleResult.FAIL)
        }
    } catch (ex: Exception) {
        AppLogger.e(tag, SimpleResult.FAIL, ex)
        null
    }
}

/**
 * Runs a write in a transaction, see [dbQuery] for how throws are handled. [block] returns false when what it
 * changes isn't there
 * @return Whether the write happened, false when it threw too
 */
fun dbWrite(tag: String, block: JdbcTransaction.() -> Boolean): Boolean {
    TransactionManager.currentOrNull()?.let { outer -> return outer.block() }

    return try {
        transaction { block() }.also { done ->
            if (done) AppLogger.m(tag, SimpleResult.SUCCESS) else AppLogger.i(tag, SimpleResult.FAIL)
        }
    } catch (ex: Exception) {
        AppLogger.e(tag, SimpleResult.FAIL, ex)
        false
    }
}
