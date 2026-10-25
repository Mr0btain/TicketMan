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
| [Messages](notify.md) | `/api/notify` | `NotifyRoute.kt` |
| [Estimates](estimate.md) | `/api/estimate` | `EstimateRoute.kt` |
| [Gift cards](giftcard.md) | `/api/giftcard` | `GiftCardRoute.kt` |
| [Purchasing](purchase.md) | `/api/purchase` | `PurchaseRoute.kt` |
| [Reports](report.md) | `/api/report` | `ReportRoute.kt` |
| [Appointments](appointment.md) | `/api/appointment` | `AppointmentRoute.kt` |
| [Time clock](timeclock.md) | `/api/timeclock` | `TimeClockRoute.kt` |
| [Payments](payment.md) | `/api/payment` | `PaymentRoute.kt` |
| [Locations](location.md) | `/api/location` | `LocationRoute.kt` |
| [Status page](status.md) | `/status` | `StatusRoute.kt` |
| [Notes](notes.md) | `.../note/...` on each record type | `NoteRoute.kt` |

The prefixes are set in
[`Routing.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/Routing.kt), and every path,
prefix or endpoint, is a constant in shared's
[`ApiRoutes.kt`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/server/ApiRoutes.kt) (`ApiRoutes.CUSTOMER`,
`RecordPaths.GET`, `TicketPaths.FETCH_MINE`, ...). The server mounts them and the client fills them in with
`fill()`, so renaming one breaks the build on both sides instead of the app.

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

Every provider also needs the token to be an access token (not a refresh token) for a session that still exists
and hasn't expired. A bad, expired or revoked token is a `401` with the text body `Bad Token or Unauthorized`, and
the client refreshes. A good token whose user doesn't have the access the route needs (or hasn't finished setup) is
a `403` with `You don't have access to that`, which refreshing wouldn't fix.

## Locations

Every request from the app carries `X-TicketMan-Location` with the [location](location.md) it works at, or `all`
from an admin looking at every one. Lists of tickets, invoices, stock counts, users, appointments and orders keep to
it, and new work belongs to it; without it the server uses the caller's home location.

## Bodies

- **JSON** both ways, through Ktor's `ContentNegotiation` with the default `Json`. Bodies are the shared
  `@Serializable` classes: models in `shared/.../data/`, requests in `shared/.../server/request/`, responses in
  `shared/.../server/response/`. [DTOs](../shared/dtos-and-platform.md) lists them.
- **Unknown fields are refused.** The default `Json` doesn't ignore unknown keys, so a client sending a field the
  server doesn't have yet gets a `400`. Add fields to the server first, with a default.
- **Reads are `GET`**, with ids in the path and anything else in the query. Paged reads take `?offset=&limit=`
  (default 0 and 50, the limit kept to 200) and searches `?query=&limit=` (limit 20 by default), answering
  `SearchResult { identifier, text }` items. A paging value that isn't a whole number is a `400`.
- **A bare id** in a body is a JSON string: `"0190a2b0-...-000000000000"`, like linking a ticket to a customer.
- **Lists** come back as JSON arrays, never wrapped.
- **Adding a record** (`/add` on customers, tickets, inventory and users) answers `201` with the record as saved,
  so the client gets anything the server filled in, like a ticket's number.
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
| `401 Unauthorized` | Not logged in, or the token is no good (see above). Also a wrong password or PIN |
| `403 Forbidden` | Logged in, but not allowed: the access level is too low. Also an unknown or unapproved station, and seeding outside development mode |
| `404 Not Found` | No such record, or an id in the path that isn't a UUID |
| `409 Conflict` | The change clashes with the current state: a taken username, a picked up ticket, stock that changed |
| `413`, `415`, `423`, `429`, `507` | Too big an upload, a photo type that can't be read, a locked PIN, too many tries, no disk space |
| `500 Internal Server Error` | The write didn't happen, a read failed (a list route answers this rather than an empty list), or the route threw. The body is just `Something went wrong on the server`; the exception is in the server's log |

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
- **A few reads are still `POST`**, because what they take doesn't fit in a URL: the station routes
  (`/api/auth/station/status`, `/station/staff`) take the station's secret, and `/api/counter/repeat-check` takes a
  whole device.
- **The in-process test engine isn't Netty.** Check upload and download changes against a real `:server:run`.

## Versions

`GET /api/ping` answers with two headers: `X-TicketMan-Version`, the server's release (`APP_VERSION`, from
`ticketman.version`, in shared's `server/AppVersion.kt`), and `X-TicketMan-Api`, its `API_VERSION` (in shared's
`ApiRoutes.kt`). The client reads both when it connects (`versionNoticeOf`, giving a `VersionNotice`):

- **A major version apart** (`1.x` and `2.x`; before 1.0 a minor apart too, `0.8` and `0.9`): breaking changes
  between them, so the client doesn't connect (`ConnectionResult.INCOMPATIBLE`). No repository gets the address, so
  nothing loads, and the update screen (`UpdateRequiredScreen`, from `SessionHandler.updateRequired`) takes the whole
  app, saying which side is behind and what to update it to, with **Try again** and **Change server**.
- **A minor version apart** (from 1.0 on): it connects, warns once with a snackbar and keeps a notice on
  Settings > Account saying which side to update.
- **A patch apart**: nothing.
- **The same release but another `API_VERSION`** (two builds between releases), or a server too old to send
  `X-TicketMan-Version` (before 0.9): the old warning, a server without `X-TicketMan-Api` counting as older. It
  never blocks.

The client also sends its own `X-TicketMan-Version` with every request (`sendAppVersion`), and the server turns away
any `/api/` call from a client a major version away (a minor before 1.0) with `426 Upgrade Required` and a line saying
which to update (`plugins/ClientVersion.kt`). `/api/ping` always answers, and a request without the header (clients
from before 0.9, scripts, the status page) goes through. A client turned away mid-session, like after the server was
updated under it, connects again (`RemoteHandler.versionRejected`), which brings up the update screen.

Bump `API_VERSION` when a change means an older client and the new server, or the other way round, can't work
together, like moving a route or changing what one takes. Adding an endpoint or an optional field doesn't need it.

## See also

- [Authentication and sessions](../server/auth.md)
- [Request lifecycle](../architecture/request-lifecycle.md)
- [DTOs, caches and platform helpers](../shared/dtos-and-platform.md)
- [Add an API route](../how-to/add-a-route.md)
