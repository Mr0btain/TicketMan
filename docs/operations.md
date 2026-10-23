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

A shop with no domain, only reached on its own network, sets `DOMAIN` to the machine's name or LAN address
(`DOMAIN=192.168.1.20`) and `CERTIFICATE=private`. Caddy then uses its own local CA, and devices are asked once to
trust it (see [Private certificates](#private-certificates)). `HTTPS_PORT` and `HTTP_PORT` publish Caddy on other
ports than 443 and 80; plain HTTP is still sent on to the right HTTPS port.

Without Docker, start `bin/server` from `:server:installDist`, the release's server jar with `java -jar` (Java 21),
or `./gradlew :server:run`, with `DATA_PATH` set,
and `TZ` if the machine's clock is on UTC (see [Configuration](configuration.md)), and put a reverse proxy in front.
The server only speaks plain HTTP; the log says which port the proxy should send to and which proxy addresses it
trusts. Either way, without `SECRET` the server makes its own and keeps it in the data folder.

## The Docker image

[`server/Dockerfile`](../server/Dockerfile) copies in the server built on the host, rather than building it inside
Docker (see [decisions](decisions.md#the-docker-image-is-built-from-the-hosts-build)). The build doesn't need the
Android SDK. The copied files are plain JVM jars, so the same build makes an amd64 or an arm64 image:

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
| Set | `RESTART_ON_EXIT=true`, `JAVA_OPTS=-XX:MaxRAMPercentage=75` |
| Health check | `GET /api/ping` every 30 seconds, through bash since the image has no curl |

Named volumes take the folder's owner from the image. A host folder mounted at `/data` or `/backups` has to be owned
by uid 10001 first (`sudo chown 10001:10001 /mnt/usb/ticketman`), or the server can't write to it. It stops on the
first start with H2's `Log file error: "/data/server/database/database.trace.db"`, caused by
`AccessDeniedException: /data/server`.

On a NAS (Unraid, Synology, TrueNAS) the share's own permissions can win over `chown`. If the error stays, use a
named volume, or run the container as the user that owns the share with `user: "<uid>:<gid>"` on the service
(often `99:100` on Unraid). Nothing in the image depends on uid 10001.

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
├── secret                     What tokens are signed with, when SECRET isn't set. Deleting it logs every device out
├── backups/                   Backups waiting to be downloaded, removed once their link runs out
├── restore/                   An uploaded backup waiting for the next restart, last.json for the last restore,
│                              and failed-<time>.zip for ones that couldn't be applied
└── pre-restore-<time>/        What a restore replaced, kept for 30 days so it can be undone by hand
```

A `tls/` folder left from when the server did HTTPS itself isn't used any more; the log says so on start, and it can
be deleted.

Photo bytes are kept on disk rather than in H2 so the database stays small and quick to back up. Only their
details (owner, size, hash, status) are in the database.

Don't edit or delete files in here while the server is running. H2 holds the database open, and the photo
folders are managed by the sweep below.

## Behind a reverse proxy

The server never does HTTPS itself. A reverse proxy in front does it, usually with a public certificate, and devices
connect to its name (`tickets.myshop.com`). Three things to set on the proxy:

- **Body size.** Photos are up to 25 MB, and a restore upload is the size of the backup. nginx refuses anything
  over 1 MB unless `client_max_body_size` is raised (`0` turns the limit off). The app says "Bigger than the proxy
  allows" when this happens. Caddy has no limit.
- **Timeouts** can stay at their defaults. Backups are built in the background and asked after, and uploads and
  downloads keep data moving.
- **`X-Forwarded-For`.** The server only believes it from `TRUSTED_PROXIES`; the admin Server tab shows whether it
  works (see [Configuration](configuration.md#trusted_proxies)). Wrong password limits count each device by it.

[Behind another proxy](#behind-another-proxy) has the nginx settings.

Devices have to use a name the certificate is for. Connecting by IP to a proxy whose certificate is for a domain
fails with "This server's certificate is for ...". Android needs 7.1.1 or newer for Let's Encrypt certificates,
which is also the app's minimum.

### Private certificates

A proxy with a certificate no public authority vouches for, like Caddy's local CA with `CERTIFICATE=private`, for a
`DOMAIN` that's `localhost`, a `.local` name or a LAN address:

- **Desktop and Android** show a "Private certificate" dialog with a warning that it isn't a public certificate,
  and the fingerprint to check. Trust it only if it matches the one the admin handed out. After that the device only
  accepts certificates from that same authority at that address, and warns loudly if it ever changes.
- **Browsers** show their usual warning page. Accept it, or install the CA's certificate on the machine to skip it.

Caddy replaces its local certificate every 12 hours and its intermediate every week, so the apps trust the CA's root
instead, which lasts ten years. [`docker/Caddyfile`](../docker/Caddyfile) hands the root out at
`/.well-known/ticketman/ca.pem`; when a device is asked to trust the server, it fetches that file, checks it signed
the server's certificate, and offers it. The fingerprint to give staff is the root's:

```bash
docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt | openssl x509 -noout -fingerprint -sha256
```

A proxy that doesn't hand its CA out gets the top certificate it sends trusted instead, which works until that one
changes. Resetting Caddy's data volume makes a new CA, so every device asks again; a backup of the `caddy-data`
volume avoids that when moving machines.

Leave `CERTIFICATE=private` set for these. Java, which the desktop and Android apps run on, sends no SNI (the name
it's connecting to) for an address or a name without a dot, and Caddy only answers a handshake without one when the
site's certificate issuer is given explicitly; with `public` it refuses them, and the apps say they can't reach the
server, while browsers, which always send it, work. [`docker/Caddyfile`](../docker/Caddyfile) sets `default_sni`
to `DOMAIN` and imports `tls internal` for `private`.

`scripts/docker_local.sh` runs all of this on one machine to try it (see [Testing](testing.md#docker)).

### Plain HTTP

Devices can still connect over `http://`, straight to the server's port without a proxy, for development or a
quick try on a shop's own network. Passwords, tokens and customer details then go over the network readable, so the
app says "Not secure" on the login popup, on Settings > Account and once when it connects. A server on the same
machine (`localhost`) is left alone.

An address typed with no scheme means `https://`. Caddy sends plain HTTP on to HTTPS; a device saved with an
`http://` address sees that when it connects, switches to the `https://` address and saves it.

## Web client

A server built with `-PwithWeb` serves the web client at `/`, on the same address as the API:

```bash
./gradlew :server:run -PwithWeb          # or :server:installDist -PwithWeb, which the Docker image uses
```

Staff then open the proxy's address in a browser and log in. There's no address to type, since the page is already
on the server. The build gzips the big files (the `.wasm` ones are about 15 MB,
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

The same tab lists **Blocked logins**: usernames and devices refused password logins for now after too many wrong
ones (see [Auth](server/auth.md#wrong-password-limits)), with a button to let one try again straight away. Each lock
is in the audit log as a failed login. They're kept in memory, so restarting the server clears them too.

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

A backup holds customer details and every user's password hash, so keep it as safe as the server itself. It doesn't
hold the proxy's certificate; back up Caddy's `caddy-data` volume for that.

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
   by hand. Before opening the database it moves `database/` and `images/` into `pre-restore-<time>/`, then
   unpacks the backup in their place. A certificate in an older backup, from when the server made its own, is
   skipped.
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

## Importing from another system

The admin screen's **Server** tab can add customers or inventory from a CSV, like a spreadsheet saved as CSV or an
export from the system the shop used before. It needs a header row; columns are matched to fields by their names
("Mobile" is a phone, "Postcode" a zip, "Retail price" a price, ...) and can be changed in the dialog. Customers can
come with one Name column or First and Last name. A preview shows each row as **Add**, **Already here** (a customer
with the same phone or email, an item with the same name) or **Problem**, and only the new ones are added, all
together. Running the same file again adds nothing.

Notes, tickets and photos aren't imported, and existing records aren't updated from the file.

## Seeding test data

On a server started with `-Pdevelopment`, the **Server** tab can add made up customers, tickets, inventory and
users. Don't use it on a shop's real server: the records are mixed in with the real ones and there's no undo
short of restoring a backup.

## Known gaps

- A new private CA (Caddy's data reset, or a new machine without it) makes every device ask to trust the server
  again; there's no way to hand devices the next one ahead of time.
- Wrong password limits are kept in memory, so a restart clears them.
- Daily backup settings are environment variables, so changing them means restarting the server.

## See also

- [Configuration](configuration.md)
- [Getting started](getting-started.md)
