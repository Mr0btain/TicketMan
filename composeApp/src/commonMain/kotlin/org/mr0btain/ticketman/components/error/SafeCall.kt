package org.mr0btain.ticketman.components.error

/**
 * Function for easily running something with run catching
 */

// TODO: Custom error handling to this?
// TODO: Implement snack bar popup errors for things

inline fun <R> safeCall(call: () -> R): Result<R> {
    return runCatching { call() }
}

sealed class Resource<T> {
    data class Success<T>(val data: T): Resource<T>()
    data class Error<T>(val error: ErrorType): Resource<T>()
}