# Add an API route

A new endpoint, from the database call to the button that uses it. The example sets a ticket's promised time:
`POST /api/data/ticket/set/promised/{identifier}`. Conventions for paths, bodies and status codes are in the
[API reference](../api/README.md).

## 1. Decide where it goes and who can call it

- **Which file**: the route file for the area (`TicketRoute.kt` here), so it lands under that file's prefix. A new
  area gets its own `<Area>Route.kt` with `fun Route.<area>Route(...)`, mounted in `Routing.kt`.
- **Who**: `authenticate("restricted")` for reading, `authenticate { }` (Normal) for changing,
  `authenticate("admin")` for admin only, nothing for the few routes that check something else. Put it inside the
  matching block that's already there.
- **Method and path**: `GET` with path and query parameters for new reads, `POST` for changes, `DELETE` for
  removing. Ids go in the path. Follow the file's existing names (`/set/<thing>/{identifier}`).

## 2. The body

A model or a bare id can be the body as is. Anything else gets a request class in
`shared/.../server/request/`, so the client and server share it:

```kotlin
/** Changes when a ticket is promised back. Null clears it */
@Serializable
data class PromiseRequest(val promisedAt: Long? = null)
```

Give fields defaults where you can. The server refuses fields it doesn't know (a `400`), so a client can't send a
field before the server has it.

## 3. The database call

Add a method to the database class, wrapped in `dbWrite` for a change (returning whether it happened) or `dbQuery`
for a read (returning the value or null):

```kotlin
fun setPromised(identifier: Uuid, promisedAt: Long?): Boolean =
    dbWrite(SET_PROMISED_TAG) {
        val ticket = TicketEntity.findById(identifier)
            ?: return@dbWrite false.also { AppLogger.i(SET_PROMISED_TAG, TicketError.NOT_FOUND) }

        ticket.promisedAt = promisedAt
        true
    }
```

with `private val SET_PROMISED_TAG = "$tag:SetPromised"` beside the other tags. Something that changes several
records together, or has rules of its own, goes in a service under `components/service/` instead (like
`InvoiceService`) and returns an `AccountResult`.

## 4. The route

```kotlin
authenticate {
    ...
    post("/set/promised/{identifier}") {
        val identifier = call.uuidParameter("identifier")
        val request = call.receive<PromiseRequest>()

        if (identifier == null || !database.setPromised(identifier, request.promisedAt)) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }

        call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Promised ${request.promisedAt}")
        call.respond(HttpStatusCode.Accepted)
    }
}
```

- **Check what you can and answer with a reason**: `call.respond(HttpStatusCode.BadRequest, "Pick a time in the
  future")`. The client shows short text bodies to the user.
- **Status codes**: `202` for a change with no body, `201` for something made (with it as the body), `200` for a
  read, `404` when the record isn't there, `409` when it clashes with the current state, `400` for a bad value.
  A route on a service answers with `call.respondAccount(result)`, which maps them for you.
- **Audit every write** with `call.audit(...)`. Reads aren't audited.
- **Don't catch exceptions** to turn them into `500`s: `StatusPages` does it, and logs them. A body that can't be
  read is already a `400`.
- **Who's asking** comes from the token: `call.getIdentifierFromToken()`, `call.username(userDatabase)`. Never take
  "me" from the request.

## 5. The client repository

Add a function to the repository for that prefix (`TicketRemoteRepository`). Changes use `networkRequest` and
`handleActionHttpResponse`, so the caller learns whether it worked and why not:

```kotlin
suspend fun setPromised(identifier: Uuid, promisedAt: Long?): EmptyResult<ServerError> {
    if (serverUrl.isBlank()) return noServerError

    val response = networkRequest(SET_PROMISED_TAG) {
        client.post {
            url("$serverUrl/set/promised/$identifier")
            contentType(ContentType.Application.Json)
            setBody(PromiseRequest(promisedAt))
        }
    }

    return handleActionHttpResponse(SET_PROMISED_TAG, response)
}
```

Reads use `networkErrorTryCatch` and `handleHttpResponse<T>` and return the value or null (see `getByNumber`).
The path is a string on both sides, so the compiler won't tell you if they don't match; the tests will.

## 6. The handler

Call it from the screen's handler, checking access first and reporting back:

```kotlin
fun setPromised(promisedAt: Long?, onResult: (String?) -> Unit) {
    val ticket = _ticket.value?.ticket ?: return
    if (!canEdit()) return

    scope.launch {
        when (val result = remoteHandler.ticketRepo.setPromised(ticket.identifier, promisedAt)) {
            is Result.Success -> { reloadTicket(); onResult(null) }
            is Result.Error -> onResult(result.error.message)
        }
    }
}
```

Hide the button for people who can't use it (`canEdit`) too; the server's check is the real one.

## 7. Tests

- **Server**: in the area's test class, the happy path, each refusal (`404` for a missing ticket, `400` for a bad
  value) and that a user without the access level gets a `401`. See [Testing](../testing.md#server-tests).
- **Client**: a `MockEngine` test that the handler sends the right request, and refuses when `canEdit()` is false.
  See [Testing](../testing.md#client-tests).

## 8. Docs

```bash
python3 scripts/check_api_docs.py
```

lists the new endpoint as not documented. Add it to the area's page in `docs/api/`, as a
`` ### `POST /api/data/ticket/set/promised/{identifier}` `` heading with an `**Auth:** normal` line, what it does,
its body and its answers, then run the script again until it passes.

## Gotchas

- **Fixed segments win.** In `/api/invoice`, `/fetch` and `/{identifier}` live side by side because Ktor prefers
  the constant path. Don't add a constant path that's also a valid value of a parameter beside it.
- **The test engine isn't Netty.** For uploads, downloads and anything big, also try it on a real
  `./gradlew :server:run`, and stream with `receiveChannel().copyToFile` / `respondFile`, not
  `receive<ByteArray>()`.
- **Web needs CORS only in development.** A dev web client on another port can call new routes as long as they
  use the methods and headers `plugins/Cors.kt` allows.

## See also

- [API reference](../api/README.md)
- [Request lifecycle](../architecture/request-lifecycle.md)
- [Networking](../client/networking.md#writing-a-call)
