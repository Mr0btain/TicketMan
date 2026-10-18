# Running a server

What a TicketMan server keeps on disk and the jobs an admin does on it: the first login, adding staff, setting up
counter stations and kiosks, and backing up and restoring. Most of these are done from the admin screen, so this
page says where, and what happens on the server behind it.

## First start

1. Start the server with `SECRET` and `DATA_PATH` set, and `ADMIN_PASSWORD` if you want to choose the first password
   (see [Configuration](configuration.md)).
2. Watch the log for `Responding at http://0.0.0.0:<port>`, and for the `admin` user's password if you didn't set one.
3. Point a client at the server (Settings > Server and data) and log in as `admin`.
4. Set up the shop profile on the admin screen's **Server** tab (name, address and contact details, tax rate, invoice footer, repair terms, default
   paper size). Tax and everything printed come from it, so do this before taking money.
5. Change the admin password, and set an admin PIN if the shop will use kiosks (leaving kiosk mode needs one).

## The data folder

Everything lives under `<DATA_PATH>/server/`:

```
server/
├── database/
│   └── database.mv.db         H2 database: every record, user, session, invoice and the audit log
├── images/
│   ├── .tmp/                  Uploads being processed
│   └── ab/<photo id>/         First 2 characters of the id, then one folder per photo
│       ├── original.jpg
│       ├── display.jpg
│       └── thumb.jpg
├── backups/                   Backups waiting to be downloaded, removed once their link runs out
├── restore/                   An uploaded backup waiting for the next restart
└── pre-restore-<time>/        What a restore replaced, kept so it can be undone by hand
```

Photo bytes are kept on disk rather than in H2 so the database stays small and quick to back up. Only their
details (owner, size, hash, status) are in the database.

Don't edit or delete files in here while the server is running. H2 holds the database open, and the photo
folders are managed by the sweep below.

## Staff accounts

Accounts are made in one of two ways, both handled on the admin screen's **Users** tab:

- **Account requests.** Someone uses "Request an account" on the login screen. They can't log in until an admin
  approves the request, choosing their access level.
- **Invites.** An admin makes the account and gets a setup code to hand over. The code works as a one off password.

Either way, the first login is held to account setup: the new user has to fill in their details, choose a
password and set a PIN before any other route lets them in.

| Access level | Can |
|--------------|-----|
| Restricted | Look at records and the counter, but not change anything |
| Normal | Everything except the admin screen |
| Admin | Everything |

An account's status (pending, active, deactivated) is separate from its level. Deactivating someone keeps their
history and names in the audit log, and ends their sessions.

## Sessions

Each device a user logs in on gets its own session, keyed by the device id the client makes on first start.
Logging in again on the same device replaces that device's session and leaves the others alone.

The **Sessions** tab lists every session and can end one, or all of one user's. Ending a session takes effect on
that device's next request, not when its token would have run out, because every request checks that its session
still exists.

Access tokens last an hour and are refreshed in the background. Refresh tokens last a year. PIN sessions last 12
hours.

## Counter stations and kiosks

A **station** is a device the shop has approved for PIN login, like the PC at the front counter. A **kiosk** is a
station locked to the counter screen, where staff sign in by PIN and it locks again when left alone.

1. **Register.** On the device, someone with Normal access goes to Settings > Account and station > Counter
   station and gives it a name. The device stores the station's id and secret.
2. **Approve.** An admin approves it on the **Stations** tab. Until then PIN logins on it are refused.
3. **Kiosk mode (optional).** On the same tab, turn on kiosk mode and choose an idle timeout (30 seconds to an
   hour). The device picks this up the next time it connects to the server, or straight away from Refresh in its
   Counter station dialog. It then switches to the kiosk: full screen, counter only, with a PIN lock screen.

On a station:

- Staff sign in with their 6 digit PIN. Signing in ends the previous person's PIN session on that station, so only
  one person is signed in there at a time.
- Five wrong PINs in a row lock that user's PIN until an admin resets it.
- A kiosk never falls back to the device owner's saved password, so a locked kiosk can't quietly log someone back in.
- Leaving kiosk mode on the device needs an admin's PIN.
- Removing a station on the **Stations** tab signs out whoever is PINned in there.

## Backup

On the admin screen's **Server** tab, choose whether to include photos and make a backup. The server:

1. writes a zip to `backups/` with a `manifest.json`, `database.zip` (H2's online `BACKUP`, so the server keeps
   running) and, if asked for, an `images/` folder,
2. hands back a download link that works for 30 minutes and doesn't need a login, so the browser can download it
   directly. It supports resuming.

Download it straight away. When the link runs out the hourly sweep deletes the file, and any backup file left from
before a restart is deleted too. The server doesn't keep a history of backups, so store them somewhere else.

A database only backup is small and fine to take often. Photos can make a full one large.

## Restore

Restoring always needs a restart, because H2's files can't be swapped under a running server.

1. On the **Server** tab, upload a backup zip. The server checks it (it's a zip, it has a manifest, it isn't from a
   newer server, it has a database, and it has nothing unexpected in it) and parks it in `restore/`. There must be
   free disk space of at least twice the zip's size.
2. The tab shows the restore as waiting. It can be cancelled until the restart.
3. Restart the server. Before opening the database it moves `database/` and `images/` into `pre-restore-<time>/`,
   then unpacks the backup in their place.
4. If unpacking fails, it moves the old folders back and renames the backup to `restore/failed-<time>.zip`, so the
   server starts on its old data.

To undo a restore by hand: stop the server, move `database/` and `images/` out of the way, move the ones from
`pre-restore-<time>/` back, and start it again. Delete old `pre-restore-*` folders once you're happy, since nothing
cleans them up.

If a backup without photos is restored, `images/` is replaced with an empty folder and photos on records won't load.

## The hourly sweep

While the server runs it sweeps once an hour:

- **Photos** marked deleted, or uploaded but never saved onto a note or item, are purged once they've been that way
  for a day. Folders under `images/` with no database row are removed after an hour.
- **Backups** whose download link has run out are deleted.

Photos are deleted a day late on purpose. A backup copies the database first and the photos after, and the delay
means a photo the database still lists can't vanish between the two.

## Seeding test data

On a server started with `-Pdevelopment`, the **Server** tab can add made up customers, tickets, inventory and
users. Don't use it on a shop's real server: the records are mixed in with the real ones and there's no undo
short of restoring a backup.

## Known gaps

- No HTTPS. The server speaks plain HTTP, so put it behind a TLS proxy if it's reachable outside the shop network.
- No scheduled backups. They're made by hand from the admin screen.
- `pre-restore-*` folders and `restore/failed-*.zip` files pile up until removed by hand.

## See also

- [Configuration](configuration.md)
- [Getting started](getting-started.md)
