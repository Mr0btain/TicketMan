# Results and errors

TicketMan code returns failures instead of throwing them. A call that can fail gives back a `Result` holding either
the value or an error with a readable message, and the caller decides what to show. This page covers the types in
`shared/.../error/` and the helpers that turn exceptions into results.

## `Result`

[`Result.kt`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/error/Result.kt):

```kotlin
sealed interface Result<out D, out E: Error> {
    data class Success<out D>(val data: D): Result<D, Nothing>
    data class Error<out E: org.mr0btain.ticketman.error.Error>(val error: E): Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>
```

Helpers: `map`, `onSuccess`, `onError` (both return the result, so they chain) and `asEmptyDataResult()`.

Use it with a `when`, or the helpers:

```kotlin
when (val created = repo.create(ticket)) {
    is Result.Error -> snackBarHost.showSnackbar("Ticket not saved: ${created.error.message}")
    is Result.Success -> seed.setTicket(created.data, notes, customer, user)
}

handleHttpResponse<Ticket>(TAG, response).onSuccess { return it }
```

`EmptyResult<E>` is for calls where only success or failure matters, like an update.

## `Error`

```kotlin
/** Error half of a [Result]. [message] is readable enough to show the user */
interface Error : LogMessage
```

Anything implementing `Error` can go in a `Result`, and its `message` should make sense in a snackbar. Because
`Error` extends `LogMessage`, every error can also be passed straight to `AppLogger`.

Most errors are enums with the message as a constructor argument:

| Enum | Where it's used |
|------|-----------------|
| `NetworkError` (in `shared/.../server/`) | Client: what went wrong with a request. Mapped from the status code, or from what the call threw, by `networkErrorFor()` |
| `TicketError`, `CustomerError`, `InventoryError`, `UserError`, `NoteError` | Server: logged by the database classes when a record isn't found |
| `ImageError` | Server: why an upload was refused. The route sends the message back to the client |

The client also has `ServerError` (in `composeApp/.../network/ServerError.kt`), which wraps a `NetworkError` with
the short plain text reason the server sent, if any. Its `message` prefers the server's reason. Repository calls
that change something return `Result<T, ServerError>` or `EmptyResult<ServerError>`.

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
| `networkRequest(tag) { ... }` | Client, `composeApp/.../network/Helpers.kt` | `Result<T, NetworkError>`: the value, or what kind of failure it was (timeout, no connection, unreadable response, unknown). Rethrows cancellation |
| `networkErrorTryCatch(tag) { ... }` | Client, same file | The value, or `null` if it threw. For reads that only need to know whether it worked |
| `dbQuery(tag) { ... }`, `dbWrite(tag) { ... }` | Server, `server/.../database/DbCall.kt` | The value or `null`; whether the write happened. See [Request lifecycle](../architecture/request-lifecycle.md) |
| `appLogTryCatch(tag) { ... }` | Shared, `error/AppLogTryCatch.kt` | Nothing, or `T?`. The older pattern, see below |

`appLogTryCatch` comes in two forms. One takes a block returning a `Boolean` and only logs. The other takes a block
returning `Pair<T, Boolean>`, logs success or failure from the `Boolean`, and returns the `T`, or `null` if it
threw. Some older server databases (audit log, tokens, stations, invites, shop, stats, counter, images) still wrap
`transaction { }` in it. New server code uses `dbQuery`/`dbWrite`, which do the same job with less ceremony.

## Logging

`AppLogger` is an `expect object` in `shared/.../logging/` with an `actual` per platform. Each method takes a tag
and a message (a `String` or any `LogMessage`):

| Method | For |
|--------|-----|
| `e(tag, message, throwable?)` | Something failed with an exception |
| `i(tag, message)` | Something didn't work but nothing threw: not found, refused |
| `m(tag, message)` | Things that worked, and general messages |
| `d(tag, message)` | Debugging |

`SimpleResult.SUCCESS` and `SimpleResult.FAIL` are the usual messages for `m` and `i`.

Tags are hierarchical strings declared as constants at the top of a class, so log lines can be filtered by area:

```kotlin
private val TAG = "TicketRemoteRepository"
private val CREATE_TAG = "$TAG:Create"
```

On the JVM (desktop and server), `e`, `i` and `d` go through `java.util.logging` and `m` goes to standard out.

## Gotchas

- **Two `Result`s.** Kotlin's own `kotlin.Result` is also in scope everywhere. `RestoreService` uses it
  on purpose (`Result.failure`, `runCatching`). Import `org.mr0btain.ticketman.error.Result` explicitly
  when you mean this one, and check which one a file is using before calling `onSuccess` on it.
- **`Result.Error` shadows `Error`** inside the sealed interface, which is why its bound is written out in full.
- **`AppLogger.LOG_LEVEL` is only read once.** On the JVM the level is applied when the object is first used, so
  setting `LOG_LEVEL` later does nothing.
- **`d` isn't hidden in release builds.** On the JVM it logs at info level like `i`.

## See also

- [Request lifecycle](../architecture/request-lifecycle.md): how a failure travels from the server to a snackbar
- [Domain models](domain-models.md)
