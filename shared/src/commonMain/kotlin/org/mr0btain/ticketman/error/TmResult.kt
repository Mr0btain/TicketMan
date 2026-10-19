package org.mr0btain.ticketman.error


sealed interface TmResult<out D, out E: Error> {
    data class Success<out D>(val data: D): TmResult<D, Nothing>
    data class Error<out E: org.mr0btain.ticketman.error.Error>(val error: E): TmResult<Nothing, E>
}

inline fun <T, E: Error, R> TmResult<T, E>.map(map: (T) -> R): TmResult<R, E> {
    return when(this) {
        is TmResult.Error -> TmResult.Error(error)
        is TmResult.Success -> TmResult.Success(map(data))
    }
}

fun <T, E: Error> TmResult<T, E>.asEmptyDataResult(): EmptyResult<E> {
    return map {  }
}

inline fun <T, E: Error> TmResult<T, E>.onSuccess(action: (T) -> Unit): TmResult<T, E> {
    return when(this) {
        is TmResult.Error -> this
        is TmResult.Success -> {
            action(data)
            this
        }
    }
}
inline fun <T, E: Error> TmResult<T, E>.onError(action: (E) -> Unit): TmResult<T, E> {
    return when(this) {
        is TmResult.Error -> {
            action(error)
            this
        }
        is TmResult.Success -> this
    }
}

typealias EmptyResult<E> = TmResult<Unit, E>
/** The value, or null if it failed */
fun <T, E: Error> TmResult<T, E>.getOrNull(): T? = (this as? TmResult.Success)?.data

/** The value, or [default] if it failed */
fun <T, E: Error> TmResult<T, E>.getOrDefault(default: T): T = (this as? TmResult.Success)?.data ?: default

/** The value, or what [onError] gives instead. It can also leave, like `return@select` */
inline fun <T, E: Error> TmResult<T, E>.valueOr(onError: (E) -> T): T =
    when (this) {
        is TmResult.Success -> data
        is TmResult.Error -> onError(error)
    }
