# Backup and restore

How the server builds a backup archive, hands it to the browser, and swaps a restored one in on the next start.
What an admin does is on [Running a server](../operations.md#backup); this page is how it works inside and what to
keep in mind when changing it.

## The archive

```
ticketman-backup-<millis>[-database].zip
├── manifest.json     BackupManifest: format version, server version, time, photo count and size
├── database.zip      H2's own online backup (itself a zip of the database files)
└── images/           Only when photos are included: the same layout as server/images/
    └── ab/<id>/{original,display,thumb}.jpg
```

`BackupManifest.CURRENT_FORMAT_VERSION` is `1`. Bump it whenever the layout changes in a way an older server
couldn't restore; servers refuse archives with a newer version than theirs.

## Building

`POST /api/admin/backup` (`BackupRequest(includeImages)`), admin only, calls
[`BackupService.prepare`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/service/BackupService.kt):

1. Only one backup is built at a time (a `Mutex`), on the IO dispatcher.
2. **The database first.** `LocalDatabase.backupTo()` runs H2's `BACKUP TO`, which copies a consistent snapshot
   while the server keeps running.
3. **Then the photos**, if asked for: every variant of every photo that isn't `DELETED`.
4. Everything is written into one zip on disk under `server/backups/`. The manifest is compressed; the database copy
   and JPEGs are stored as is, since they're already compressed. A SHA-256 of the archive is worked out as it's
   written.
5. The response is a `BackupTicket`: id, file name, size, hash, expiry and a `downloadPath` of
   `/api/admin/backup/download/{id}?token=<random>`.

Why the order matters: photos are only purged a day after they're deleted (see [Photos](photos.md)). So every photo
the copied database points at is still on disk when the photo files are copied a moment later, even if someone
deleted one in between.

## Downloading

`GET /api/admin/backup/download/{id}?token=...` has **no login**. The token in the link is the credential, so the
client can hand the URL to the browser or a file saver and let it download directly, without the app holding the
file in memory.

- The token is 32 random bytes, compared in constant time.
- The link works for 30 minutes and can be used more than once, so a dropped download can be resumed with a Range
  request (the `PartialContent` plugin in `plugins/Transfers.kt`).
- The file is sent with `respondFile`, `Content-Disposition: attachment` and `Cache-Control: no-store`.

Links are kept in memory. After a restart they're gone, and the sweep deletes the files.

## Cleaning up

`BackupService.sweep()` runs with the hourly photo sweep. It deletes archives whose link has run out and any file in
`backups/` it doesn't know about (left from before a restart, or a crash mid build). It skips a run while a backup is
being built.

## Restoring

Swapping H2's files under a running server isn't safe, so a restore is split in two by a restart.

### 1. Staging, while running

`POST /api/admin/restore` takes the raw zip as the request body (not multipart).

1. Refuses up front with `507` if the declared size times two (the upload plus the unpacked copy) won't fit on disk.
2. Streams the body to a temp file in `server/restore/` with `copyToFile`, capped at half the free space. A cut off
   upload is `400`.
3. [`RestoreService.stage`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/service/RestoreService.kt)
   checks it: it's a zip, it has a manifest that parses, the format version isn't newer, the database entry is there,
   and every entry is one it expects. Any failure is `400` with the reason.
4. Saves it as `restore/pending.zip` with a `pending.json` (`PendingRestore`), replacing any restore already waiting.

`GET /api/admin/restore` shows what's waiting (`204` if nothing), and `DELETE /api/admin/restore` cancels it.

### 2. Applying, on the next start

`configureDatabase()` calls `RestoreService.applyPending(serverDir)` before H2 opens anything:

1. Moves `database/` and `images/` into a new `pre-restore-<millis>/` folder.
2. Unpacks the archive: `database.zip` is unzipped into `database/`, and `images/...` into `images/`. Every path is
   checked to land inside its folder, so an archive can't write anywhere else (zip slip).
3. Deletes the pending files.

If anything fails, it deletes the half unpacked folders, moves the old ones back, and renames the archive to
`restore/failed-<millis>.zip`. The server then starts on its old data.

The `pre-restore-*` folder is kept so a restore can be undone by hand.

## Gotchas

- **Stream big bodies, never buffer them.** Use `receiveChannel().copyToFile(...)` for uploads and `respondFile` for
  downloads, not `receive<ByteArray>()` or `respondBytes`. A backup with photos can be gigabytes.
- **Test against a real server.** The in-process test engine handles big and cut off bodies differently from Netty.
  `RestoreTest` covers the logic by starting the server twice on one data folder, with an H2 `SHUTDOWN` between.
- **Restoring a database only backup leaves `images/` empty**, so photos on restored records won't load.
- **The download link is a bearer credential** for 30 minutes. It ends up in browser history and possibly proxy logs.

## See also

- [Running a server](../operations.md#backup)
- [Photos](photos.md)
