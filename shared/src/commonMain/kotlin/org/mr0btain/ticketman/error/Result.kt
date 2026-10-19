package org.mr0btain.ticketman.error


sealed interface Result<out D, out E: Error> {
    data class Success<out D>(val data: D): Result<D, Nothing>
    data class Error<out E: org.mr0btain.ticketman.error.Error>(val error: E): Result<Nothing, E>
}

inline fun <T, E: Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> {
    return when(this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }
}

fun <T, E: Error> Result<T, E>.asEmptyDataResult(): EmptyResult<E> {
    return map {  }
}

inline fun <T, E: Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    return when(this) {
        is Result.Error -> this
        is Result.Success -> {
            action(data)
            this
        }
    }
}
inline fun <T, E: Error> Result<T, E>.onError(action: (E) -> Unit): Result<T, E> {
    return when(this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

typealias EmptyResult<E> = Result<Unit, E>
/** The value, or null if it failed */
fun <T, E: Error> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.data

/** The value, or [default] if it failed */
fun <T, E: Error> Result<T, E>.getOrDefault(default: T): T = (this as? Result.Success)?.data ?: default

/** The value, or what [onError] gives instead. It can also leave, like `return@select` */
inline fun <T, E: Error> Result<T, E>.valueOr(onError: (E) -> T): T =
    when (this) {
        is Result.Success -> data
        is Result.Error -> onError(error)
    }
