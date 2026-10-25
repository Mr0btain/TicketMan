# Admin API

`/api/admin`, from [`AdminRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/AdminRoute.kt).
What the [admin screen](../ui/screens/admin.md) runs on: overview numbers, sessions, stations, server info,
backup and restore, test data and the audit log. Everything is admin only except the backup download link.

## Numbers

### `GET /api/admin/stats`

**Auth:** admin

The overview's numbers: ticket counts by status and priority, customers, users by access, low stock, active
sessions, the oldest open ticket and recent tickets.

- **Query:** `lowStock` (threshold, default 5), `recent` (how many recent tickets, default 10, at most 200).
- **`200`** `AdminStatsResponse`. **`500`** if it couldn't be built.

### `GET /api/admin/report/daily`

**Auth:** admin

One day's takings by payment method, sales, tax, invoices paid, tickets checked in and picked up, and what's
overdue or waiting for pickup now. For printing at close.

- **Query:** `date`, like `2026-10-17`. Leave it off for today, in the server's time zone.
- **`200`** `DailyReport`. **`400`** `Date has to look like 2026-10-17`.

### `GET /api/admin/inventory/low-stock`

**Auth:** admin

Tracked items at or below their own `reorderPoint`, or `threshold` for items without one. `lowStockCount` in
`/stats` counts the same way with its `lowStock` query.

- **Query:** `threshold` (default 5), `limit` (default and most 200).
- **`200`** a list of `Inventory`.

## Sessions

### `GET /api/admin/sessions`

**Auth:** admin

Every logged in device.

- **`200`** a list of `SessionResponse`: user, device name, `PASSWORD` or `PIN`, created, last used, expiry.

### `DELETE /api/admin/sessions/{sessionId}`

**Auth:** admin

Logs out one device. Its access tokens stop working with it.

- **`202`** done. **`404`** no such session.

### `DELETE /api/admin/sessions/user/{identifier}`

**Auth:** admin

Logs a user out of every device.

- **`202`** done. **`404`** no such user.

### `GET /api/admin/logins`

**Auth:** admin

What the [wrong password limits](../server/auth.md#wrong-password-limits) are holding back right now.

- **`200`** `LoginLimitsResponse`: `locks` (`LoginLock`: `kind` `USERNAME` or `ADDRESS`, `key` the lowercase
  username or the address, `failures` counted against it, `lockedUntil` epoch millis), newest lock first;
  `recentFailures` (wrong passwords from everywhere in the window), `surge` (over the server wide ceiling) and
  `windowMinutes` (15).

### `POST /api/admin/logins/unlock`

**Auth:** admin

Lets a locked username or address try logging in again straight away. Audited.

- **Body:** `LoginUnlockRequest { kind, key }`.
- **`202`** its count is cleared. **`404`** it had nothing counted.

## Stations

### `GET /api/admin/stations`

**Auth:** admin

- **`200`** a list of every `Station`, pending and approved. Secrets aren't included.

### `POST /api/admin/stations/{identifier}/approve`

**Auth:** admin

Lets staff PIN in on this station.

- **`202`** approved. **`404`** no such station.

### `POST /api/admin/stations/{identifier}/kiosk`

**Auth:** admin

Turns kiosk mode on or off, with the idle lock time. The device picks it up next time it checks its station.

- **Body:** `KioskSettings { enabled, idleTimeoutSeconds }`, the timeout 30 to 3600.
- **`202`** saved. **`400`** the timeout is out of range. **`404`** no such station. **`409`** not approved yet.

### `DELETE /api/admin/stations/{identifier}`

**Auth:** admin

Removes a station and signs out whoever is PINned in there.

- **`202`** removed. **`404`** no such station.

## Server

### `GET /api/admin/server`

**Auth:** admin

- **`200`** `ServerInfoResponse`: name, version, uptime, start time, data path, database and photo sizes, Java and
  OS, development mode, `scheduledBackup` (`ScheduledBackupInfo`: folder, how many are kept, whether photos are in them, the newest
  one's time and size, and when the last try failed; null without `BACKUP_DIR`) and `lastRestore` (`RestoreRecord`:
  the restored backup's manifest, when it was applied and the `pre-restore-<time>` folder; null once that folder has
  been swept, or if there's been no restore).
- It also says how this request reached the server, so an admin can check the reverse proxy is set up right:
  `trustedProxies` (`TRUSTED_PROXIES` as given, empty with `none`),
  `clientAddress` (the address the server sees for the device asking), `forwarded` (whether that came from a
  trusted proxy's `X-Forwarded-For`) and `timeZone` (what "today" and the yearly number rollover use). Behind a
  working proxy `clientAddress` is the device's own address and `forwarded` is true; the proxy's address with
  `forwarded` false means the proxy isn't in `trustedProxies` or doesn't send the header. All four have defaults.
  The `certificate` and `proxy` fields from when the server could do HTTPS itself are gone.
- `canRestart`: whether [`POST /api/admin/restart`](#post-apiadminrestart) works (`RESTART_ON_EXIT=true`). Defaults
  to false.

### `POST /api/admin/restart`

**Auth:** admin

Restarts the server, which applies a waiting restore. It answers, then exits with code 3 about a
second later and leaves starting it again to whatever runs it (Docker's restart policy). Audited, once per restart.

- **`202`** it's on its way, also when it already was.
- **`409`** `RESTART_ON_EXIT` isn't on, so nothing would start it again (the text says so).

## Backup and restore

How backups are built and restores applied is in [Backup and restore](../server/backup-restore.md).

### `POST /api/admin/backup`

**Auth:** admin

Builds a backup zip on the server and hands back a link to it, answering once it's built. Can take a while with
photos, long enough for a reverse proxy to give up, so clients use `POST /api/admin/backup/start`. Kept for older
clients.

- **Body:** `BackupRequest { includeImages }`.
- **`201`** `BackupTicket { id, fileName, sizeBytes, sha256, downloadPath, expiresAt, includesImages }`.
- **`500`** the backup couldn't be made.

### `POST /api/admin/backup/start`

**Auth:** admin

Starts building a backup in the background and answers straight away. A reverse proxy gives up on a request the
server is quiet on for long (nginx after 60 seconds), and a backup with photos can take longer than that, so the
client uses this and asks after it with `GET /api/admin/backup/status/{id}`.

- **Body:** `BackupRequest { includeImages }`.
- **`202`** `BackupJob { id, state, startedAt, ticket, error }`, with `state` `RUNNING`. Logged to the audit log once
  it's built.

### `GET /api/admin/backup/status/{id}`

**Auth:** admin

How a backup started with `POST /api/admin/backup/start` is going. Ask every couple of seconds until it isn't
`RUNNING`.

- **`200`** `BackupJob`: `RUNNING`; `DONE` with the `ticket` to download it with; or `FAILED` with an `error`.
- **`404`** no such backup, or it finished more than an hour ago. Forgotten on restart.

### `GET /api/admin/backup/download/{id}`

**Auth:** none

Downloads a backup. The `token` in the link is the login, so a browser can open it directly.

- **Query:** `token`, from the ticket's `downloadPath`.
- **`200`** the zip, as an attachment with `Cache-Control: no-store`. Range requests work, so a cut off download
  can carry on (`206`).
- **`404`** unknown id, wrong token, or expired: 30 minutes after the backup was made if the link hasn't been
  used, otherwise 5 minutes after it was last used.

### `POST /api/admin/restore`

**Auth:** admin

Uploads a backup to restore on the next start. The body is the raw zip (`application/zip`), streamed to disk.

- **`202`** `PendingRestore { manifest, sizeBytes, stagedAt }`. Restart the server to apply it. A manifest with
  `includesImages` false means photos on records won't load afterwards.
- **`400`** cut off, or not a backup this server can restore (the text says why).
- **`507`** not enough disk space: it needs room for the zip and for it unpacked.

### `GET /api/admin/restore`

**Auth:** admin

- **`200`** `PendingRestore` waiting for a restart. **`204`** nothing waiting.

### `DELETE /api/admin/restore`

**Auth:** admin

Cancels a waiting restore.

- **`202`** whether or not there was one.

## Import

Customers or inventory from another system. The client reads the CSV and sends records; the server decides what's
already here (`ImportService`), so a dry run is the preview and can't disagree with the import. New identifiers are
made on the server. Audited as a `CREATE` on `customers` or `inventory` with the counts.

### `POST /api/admin/import/customers`

**Auth:** admin

- **Body:** a list of `Customer`, at most 20,000.
- **Query:** `dryRun=true` to only say what would happen.
- **`200`** `ImportResult { rows, added, dryRun }`, a row for each record sent (`ImportRowResult { row, outcome,
  message }`). `ADD` is new; `MATCH` is skipped because a customer here, or earlier in the list, has the same phone
  number (compared by its digits, 7 or more) or email (ignoring case); `ERROR` has no name or an email without `@`.
  The `ADD` rows are added in one transaction.
- **`400`** more than 20,000.

### `POST /api/admin/import/inventory`

**Auth:** admin

The same for a list of `Inventory`: `MATCH` is an item with the same name, ignoring case and spaces at the ends;
`ERROR` has no name, a negative price or cost, or negative stock. Stock comes in as sent (`Int.MAX_VALUE` is not
counted).

## Test data

### `POST /api/admin/seed`

**Auth:** admin

Adds made up records. Only on servers started with `-Pdevelopment`.

- **Body:** `SeedRequest { tickets, customers, inventory, users }`, each 0 to 5000 (defaults 100, 40, 20, 5).
- **`202`** done. **`400`** a count out of range. **`403`** not a development server.

## Audit log

### `GET /api/admin/audit/fetch`

**Auth:** admin

A page of the audit log, newest first.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `AuditEntry`.

## See also

- [Admin screen](../ui/screens/admin.md)
- [Backup and restore](../server/backup-restore.md)
- [PINs and stations](../server/pins-and-stations.md)
- [Audit log](../server/audit-log.md)
