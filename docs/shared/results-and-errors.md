# Results and errors

TicketMan code returns failures instead of throwing them. A call that can fail gives back a `TmResult` holding either
the value or an error with a readable message, and the caller decides what to show. This page covers the types in
`shared/.../error/` and the helpers that turn exceptions into results.

## `TmResult`

[`TmResult.kt`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/error/TmResult.kt):

```kotlin
sealed interface TmResult<out D, out E: Error> {
    data class Success<out D>(val data: D): TmResult<D, Nothing>
    data class Error<out E: org.mr0btain.ticketman.error.Error>(val error: E): TmResult<Nothing, E>
}

typealias EmptyResult<E> = TmResult<Unit, E>
```

Helpers: `map`, `onSuccess`, `onError` (both return the result, so they chain) and `asEmptyDataResult()`.

Use it with a `when`, or the helpers:

```kotlin
when (val created = repo.create(ticket)) {
    is TmResult.Error -> snackBarHost.showSnackbar("Ticket not saved: ${created.error.message}")
    is TmResult.Success -> seed.setTicket(created.data, notes, customer, user)
}

handleHttpResponse<Ticket>(TAG, response).onSuccess { return it }
```

`EmptyResult<E>` is for calls where only success or failure matters, like an update.

## `Error`

```kotlin
/** Error half of a [TmResult]. [message] is readable enough to show the user */
interface Error : LogMessage
```

Anything implementing `Error` can go in a `TmResult`, and its `message` should make sense in a snackbar. Because
`Error` extends `LogMessage`, every error can also be passed straight to `AppLogger`.

Most errors are enums with the message as a constructor argument:

| Enum | Where it's used |
|------|-----------------|
| `NetworkError` (in `shared/.../server/`) | Client: what went wrong with a request. Mapped from the status code, or from what the call threw, by `networkErrorFor()` |
| `TicketError`, `CustomerError`, `InventoryError`, `UserError`, `NoteError` | Server: logged by the database classes when a record isn't found |
| `ImageError` | Server: why an upload was refused. The route sends the message back to the client |

The client also has `ServerError` (in `composeApp/.../network/ServerError.kt`), which wraps a `NetworkError` with
the short plain text reason the server sent, if any. Its `message` prefers the server's reason. Repository calls
that change something return `TmResult<T, ServerError>` or `EmptyResult<ServerError>`.

To add an error, add an enum (or a value to an existing one) implementing `Error`:

```kotlin
enum class InvoiceError(override val message: String): Error {
    ALREADY_PAID("That invoice is already paid")
}
```

## Turning exceptions into results

Three helpers catch exceptions at the edge, log them, and return something the caller can check:

| Helper | Where | Returns |
|--------|-------|---------|
| `networkRequest(tag) { ... }` | Client, `composeApp/.../network/Helpers.kt` | `TmResult<T, NetworkError>`: the value, or what kind of failure it was (timeout, no connection, unreadable response, unknown). Rethrows cancellation |
| `networkErrorTryCatch(tag) { ... }` | Client, same file | The value, or `null` if it threw. For reads that only need to know whether it worked |
| `dbQuery(tag) { ... }`, `dbWrite(tag) { ... }` | Server, `server/.../database/DbCall.kt` | The value or `null`; whether the write happened. See [Request lifecycle](../architecture/request-lifecycle.md) |
| `appLogTryCatch(tag) { ... }` | Shared, `error/AppLogTryCatch.kt` | Nothing, or `T?`. See below |

`appLogTryCatch` comes in two forms. One takes a block returning a `Boolean` and only logs. The other takes a block
returning `Pair<T, Boolean>`, logs success or failure from the `Boolean`, and returns the `T`, or `null` if it
threw. The server's databases used to wrap `transaction { }` in it; they all use `dbQuery`/`dbWrite` now, which also
join an outer transaction. Keep `appLogTryCatch` for shared code.

## Logging

`AppLogger` is an `expect object` in `shared/.../logging/` with an `actual` per platform. Each method takes a tag
and a message (a `String` or any `LogMessage`):

| Method | For |
|--------|-----|
| `e(tag, message, throwable?)` | Something failed with an exception |
| `i(tag, message)` | Something didn't work but nothing threw: not found, refused |
| `m(tag, message)` | Things that worked, and general messages |
| `d(tag, message)` | Debugging. Only written when `AppLogger.debug` is on |

`SimpleResult.SUCCESS` and `SimpleResult.FAIL` are the usual messages for `m` and `i`.

`AppLogger.debug` starts off. The clients turn it on from `isDebug()` in `initKoin()` (after Koin starts, since
Android reads its debug flag from the context Koin holds), and the server in development mode, at the top of
`Application.module()`. Everything else is always written.

Tags are hierarchical strings declared as constants at the top of a class, so log lines can be filtered by area:

```kotlin
private val TAG = "TicketRemoteRepository"
private val CREATE_TAG = "$TAG:Create"
```

On the JVM (desktop and server), `e`, `i` and `d` go through `java.util.logging` and `m` goes to standard out.

## Gotchas

- **`TmResult` isn't `kotlin.Result`.** Kotlin's own `Result` is always in scope, which is why the app's is named
  `TmResult`: a bare `Result` in this codebase is Kotlin's. `RestoreService` uses Kotlin's on purpose
  (`Result.failure`, `runCatching`), for exceptions rather than error enums. Use `TmResult` for anything a caller
  shows or acts on.
- **`TmResult.Error` shadows `Error`** inside the sealed interface, which is why its bound is written out in full.
- **`d` logs at info level on the JVM**, like `i`, with a `DEBUG:` prefix, so it shows without changing
  `java.util.logging`'s level. Anything logged before `debug` is set (Koin's own start up, Netty's own start up
  on the server) skips `d`.

## See also

- [Request lifecycle](../architecture/request-lifecycle.md): how a failure travels from the server to a snackbar
- [Domain models](domain-models.md)
