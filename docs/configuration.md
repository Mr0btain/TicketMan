# Configuration

Every server setting, where it comes from, and the few things that change in development mode. Client settings are
covered briefly at the end.

## Server settings

Settings live in
[`server/src/main/resources/application.yaml`](../server/src/main/resources/application.yaml). Each value written
as `"$VAR:default"` is read from the environment variable `VAR`, falling back to the default after the colon.

| Setting | Env var | Default | What it does |
|---------|---------|---------|--------------|
| `ktor.deployment.port` | `PORT` | `8080` | Port for plain HTTP, on all interfaces. The server only speaks plain HTTP, see [HTTPS](#https) |
| `env.trustedProxies` | `TRUSTED_PROXIES` | blank | Addresses and CIDR ranges whose `X-Forwarded-For` is believed, comma separated. Blank means loopback and the private ranges, `none` ignores the header |
| `cors.hosts` | `CORS_HOSTS` | blank | Development mode only: hosts allowed to call the server from a browser, like `localhost:8080`. Blank allows any |
| `jwt.secret` | `SECRET` | blank | Signs every access and refresh token. Blank means the server makes one and keeps it |
| `jwt.audience` | - | `my-audience` | Put in tokens and checked on every request |
| `jwt.issuer` | - | `http://localhost` | Put in tokens and checked by the verifier |
| `jwt.realm` | - | `TicketMan Server` | Realm the auth providers report |
| `env.name` | `NAME` | `Server` | Server name shown on the admin screen's Server tab |
| `env.dataPath` | `DATA_PATH` | blank | Folder for the database, photos, backups and restores. Blank means the platform's app data folder |
| `env.adminPassword` | `ADMIN_PASSWORD` | blank | Password for the `admin` user made when no admin exists. Blank means a random one is logged |
| `env.seedTestData` | `SEED_TEST_DATA` | `false` | `true` fills an empty database with made up records on start, see [Seeding on start](#seeding-on-start). Never on a real shop's server |
| `env.restartOnExit` | `RESTART_ON_EXIT` | `false` | `true` when something starts the server again after it exits, so admins can restart it from the Server tab. The Docker image sets it |
| - | `TZ` | the machine's | The time zone "today", due dates and the yearly number rollover use. Set it in a container, which is UTC otherwise |
| `backup.folder` | `BACKUP_DIR` | blank | Folder for a backup made once a day. Blank turns it off |
| `backup.keep` | `BACKUP_KEEP` | `7` | How many daily backups to keep, the oldest are deleted |
| `backup.photos` | `BACKUP_PHOTOS` | `true` | `false` for database only daily backups |
| `housekeeping.auditLogDays` | `AUDIT_LOG_DAYS` | `730` | Audit entries older than this many days are deleted. `0` keeps them all |
| `housekeeping.restoreLeftoverDays` | `RESTORE_KEEP_DAYS` | `30` | Days to keep what a restore replaced (`pre-restore-*`) and restores that failed. `0` keeps them |

Settings with no env var can only be changed by editing the file.

### `SECRET`

Tokens are signed with `SECRET` when it's set. When it isn't, the server makes a random 64 byte secret on first
start, keeps it in `<DATA_PATH>/server/secret` (readable only by the account running the server) and logs
`SECRET isn't set, made one in ...`. Later starts read the same file.

Either way, keep it the same across restarts: changing `SECRET` or deleting the file signs everyone out, since every
existing token stops verifying. A backup doesn't include the file, so a server restored onto a new machine makes a
new secret and devices log in once more.

Servers from before this used a fixed default written in `application.yaml`. The first start after upgrading makes
a secret instead, so devices that relied on that default log in once more.

### HTTPS

The server never does HTTPS itself: a reverse proxy in front of it (Caddy, nginx, Traefik) does, usually with a
public certificate, and talks plain HTTP to it. See [Running a server](operations.md#behind-a-reverse-proxy) for
setting one up, and [Private certificates](operations.md#private-certificates) for a shop with no domain.

`PROXY`, `TRUST_PROXY`, `SSL_PORT`, `HTTP_MODE`, `SSL_KEYSTORE`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_ALIAS` and
`SSL_KEY_PASSWORD` are from when it could, and are ignored. The startup log warns when any of them is set, and notes
a `server/tls/` folder left in the data folder. The log also names the plain HTTP port the proxy should send to and
the trusted ranges (`StartupLog.kt`, from `ProxySettings` in `components/proxy/`).

### `TRUSTED_PROXIES`

Behind a reverse proxy every request reaches the server from the proxy's address, so the wrong login and account
request limits would count every device together. The proxy says who the client was in `X-Forwarded-For`, but anyone
can send that header, so the server only believes it on requests whose connection comes from an address in
`TRUSTED_PROXIES`. It then takes the last entry, the one the nearest proxy added (earlier ones came from the client),
and `X-Forwarded-Proto` for the scheme. From any other address the headers are ignored.

Left blank it's loopback and the private ranges (`127.0.0.1`, `::1`, `10.0.0.0/8`, `172.16.0.0/12`,
`192.168.0.0/16`, `fc00::/7`), which covers a proxy on any Docker network or the same machine with nothing set. The
cost: if the server's own port is also reachable from the shop network, past the proxy, a device there could pick
its own address. Don't publish the server's port when it's behind a proxy, or narrow the list to the proxy's
address, like `TRUSTED_PROXIES=172.20.0.2`. Entries that aren't an address or range are logged and ignored. Names
aren't looked up.

`TRUSTED_PROXIES=none` ignores the header from everyone, for a server devices reach directly over plain HTTP with
no proxy in front, so nobody on the network can claim to be someone else.

### `TZ`

"Today" on the end of day report and the counter, due dates and the yearly ticket and invoice number rollover use
the server's time zone (`ZoneId.systemDefault()`). A container's is UTC unless `TZ` is set, so a shop eight hours
behind would see tomorrow's date every evening. The server logs a warning on start when the zone is UTC and `TZ`
isn't set. Set it to the shop's, like `TZ=America/New_York`.

### `BACKUP_DIR`

A folder for a backup made once a day, the same as one downloaded from the admin screen, named
`ticketman-backup-<millis>[-database].zip`. After each one, all but the newest `BACKUP_KEEP` are deleted; other files
in the folder are left alone. The server makes the folder if it isn't there. Point it off the machine's own disk if
you can (a USB drive or network share), and keep it private: backups hold customer details.

It can't be the server's own `<DATA_PATH>/server/backups/`, which is emptied by the hourly sweep. See
[Running a server](operations.md#daily-backups).

### `RESTART_ON_EXIT`

A restore only takes effect when the server starts again. With `RESTART_ON_EXIT=true` the
Server tab offers **Restart now** next to a waiting restore, which calls `POST /api/admin/restart`: the server answers,
then exits with code 3 and leaves starting it again to whatever runs it. The Docker image sets it, and the compose
file's `restart: unless-stopped` does the starting. Under systemd, `Restart=on-failure` does the same. Leave it off
anywhere nothing would, or the button stops the server for good.

### Housekeeping

`AUDIT_LOG_DAYS` and `RESTORE_KEEP_DAYS` set what the hourly sweep deletes (see
[Running a server](operations.md#the-hourly-sweep)). The audit log keeps two years by default; set `0` to keep it
forever, for example where records have to be kept longer. Restore leftovers are whole copies of the database and
photos, so they're only kept 30 days.

### `DATA_PATH`

Everything the server stores goes under `<DATA_PATH>/server/`. See [Running a server](operations.md#the-data-folder)
for what's inside.

When `DATA_PATH` is blank the server uses `AppDir` from `shared`:

| OS | Folder |
|----|--------|
| Linux | `$XDG_DATA_HOME/TicketMan`, or `$HOME/.local/share/TicketMan` when that isn't set |
| macOS | `$HOME/Library/Application Support/TicketMan` |
| Windows | `%APPDATA%\TicketMan` |

`AppDirTest` pins these down, but only the Linux folder has been tried on a real machine.

The server logs `Path for Files: ...` on start with the folder it's using. If `DATA_PATH` is blank and there's no
app data folder either, it logs an error and starts without a database.

### `ADMIN_PASSWORD`

Read only when the database has no admin. The server then makes a user called `admin` (or `admin_<time>` if a
non-admin already has that name) and logs either:

```
No admin found, created "admin" with the ADMIN_PASSWORD password
No admin found, created "admin" with password: zWjmKVuMQ8OUS6Uv  (change it from the admin screen)
```

Once an admin exists, the setting does nothing. It isn't a way to reset a password.

## Development mode

`./gradlew :server:run -Pdevelopment` sets the JVM property `io.ktor.development=true` (see
[`server/build.gradle.kts`](../server/build.gradle.kts)). That:

- turns on Ktor's own development mode,
- allows `POST /api/admin/seed`, which the admin screen's Server tab uses to fill the database with made up
  customers, tickets, inventory and users (up to 5000 of each). On any other server the route answers `403`,
- turns on CORS, so the web client's dev server (`wasmJsBrowserDevelopmentRun`, on its own port) can call the
  server. `CORS_HOSTS` narrows it to listed hosts. A real server serves the web client itself and never needs it.

For HTTPS in development, put Caddy in front with `scripts/dev_proxy.sh` (see
[Getting started](getting-started.md#behind-a-proxy)).

### Seeding on start

`SEED_TEST_DATA=true` fills the database with made up records on start, in or out of development mode, but only
when it has no tickets yet, so a restart doesn't add more: 150 tickets, 60 customers, 40 inventory items and a few
users, then work logged on about two thirds of the tickets, some deposits, and most of the done ones picked up and
paid, all through `InvoiceService` like the counter. A blank shop profile is filled in (`Bench Repair (test data)`,
6% tax, $80 an hour labor), and staff with known passwords are added: `tech` / `tech_pass` (Normal) and `clerk` /
`clerk_pass` (Restricted). A second location, Downtown, gets a quarter of the tickets and some of the staff.

Then what the shop runs its days with (`SeedOperations.kt`): everyone's weekly shifts from last week to six weeks
ahead, with training and a staff meeting; time off allowances and requests waiting, approved and declined; the time
clock over the shifts so far, with a few late starts and a missed shift; appointments over the past week (checked
in, no shows, cancelled) and the next ten days, with online booking turned on; three suppliers and a purchase order
in each state, one with a part for a ticket; stock moved to Downtown; store credit, gift cards and commission rates.
On a server in development mode the simulated card processor is turned on too, with tips and a reader per location.
Seeding again from the admin screen adds more records but leaves the schedule and suppliers alone once there are some.
The log warns when it happens. `scripts/docker_local.sh` turns it on. Never set it on a
shop's real server.

## Logging

The server logs to standard out through Logback, configured in
[`logback.xml`](../server/src/main/resources/logback.xml) at `info` level. Lines from TicketMan's own code come
through `AppLogger` and start with a tag in brackets, like `[TicketRoute:Get]`, which makes it easy to filter for
one area.

## Client settings

The client has no config file. What it needs is set from the app and saved on the device by `LocalStorage`:

| Setting | Set from |
|---------|----------|
| Server address, device name | Settings > Server |
| Start tab, page size, search result limit, cache size | Settings > Records |
| Trusted server certificates | Saying yes when asked to trust one |
| Remember login | Settings > Login |
| Saved username and password, tokens | Logging in |
| Theme, font, dark mode, reduce transparency | Settings > Appearance |
| Ticket list scope (mine or all) | Records screen |
| Device id and name, station credentials | Made on first start, and when registering a station |

The device id is how the server tells this device's session apart from others, so logging in again on the same
device replaces its old session instead of adding one.

## See also

- [Getting started](getting-started.md)
- [Running a server](operations.md)
