# API reference

Every HTTP endpoint the server has, one page per route file. This page covers what they all share; the pages below
list each endpoint with who can call it, what it takes and what it answers. The client calls all of these through
the repositories in `composeApp/.../components/network/` (see [Networking](../client/networking.md)), so most
changes here are made to both sides together.

| Page | Prefix | Route file |
|------|--------|------------|
| [Auth](auth.md) | `/api/auth` | `AuthRoute.kt` |
| [Users](user.md) | `/api/user` | `UserRoute.kt` |
| [Admin](admin.md) | `/api/admin` | `AdminRoute.kt` |
| [Counter](counter.md) | `/api/counter` | `CounterRoute.kt` |
| [Invoices](invoice.md) | `/api/invoice` | `InvoiceRoute.kt` |
| [Stations](station.md) | `/api/station` | `StationRoute.kt` |
| [Shop profile](shop.md) | `/api/shop` | `ShopRoute.kt` |
| [Photos](image.md) | `/api/image` | `ImageRoute.kt` |
| [Customers](customer.md) | `/api/data/customer` | `CustomerRoute.kt` |
| [Tickets](ticket.md) | `/api/data/ticket` | `TicketRoute.kt` |
| [Inventory](inventory.md) | `/api/data/inventory` | `InventoryRoute.kt` |
| [Notes](notes.md) | `.../note/...` on each record type | `NoteRoute.kt` |

The prefixes are set in
[`Routing.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/Routing.kt).

## Auth

Send the access token from a login as `Authorization: Bearer <token>`. Each endpoint's **Auth** line says which
provider guards it (registered in `plugins/Security.kt`, see [Authentication and sessions](../server/auth.md)):

| Auth | Who gets in |
|------|-------------|
| `none` | Anyone. Some of these check something else instead, like a station's secret or a one off link token |
| `setup` | Any active user, including one who hasn't finished first login setup |
| `restricted` | Active, set up users with Restricted access or higher. Used for reading |
| `normal` | Normal access or higher. Used for changing things. This is `authenticate { }` with no name |
| `admin` | Admins only |

Every provider also needs the token to be an access token (not a refresh token) for a session that still exists.
Failing any check is a `401` with the text body `Bad Token or Unauthorized`; there is no `403` for "logged in but
not allowed". See [Request lifecycle](../architecture/request-lifecycle.md#gotchas) for how the client copes.

## Bodies

- **JSON** both ways, through Ktor's `ContentNegotiation` with the default `Json`. Bodies are the shared
  `@Serializable` classes: models in `shared/.../data/`, requests in `shared/.../server/request/`, responses in
  `shared/.../server/response/`. [DTOs](../shared/dtos-and-platform.md) lists them.
- **Unknown fields are refused.** The default `Json` doesn't ignore unknown keys, so a client sending a field the
  server doesn't have yet gets a `400`. Add fields to the server first, with a default.
- **A bare id** is a JSON string: `"0190a2b0-...-000000000000"`. Several `/get` routes take one as the whole body.
- **Lists** come back as JSON arrays, never wrapped. Paging is `PagedRequest { offset, itemsPerPage }`, searching
  is `SearchQuery { query, limit }` answering `SearchResult { identifier, text }` items.
- **Money** is always `Long` cents. **Times** are epoch milliseconds. **Ids** are UUIDs, version 7 for records.
- **Errors** that come with a reason have it as a plain text body meant for people, like
  `Username is already taken`. The client's `handleActionHttpResponse` keeps a short text body (under 200
  characters, not JSON) as the error's reason and shows it; anything else falls back to the status code's message.
- Photo uploads are multipart and restore uploads are a raw zip; see [Photos](image.md) and [Admin](admin.md).

## Status codes

The routes mostly use these:

| Code | Meaning here |
|------|--------------|
| `200 OK` | Here's what you asked for |
| `201 Created` | Made something; the body is usually the new thing |
| `202 Accepted` | Done. Most writes answer this, usually with no body. It doesn't mean "queued" |
| `204 No Content` | Nothing to report (only `GET /api/admin/restore`) |
| `400 Bad Request` | A value the route checked was wrong, with the reason as the text body. Or the body couldn't be read at all: not JSON, an unknown or missing field, an id that isn't a UUID. That one always says `The server couldn't read that request` |
| `401 Unauthorized` | Not logged in, or not allowed (see above). Also a wrong password |
| `404 Not Found` | No such record, or an id in the path that isn't a UUID. Some list routes also use it for a database error |
| `409 Conflict` | The change clashes with the current state: a taken username, a picked up ticket, stock that changed |
| `413`, `415`, `423`, `429`, `507` | Too big an upload, a photo type that can't be read, a locked PIN, too many tries, no disk space |
| `500 Internal Server Error` | The write didn't happen, or the route threw. The body is just `Something went wrong on the server`; the exception is in the server's log |

Routes that go through a service answer with `respondAccount` (in `UserRoute.kt`): `Done` → the route's success
code, `Invalid` → `400`, `Conflict` → `409`, `NotFound` → `404`, each with the service's message.

## Server endpoints

### `GET /api/ping`

**Auth:** none

The clients' "is this a TicketMan server" check. Answers `200` with the text `TicketMan` (`SERVER_PING_BODY`).

### `GET /`

**Auth:** none

With the web client built in (`-PwithWeb`), serves it (`webRoute()`, with gzip copies and ETags). Otherwise answers
an empty `200`, which older clients used as their up check.

## Checking these pages

[`scripts/check_api_docs.py`](../../scripts/check_api_docs.py) reads the route files, works out every endpoint and
its auth provider, and compares them with the headings and **Auth** lines on these pages:

```bash
python3 scripts/check_api_docs.py          # exit 1 and list what's missing, extra or has the wrong auth
python3 scripts/check_api_docs.py --list   # every endpoint the routes define
```

Run it after adding, moving or re-guarding a route. It follows `route()`, `authenticate()` and the shared
`noteRoute()`, plus routes added in `plugins/`. Each endpoint here is a heading of the form
`` ### `METHOD /path` `` with an `**Auth:** <provider>` line under it, which is what the script reads.

## Gotchas

- **Errors that escape a route go through `StatusPages`** in `Routing.kt`. Ktor's `BadRequestException` (what
  `call.receive` throws for a body it can't read) becomes a `400` and is logged at info with the serializer's reason,
  like `Field 'password' is required`. Anything else becomes a `500` and is logged as an error with its stack trace.
  Neither sends the exception to the caller, so look in the server log to see why.
- **Most reads are `POST`.** `/get`, `/fetch`, `/search` and `/get/notes/{id}` take a body, so they're `POST` even
  though they change nothing. Newer routes use `GET` with path and query parameters.
- **Add answers differ by record type**: customers `202` with no body, inventory `201` with no body, tickets `201`
  with the saved ticket (so the client learns its number).
- **The in-process test engine isn't Netty.** Check upload and download changes against a real `:server:run`.

## Known gaps

- There's no versioning. Client and server are expected to be updated together.

## See also

- [Authentication and sessions](../server/auth.md)
- [Request lifecycle](../architecture/request-lifecycle.md)
- [DTOs, caches and platform helpers](../shared/dtos-and-platform.md)
- [Add an API route](../how-to/add-a-route.md)
