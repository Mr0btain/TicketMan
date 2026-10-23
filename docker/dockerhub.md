# TicketMan

A ticket tracking server for repair shops, or any shop that runs its work through tickets. Staff check devices in at
the counter, track the repair, take payment at pickup, and keep customer, inventory and photo records along the way.

This image is the TicketMan server: a Ktor app on Java 21 with its own H2 database, so there's nothing else to set
up. Connect to it with the desktop app (Windows, macOS, Linux), the Android app, or a browser.

> TicketMan is young and still changes often between versions. Take a backup before upgrading.

![The counter screen showing a ticket, its invoice and the queue](https://raw.githubusercontent.com/Mr0btain/TicketMan/master/docs/images/counter.png)

## Images

| Image | What it is |
|-------|------------|
| `mr0btain/ticketman` | The server with the web client built in, served at `/`. Most shops want this one |
| `mr0btain/ticketman-server` | The server alone, for shops that only use the desktop and Android apps. `/` is a plain up check |

Both come for `linux/amd64` and `linux/arm64` (a Raspberry Pi 4 or 5 works).

| Tag | Points at |
|-----|-----------|
| `latest` | The newest release |
| `<version>`, like `0.8.0` | That release, the safe choice for a shop's server |

Desktop and Android apps, and the release notes, are on the
[releases page](https://github.com/Mr0btain/TicketMan/releases).

## Quick try

```bash
docker run -d --name ticketman \
  -p 8080:8080 \
  -e TZ=America/New_York \
  -e ADMIN_PASSWORD=change_me \
  -v ticketman-data:/data \
  mr0btain/ticketman:latest
```

Open `http://localhost:8080` and log in as `admin` with that password. Without `ADMIN_PASSWORD` a random one is
printed in `docker logs ticketman`.

This is plain HTTP, fine on the same machine. From anywhere else passwords and customer details would cross the
network readable (the apps say "Not secure"), so a real shop puts HTTPS in front, as below.

## Running it for a shop

The server only speaks plain HTTP. HTTPS is a reverse proxy's job, and Caddy does it with almost no setup. Make a
folder with these three files.

**`.env`**

```bash
DOMAIN=tickets.myshop.com      # or the machine's LAN address / name, like 192.168.1.20
CERTIFICATE=public             # private for a LAN address, a .local name or localhost
TZ=America/New_York
ADMIN_PASSWORD=                # optional, the first admin password
```

**`docker-compose.yml`**

```yaml
name: ticketman

services:
  ticketman:
    image: mr0btain/ticketman:0.8.0
    restart: unless-stopped
    environment:
      TZ: ${TZ:-America/New_York}
      TRUSTED_PROXIES: 172.30.80.0/24
      BACKUP_DIR: /backups
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:-}
    volumes:
      - ticketman-data:/data
      - ticketman-backups:/backups
    expose:
      - "8080"            # only Caddy reaches it
    networks:
      - ticketman

  caddy:
    image: caddy:2.10
    restart: unless-stopped
    environment:
      DOMAIN: ${DOMAIN:-localhost}
      CERTIFICATE: ${CERTIFICATE:-public}
      HTTPS_PORT: ${HTTPS_PORT:-443}
    ports:
      - "${HTTP_PORT:-80}:80"
      - "${HTTPS_PORT:-443}:443"
      - "${HTTPS_PORT:-443}:443/udp"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    networks:
      - ticketman

networks:
  ticketman:
    ipam:
      config:
        - subnet: 172.30.80.0/24

volumes:
  ticketman-data:
  ticketman-backups:
  caddy-data:
  caddy-config:
```

**`Caddyfile`**

```
{
	default_sni {$DOMAIN:localhost}
}

(certificate-public) {
}

(certificate-private) {
	tls internal
}

{$DOMAIN:localhost} {
	import certificate-{$CERTIFICATE:public}

	# Caddy's local CA, which the apps fetch when asked to trust a private certificate
	handle /.well-known/ticketman/ca.pem {
		root * /data/caddy/pki/authorities/local
		rewrite * /root.crt
		file_server
	}

	handle {
		reverse_proxy ticketman:8080
	}
}

http://{$DOMAIN:localhost} {
	redir https://{host}:{$HTTPS_PORT:443}{uri} 308
}
```

Then:

```bash
docker compose up -d
docker compose logs ticketman     # the admin password, if ADMIN_PASSWORD was blank
```

Open `https://<DOMAIN>` or point an app at it (Settings > Server), log in as `admin`, and on the admin screen's
**Server** tab fill in the shop profile (name, contact details, tax rate, invoice footer) before taking money. Then
change the admin password.

**With a real domain** (`CERTIFICATE=public`), point it at the machine and let ports 80 and 443 reach it from the
internet; Caddy gets a Let's Encrypt certificate on its own.

**On the shop's own network only** (`CERTIFICATE=private`), Caddy makes its own certificate authority. The desktop
and Android apps ask once to trust it and show a fingerprint; browsers show their usual warning. Check the
fingerprint against:

```bash
docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt | openssl x509 -noout -fingerprint -sha256
```

Keep the `caddy-data` volume: losing it makes a new authority, and every device asks again.

### Behind a proxy you already run

Leave the `caddy` service out, put `ticketman` on your proxy's Docker network, point the proxy at
`http://ticketman:8080`, and set `TRUSTED_PROXIES` to that network's range (`docker network inspect <name>`).
For nginx or Nginx Proxy Manager, raise the body size limit (photos are up to 25 MB, a restore is the size of the
backup):

```nginx
client_max_body_size 0;
proxy_request_buffering off;
proxy_buffering off;
proxy_set_header Host $host;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

Keep query strings out of the proxy's access log: a backup download link's `?token=` is its password.

## Environment variables

| Variable | Default | What it does |
|----------|---------|--------------|
| `TZ` | UTC | The shop's time zone, like `America/New_York`. "Today", due dates and the yearly ticket number rollover follow it, so set it |
| `ADMIN_PASSWORD` | random, logged | Password for the `admin` user made on the first start. Only read while there's no admin, so it can't reset a password |
| `TRUSTED_PROXIES` | loopback and private ranges | Addresses or CIDR ranges allowed to say who a request is from (`X-Forwarded-For`), comma separated. Set it to your proxy's network. `none` ignores the header, for a server reached directly |
| `BACKUP_DIR` | off | Folder for a backup made once a day, like `/backups` |
| `BACKUP_KEEP` | `7` | How many daily backups to keep |
| `BACKUP_PHOTOS` | `true` | `false` for database only daily backups |
| `SECRET` | made and kept in `/data` | Signs login tokens. Changing it logs every device out |
| `NAME` | `Server` | The server's name on the admin screen |
| `AUDIT_LOG_DAYS` | `730` | Days of audit log to keep. `0` keeps it all |
| `RESTORE_KEEP_DAYS` | `30` | Days to keep what a restore replaced, so it can be undone by hand. `0` keeps it |
| `SEED_TEST_DATA` | `false` | `true` fills an empty database with made up tickets, customers and staff (`tech`/`tech_pass`, `clerk`/`clerk_pass`) to try it out. Never on a real shop's server |
| `PORT` | `8080` | Plain HTTP port inside the container |
| `DATA_PATH` | `/data` | Where everything is stored. Leave it, and mount a volume there |
| `RESTART_ON_EXIT` | `true` | Lets an admin restart the server from the app (needed to finish a restore). Leave it on with a restart policy |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | Options for the JVM |

## Volumes and ports

| Path / port | What it is |
|-------------|------------|
| `/data` | The database, photos, the token secret and pending restores. **Back this up** (or use `BACKUP_DIR`) |
| `/backups` | An empty folder owned by the server's user, for `BACKUP_DIR=/backups` |
| `8080` | Plain HTTP. Give it to the proxy only; don't publish it when a proxy is in front |

The server runs as user `ticketman`, uid `10001`. Named volumes just work. A host folder has to be owned by that uid
first:

```bash
sudo chown -R 10001:10001 /mnt/usb/ticketman
```

Otherwise it stops on the first start with `AccessDeniedException: /data/server`. On a NAS (Unraid, Synology,
TrueNAS) whose share permissions win over `chown`, run the container as the share's owner instead, like
`user: "99:100"` on Unraid.

## Backups, restore and upgrades

- **Backups**: from the admin screen's **Server** tab (download straight away, the link lasts 30 minutes), or daily
  into `BACKUP_DIR`. Point that at a USB drive or network share, not the same disk. Backups hold customer details and
  password hashes, so keep them safe.
- **Restore**: upload a backup on the **Server** tab, then press **Restart now**. The server swaps the data in on
  start and keeps the old data for `RESTORE_KEEP_DAYS` in case it needs undoing.
- **Upgrade**: take a backup, change the tag, then `docker compose pull && docker compose up -d`. The database is
  migrated on start. Update the desktop and Android apps too; they warn when the server speaks a different API
  version.

`docker stop` shuts the database down cleanly. The image has a health check on `GET /api/ping`.

## Links

- Source and docs: <https://github.com/Mr0btain/TicketMan>
- Running a server, in depth: [docs/operations.md](https://github.com/Mr0btain/TicketMan/blob/master/docs/operations.md)
- Every setting: [docs/configuration.md](https://github.com/Mr0btain/TicketMan/blob/master/docs/configuration.md)
