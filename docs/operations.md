# Running a server

What a TicketMan server keeps on disk and the jobs an admin does on it: the first login, adding staff, setting up
counter stations and kiosks, and backing up and restoring. Most of these are done from the admin screen, so this
page says where, and what happens on the server behind it.

## First start

The usual setup is the server in Docker behind a reverse proxy that does HTTPS. The repo's
[`docker-compose.yml`](../docker-compose.yml) runs it behind Caddy, which gets a public certificate by itself.

1. Point a domain (`tickets.myshop.com`) at the machine, with ports 80 and 443 reaching it from the internet so
   Caddy can get the certificate.
2. Next to `docker-compose.yml`, make a `.env` file with `DOMAIN=tickets.myshop.com`, `TZ=` the shop's time zone
   (like `America/New_York`), and `ADMIN_PASSWORD=` if you want to choose the first password.
3. Build the server and start it (see [The Docker image](#the-docker-image) for why the build is on the host):

   ```bash
   ./gradlew :server:installDist -PwithWeb
   docker compose up -d --build
   ```

4. Open `https://tickets.myshop.com` in a browser, or point a client at it (Settings > Server), and log in as
   `admin`. Without `ADMIN_PASSWORD` the password is in `docker compose logs ticketman`.
5. Set up the shop profile on the admin screen's **Server** tab (name, address and contact details, tax rate, invoice footer, repair terms, default
   paper size). Tax and everything printed come from it, so do this before taking money.
6. Change the admin password, and set an admin PIN if the shop will use kiosks (leaving kiosk mode needs one).

Without Docker, start `bin/server` from `:server:installDist` (or `./gradlew :server:run`) with `DATA_PATH` set,
and `TZ` if the machine's clock is on UTC (see [Configuration](configuration.md)). The log says which plain HTTP port
the proxy should send to and which proxy addresses it trusts. Without a proxy at all, set `PROXY=off` and see
[Without a proxy](#without-a-proxy). Either way, without `SECRET` the server makes its own and keeps it in the data
folder.

## The Docker image

[`server/Dockerfile`](../server/Dockerfile) copies in the server built on the host, rather than building it inside
Docker, because the Gradle build needs the Android SDK even for the server (`shared` builds for Android too). The
copied files are plain JVM jars, so the same build makes an amd64 or an arm64 image:

```bash
./gradlew :server:installDist -PwithWeb
docker build -f server/Dockerfile -t ticketman .          # or docker compose build
docker buildx build --platform linux/amd64,linux/arm64 -f server/Dockerfile -t <registry>/ticketman .
```

Build it from the repo root; [`.dockerignore`](../.dockerignore) sends only `server/build/install/server`. Don't
build it with `-Pdevelopment`, which bakes development mode into the start script.

| What | In the image |
|------|--------------|
| Base | `eclipse-temurin:21-jre` |
| Runs | `/app/bin/server` as user `ticketman` (uid 10001) |
| Data | `/data` (`DATA_PATH`), a volume |
| Backups | `/backups`, owned by `ticketman`, for `BACKUP_DIR=/backups` |
| Port | `8080` (`PORT`), plain HTTP for the proxy |
| Set | `PROXY=on`, `RESTART_ON_EXIT=true`, `JAVA_OPTS=-XX:MaxRAMPercentage=75` |
| Health check | `GET /api/ping` every 30 seconds, through bash since the image has no curl |

Named volumes take the folder's owner from the image. A host folder mounted at `/data` or `/backups` has to be owned
by uid 10001 first (`sudo chown 10001:10001 /mnt/usb/ticketman`), or the server can't write to it.

`docker stop` sends SIGTERM to Java, which finishes the requests in flight and then closes H2 cleanly (the log
says `Database closed`). The compose file's `restart: unless-stopped` brings the server back after a crash, a reboot
and the admin screen's restart (see [Restore](#restore)).

In [`docker-compose.yml`](../docker-compose.yml) the server is only on `expose`, so nothing outside reaches it except
through Caddy. The two share a network with a fixed range (`172.30.80.0/24`, `SUBNET` changes it), and
`TRUSTED_PROXIES` is set to exactly that range.

### Behind another proxy

Already running Traefik or Nginx Proxy Manager? Leave the `caddy` service out, put the `ticketman` service on that
proxy's Docker network (`networks: [proxy]`, with the network declared `external: true`), and set `TRUSTED_PROXIES`
to that network's range (`docker network inspect <name>` shows it). Point the proxy at `http://ticketman:8080`.

nginx, or Nginx Proxy Manager's advanced settings, needs a few changes from its defaults:

```nginx
location / {
    proxy_pass http://ticketman:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    client_max_body_size 0;          # photos are up to 25 MB, a restore is the size of the backup
    proxy_request_buffering off;     # send uploads on as they arrive, not after writing them to disk
    proxy_buffering off;             # same for backup downloads
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
}

# The backup download link's ?token= is its password, so keep query strings out of the access log
log_format ticketman '$remote_addr - $remote_user [$time_local] "$request_method $uri $server_protocol" '
                     '$status $body_bytes_sent';
access_log /var/log/nginx/ticketman.log ticketman;
```

`log_format` goes in the `http` block, the rest in the site's `server` block. Traefik streams bodies and has no size
limit by default; check its access log doesn't record query strings either.

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
├── tls/                       The certificate the server made for HTTPS, and its keystore password
│   └── next/                  A new certificate made from the admin screen, swapped in on the next start
├── secret                     What tokens are signed with, when SECRET isn't set. Deleting it logs every device out
├── backups/                   Backups waiting to be downloaded, removed once their link runs out
├── restore/                   An uploaded backup waiting for the next restart, last.json for the last restore,
│                              and failed-<time>.zip for ones that couldn't be applied
└── pre-restore-<time>/        What a restore replaced, kept for 30 days so it can be undone by hand
```

Photo bytes are kept on disk rather than in H2 so the database stays small and quick to back up. Only their
details (owner, size, hash, status) are in the database.

Don't edit or delete files in here while the server is running. H2 holds the database open, and the photo
folders are managed by the sweep below.

## Behind a reverse proxy

The proxy does HTTPS, usually with a public certificate, and devices connect to its name (`tickets.myshop.com`).
Three things to set on the proxy:

- **Body size.** Photos are up to 25 MB, and a restore upload is the size of the backup. nginx refuses anything
  over 1 MB unless `client_max_body_size` is raised (`0` turns the limit off). The app says "Bigger than the proxy
  allows" when this happens. Caddy has no limit.
- **Timeouts** can stay at their defaults. Backups are built in the background and asked after, and uploads and
  downloads keep data moving.
- **`X-Forwarded-For`.** The server only believes it from `TRUSTED_PROXIES`; the admin Server tab shows whether it
  works (see [Configuration](configuration.md#trusted_proxies)).

[Behind another proxy](#behind-another-proxy) has the nginx settings.

Devices have to use a name the certificate is for. Connecting by IP to a proxy whose certificate is for a domain
fails with "This server's certificate is for ...". Android needs 7.1.1 or newer for Let's Encrypt certificates,
which is also the app's minimum.

## Without a proxy

With `PROXY=off` (the direct setup, see [Configuration](configuration.md#proxy)) the server does HTTPS itself. It
listens for HTTPS on `SSL_PORT` (8443) next to plain HTTP on `PORT` (8080). Devices should use the HTTPS one, since
passwords, tokens and backup links can be read off the network over plain HTTP. On first start look for
`Responding at https://0.0.0.0:8443` and the certificate fingerprint in the log, and point clients at something like
`192.168.1.20:8443`, which asks to check that fingerprint.

### The certificate

With no `SSL_KEYSTORE` set, the server makes its own certificate on first start and keeps it in `server/tls/`. It
lasts ten years and lists `localhost`, the machine's name and its IPv4 addresses. No authority vouches for it, so
every device is asked once whether to trust it:

- **Desktop and Android** show the certificate's fingerprint and ask. Compare it with the one in the server's log
  (`Certificate fingerprint ...`) or on the admin screen's **Server** tab, and trust it if they match. After that the
  device only accepts that certificate from that address, and warns if it ever changes.
- **Browsers** show their usual warning page. Open the server's address, check the certificate the browser shows,
  and accept it.

To use a certificate of your own, like one from Let's Encrypt for a real domain or from a local CA such as
`mkcert` that's installed on every device, put it in a PKCS12 file and set `SSL_KEYSTORE` and
`SSL_KEYSTORE_PASSWORD` (see [Configuration](configuration.md#https)). Devices that trust its authority connect
without being asked. A PEM certificate and key can be converted with:

```bash
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out ticketman.p12 -name ticketman
```

A supplied keystore that can't be read stops the server, rather than quietly serving a different certificate.

### Changing the certificate

Every device that trusted the old certificate will warn that it changed, so do it on purpose and tell staff to
expect it.

The Server tab shows when the certificate runs out, and warns once that's less than a year away. To replace one
the server made, use **Make a new certificate** there. The new one waits in `server/tls/next/` and the tab shows its
fingerprint, so staff can be told what to look for; the server switches to it the next time it starts. **Keep the
current certificate** drops it before then. Stopping the server, deleting `server/tls/` and starting it again does
the same in one go.

A supplied certificate is replaced by replacing the `SSL_KEYSTORE` file and restarting.

Backups include a certificate the server made, so restoring one on a new machine brings the old certificate back
and devices carry on without asking. Moving to a new machine without a backup or a copy of `server/tls/` makes a
new certificate.

### Plain HTTP

`HTTP_MODE` sets what the HTTP port does: `redirect` (the default, sends everything to HTTPS), `on` (answers as
usual) or `off`. Development mode defaults to `on`.

Devices saved with an `http://` address move themselves: when they connect, the app sees the redirect, switches to
the `https://` address it points to and saves it, then asks to trust the certificate like any first connection. That
happens when the app starts or the address is saved, since a redirected POST arrives as a GET and fails, so the
app checks before it sends anything else. Scripts and other tools calling the API need an `https://` address, or
`HTTP_MODE=on`.

## Web client

A server built with `-PwithWeb` serves the web client at `/`, on the same address as the API:

```bash
./gradlew :server:run -PwithWeb          # or :server:installDist -PwithWeb, which the Docker image uses
```

Staff then open the proxy's address in a browser (direct: `https://<server>:8443`, accepting the certificate once),
and log in. There's no address
to type, since the page is already on the server. The build gzips the big files (the `.wasm` ones are about 15 MB,
4 MB gzipped), and browsers check for a new version on each load, which costs a 304 when nothing changed.

Without `-PwithWeb` the server has no web client and `/` is a plain up check. The Wasm build takes a few minutes,
which is why it's left out by default.

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
2. hands back a download link that doesn't need a login, so the browser can download it directly. It works for 30
   minutes, or until 5 minutes after it was last used: enough to resume a dropped download, not enough to be of use
   to someone who finds the link in the browser's history later.

Download it straight away. When the link runs out the hourly sweep deletes the file, and any backup file left from
before a restart is deleted too. Downloaded backups aren't kept on the server, so store them somewhere else.

A database only backup is small and fine to take often. Photos can make a full one large.

A backup holds the server's certificate (if it made its own) as well as customer details, so keep it as safe as
the server itself.

### Daily backups

Set `BACKUP_DIR` to a folder (a USB drive, a network share, a folder a sync tool copies off the machine) and the
server makes a backup there once a day, keeping the newest `BACKUP_KEEP` (7 by default). `BACKUP_PHOTOS=false` makes
them database only. See [Configuration](configuration.md#backup_dir). The Server tab shows the folder, when the last
one was made and how big it was, and says so if the last one failed.

The first one is made within an hour of the server starting, and then about a day after the newest one in the
folder. A folder on the same disk as the server doesn't help if that disk dies, so point it somewhere else.

## Restore

Restoring always needs a restart, because H2's files can't be swapped under a running server.

1. On the **Server** tab, upload a backup zip. The server checks it (it's a zip, it has a manifest, it isn't from a
   newer server, it has a database, and it has nothing unexpected in it) and parks it in `restore/`. There must be
   free disk space of at least twice the zip's size.
2. The tab shows the restore as waiting. It can be cancelled until the restart.
3. Restart the server. In Docker (or anywhere `RESTART_ON_EXIT=true`), **Restart now** next to the waiting restore
   does it: the server answers, exits with code 3, and the restart policy starts it again. The app waits for it to
   come back, and devices may have to log in again, since the sessions come from the backup. Otherwise restart it
   by hand. Before opening the database it moves `database/` and `images/` (and `tls/`, if the backup
   has the certificate) into `pre-restore-<time>/`, then unpacks the backup in their place.
4. If unpacking fails, it moves the old folders back and renames the backup to `restore/failed-<time>.zip`, so the
   server starts on its old data.

To undo a restore by hand: stop the server, move `database/` and `images/` out of the way, move the ones from
`pre-restore-<time>/` back, and start it again. The hourly sweep deletes `pre-restore-*` folders and
`restore/failed-*.zip` files after `RESTORE_KEEP_DAYS` (30), so undo within that time or copy the folder elsewhere.

If a backup without photos is restored, `images/` is replaced with an empty folder and photos on records won't load.
The app warns before uploading a file named like a database only backup, the waiting restore says the backup has no
photos, and after the restart the Server tab says it was restored from one.

## The hourly sweep

While the server runs it sweeps once an hour:

- **Photos** marked deleted, or uploaded but never saved onto a note or item, are purged once they've been that way
  for a day. Folders under `images/` with no database row are removed after an hour.
- **Backups** whose download link has run out are deleted.
- **Sessions** whose refresh token has expired, and kiosk PIN sessions left unused past their idle limit, are
  deleted.
- **Audit entries** older than `AUDIT_LOG_DAYS` (two years) are deleted.
- **Restore leftovers** (`pre-restore-*` folders, `restore/failed-*.zip`) older than `RESTORE_KEEP_DAYS` (30 days)
  are deleted.
- **The daily backup** is made, if `BACKUP_DIR` is set and the newest one is a day old.

Photos are deleted a day late on purpose. A backup copies the database first and the photos after, and the delay
means a photo the database still lists can't vanish between the two.

## Seeding test data

On a server started with `-Pdevelopment`, the **Server** tab can add made up customers, tickets, inventory and
users. Don't use it on a shop's real server: the records are mixed in with the real ones and there's no undo
short of restoring a backup.

## Known gaps

- Replacing the certificate still makes every device ask to trust the new one; there's no way to hand devices the
  next certificate ahead of time.
- Daily backup settings are environment variables, so changing them means restarting the server.

## See also

- [Configuration](configuration.md)
- [Getting started](getting-started.md)
