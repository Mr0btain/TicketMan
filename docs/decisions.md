# Decisions

Why some things are the way they are. Each entry says what was chosen, the reasons the code and these docs give
for it, what it costs, and what would make it worth looking at again. Read this before "fixing" one of them, and
add an entry when you make a choice the next person might want to undo.

## The server

### H2, embedded, one file

**Chosen:** the database is H2 running inside the server process, in `<dataPath>/server/database/`, through
Exposed.

**Why:** a repair shop runs one server on one machine, often with nobody to look after a database service. An
embedded database means one thing to install and start, and backups are H2's online `BACKUP` into a zip while the
server keeps running.

**Costs:** H2 has its quirks: reserved words break column names (`condition` is stored as `device_condition`), it
can't list the `user` table's columns, so they're added by hand, and files can't be swapped under a running
server, so restoring needs a restart. One process means no second server for the same shop.

**Revisit if:** a shop needs more than one server on the same data, or a managed database is available anyway.

### Photos on disk, metadata in the database

**Chosen:** photo bytes live in `ImageStore` folders (`<dataPath>/server/images/`), with only their details in
`ImageTable`. Paths are built from the photo's id, never from anything the client sends.

**Why:** the database stays small and quick to back up, photos can be served straight from files with caching
headers, and a backup can leave them out.

**Costs:** two things to keep in step, which is what the delayed deletion below is for.

### Photo deletion waits a day

**Chosen:** a dropped photo is only marked `DELETED`. The hourly sweep removes files a day later, along with
uploads that were never linked to anything.

**Why:** a backup copies the database first and the photo folders after. Because no file disappears within a day
of being dropped, every photo the copied database points at is still there when the folders are copied, so the
backup is consistent without stopping the server. It also gives a just-uploaded photo time to be saved onto its
note before it counts as abandoned.

**Costs:** deleted photos use disk space for a day. Code must never delete photo files directly: mark the row
and let the sweep do it.

**Revisit if:** backups start pausing writes, or photos move to a store with its own snapshots.

### One session per device

**Chosen:** each login is a row in `TokenTable` keyed by the client's device id. Tokens carry the session id, and
an access token only works while its session row exists.

**Why:** an admin can see every logged in device and log out just one, and logging in again on the same device
replaces its session instead of piling up. Ending a session cuts it off on the device's next request, not when
its token would have run out.

**Costs:** every authenticated request looks the session up. A device that logs in without a device id (old
clients, scripts) adds a new session every time; only the newest 10 of those are kept per user.

### Refresh tokens last a year and are swapped on each use

**Chosen:** a password session lasts 365 days and a PIN session 12 hours. Each refresh hands out a new refresh
token with the same expiry and the old one stops working. Kiosk PIN sessions also end once no request has reached
them for the station's idle timeout plus 10 minutes.

**Why:** a shop's own computers should stay signed in, and the per-device session above means any one can still
be ended from the admin screen. Swapping the token means a copied one stops working as soon as the real device
refreshes, which is at most an hour later. Keeping the first expiry means refreshing never stretches a session.
Tokens are typed (`typ`), so a refresh token can't be used as an access token.

**Costs:** a refresh whose answer never arrives leaves the device holding a dead token, so it logs in again (with
the saved password if it has one). Reusing an old token is only refused, not treated as a theft that ends the
session, because that lost answer would look the same.

**Revisit if:** the server is reachable from outside the shop.

### Its own certificate, pinned by the devices

**Chosen:** without a supplied certificate the server makes one, once, kept in `<dataPath>/server/tls/` for ten
years. Desktop and Android ask staff to check its fingerprint the first time, then trust exactly that
certificate for that address.

**Why:** most shops have no domain name for a box in the back room, so a public certificate isn't an option, and
plain HTTP sends passwords readable on the network. Pinning gives encryption with one check by a person.

**Costs:** replacing the certificate makes every device ask again, and web users get the browser's own warning
instead of the app's. So the certificate is put in backups (a server restored on a new machine keeps it), which
makes a backup something that can impersonate the server. A new one can be made from the admin screen ahead of time
but only takes over on a restart.

### Money in cents, totals from one place

**Chosen:** every amount is a `Long` of cents. Invoice totals are worked out only by `InvoiceMath` in `shared`,
which the server uses to store them and the client uses to preview them.

**Why:** no rounding drift from floating point, and the pickup screen shows exactly what the server will charge.

**Costs:** inventory prices were text once, and moved to cents with a data fix (`migrateLegacyPrices`); any new
money field has to follow the pattern.

### Invoices keep the tax rate they were made with

**Chosen:** an invoice stores the shop's tax rate when it's made (at check-in with a deposit, or the first time
it's opened) and keeps it. Saving its lines later uses the stored rate, not the shop's current one.

**Why:** the customer was quoted with the rate at the time, and an invoice that changes under someone because the
profile was edited is hard to explain at the counter.

**Costs:** if the shop's rate was wrong, or blank, open invoices made before it was fixed keep the wrong rate.
There's no way yet to put the shop's current rate on an existing invoice.

**Revisit if:** shops need a rate per line or per customer (tax exempt customers), which would make the rate a
choice on the invoice anyway.

### Pickup is the only way to close a ticket

**Chosen:** only `POST /api/counter/pickup` sets `PICKED_UP`, and it pays the invoice, takes the parts from
stock and closes the ticket in one transaction. A picked up ticket's status can't change.

**Why:** a closed ticket always has a paid invoice and stock that matches what left the shop. Letting a plain
ticket update close it would skip both.

**Costs:** fixing a mistaken pickup means voiding the invoice (an admin), which puts the parts back but leaves the
ticket closed.

### Locks in the server process

**Chosen:** ticket and invoice numbering, and every invoice change, run under JVM `synchronized` locks, with a
unique index behind the numbers.

**Why:** with one server process, a lock is the simplest way to stop two counters handing out the same number or
both picking up the same ticket.

**Costs:** it only works with one server process, which H2 already requires. An outer transaction that adds a
ticket or invoice has to hold the locks until it commits.

### Audit entries join the change's transaction

**Chosen:** `AuditLogDatabase.log` writes through `dbWrite`, so called inside a `transaction { }` it's part of
that transaction. Check-in, pickup and voiding log as the last step inside theirs.

**Why:** an entry for a change that was rolled back is a lie in the one place people go to find out what happened,
and an entry logged after the commit can be lost if the server stops in between.

**Costs:** inside a transaction, an entry that can't be written undoes the change it describes. Outside one (most
routes), a failed entry is only logged to the console and the change stays.

### In-memory rate limits

**Chosen:** account requests and wrong password logins are counted in memory per address and per username.

**Why:** simple, and nothing to clean up in the database.

**Costs:** a restart clears the counts. Behind a reverse proxy every request has the proxy's address unless
`TRUST_PROXY` is set. A username's limit can be used to keep that user out of password login for a while.

## The client

### Client-made ids

**Chosen:** records get a version 7 UUID when the client makes them, not when the server saves them. Its
timestamp is the record's creation time; there's no separate "created" column.

**Why:** a new record has its final id before it's saved, so notes and photos can be attached to it straight away,
and the same request sent twice can be recognised (check-in refuses a ticket id that already exists).

**Costs:** the server has to check ids are version 7 where the time matters (check-in does), and a client with a
wrong clock makes records with the wrong creation time.

### One Compose Multiplatform client

**Chosen:** desktop, Android and web share one Compose codebase, with `expect`/`actual` for what differs.

**Why:** one UI to build and fix. Desktop is the main target, since a counter PC is where the app mostly runs.

**Costs:** Android hasn't been run end to end, and some things are weaker off desktop (web caps restore uploads
at 512 MB since it reads them whole; Android's full screen can be swiped away).

### iOS parked, not removed

**Chosen:** the iOS targets stay commented out, with the `iosMain` source sets and `iosApp` left in the repository
as they are.

**Why:** shops run the app on counter PCs, Android tablets and browsers, and building iOS needs a Mac. Keeping the
files means picking it up later starts from the Xcode project and the few `actual`s already there.

**Costs:** the `iosMain` files don't compile and fall behind as `expect`s are added (they already lack several).
Turning iOS on means writing those `actual`s first. If it's clear iOS won't happen, delete them rather than keep
fixing around them.

### Handlers on one view model

**Chosen:** `MainViewModel` lives as long as the app and owns a handler per area, connected by callbacks.

**Why:** leaving a screen and coming back keeps its search, selection and open ticket, and handlers can be tested
without the UI, on a `MockEngine`.

**Costs:** everything loads up front, and a handler grows with its screen. The two biggest (`CounterHandler`,
`AdminHandler`) are split into parts, one per area of the screen, reached through the handler (`counter.pickup`,
`admin.accounts`). New areas should do the same rather than grow one class.

### A UI kit enforced by a test

**Chosen:** all styling lives in `ui/kit`, and `UiKitRulesTest` fails if a screen hard codes a color, size or
shape or uses a Material component.

**Why:** themes, light mode and fonts only work if every screen takes its look from the kit, and a test catches
the screen that doesn't before a person has to.

**Costs:** something new means a kit component and a gallery entry first. The test is a text search, so it can
be fooled.

### Glass only for floating layers

**Chosen:** blurred glass is for the nav, bars, dialogs and menus; cards and lists are solid. Reduce
transparency turns it all solid.

**Why:** blur behind text makes it harder to read and costs drawing time on every frame.

## The API

### Route paths shared, reads as `GET`

**Chosen:** every route path is a constant in shared's `ApiRoutes.kt`, used by the server's route files and the
client's repositories. Reads are `GET` with path and query parameters; only reads that take a secret or a whole
object (station status, repeat check) are `POST`.

**Why:** a path typed on both sides could drift apart and only fail when someone used that screen. A shared constant
breaks the build instead. `GET` reads can be retried and cached, and show what they read in the server's log.

**Costs:** `scripts/check_api_docs.py` has to read the constants to work out the routes, so they stay plain string
constants. Moving the reads to `GET` was a breaking change, the reason `API_VERSION` is 2.

### Client and server updated together, with a version check

**Chosen:** one API version, `API_VERSION`, sent in the `X-TicketMan-Api` header of `/api/ping`. The client warns
when the server's isn't its own, but carries on. There are no versioned routes. The server refuses fields it doesn't
know; the client ignores fields it doesn't know.

**Why:** a shop runs one server and its own few devices, normally updated together. The warning catches the device
that was missed, without keeping old routes around.

**Costs:** a new field has to reach the server before any client sends it, and a mismatched device gets errors on
whatever changed until it's updated.

**Revisit if:** shops can't update every device at once, which would need the server to keep older routes working.

## See also

- [Architecture overview](architecture/overview.md)
- [Running a server](operations.md)
- [Authentication and sessions](server/auth.md)
