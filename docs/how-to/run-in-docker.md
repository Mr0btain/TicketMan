# Run TicketMan in Docker

Three ways, from a two minute look to a shop's server. The server only speaks plain HTTP; anything past this
machine wants HTTPS in front of it, which the [compose file](../../docker/docker-compose.yml) does with Caddy.

## 1. Look around: the published image

```bash
docker run -d --name ticketman -p 8080:8080 \
  -e TZ=America/New_York -e ADMIN_PASSWORD=change_me \
  -v ticketman-data:/data \
  mr0btain/ticketman:latest
```

Open `http://localhost:8080` and log in as `admin` with that password (without `ADMIN_PASSWORD` a random one is in
`docker logs ticketman`). `mr0btain/ticketman` has the web client built in; `mr0btain/ticketman-server` is the server
alone, for shops that only use the desktop and Android apps. Both run on `linux/amd64` and `linux/arm64`. The
[Docker Hub page](../../docker/dockerhub.md) lists every setting, volume and port.

Plain HTTP is fine on the same machine. From anywhere else passwords would cross the network unencrypted (the apps
say "Not secure"), so use 3 for anything real.

## 2. Try it from the source, with made up records

```bash
scripts/docker_local.sh            # builds the server and image, starts it at https://localhost:8443
scripts/docker_local.sh --empty    # the same without the sample customers, tickets and staff
scripts/docker_local.sh --down     # stops it and deletes its data
```

This is the shop setup below on your own machine: the server behind Caddy using its own local certificate
authority, as its own compose project so it doesn't touch a real install. Log in as `admin` / `admin_pass`, or
`tech` / `tech_pass` and `clerk` / `clerk_pass`. The desktop and Android apps ask once to trust Caddy's certificate.
The script prints the CA's fingerprint to check, and how to start a desktop client that trusts it already:

```bash
./gradlew :composeApp:run -DticketmanDebug=true -PdevTrustStore=build/docker-truststore.p12
```

`scripts/docker_smoke.sh` runs the same setup unattended, walks a ticket from check-in to pickup over HTTP, restores
a backup by restarting, and stops it cleanly ([Testing](../testing.md)).

## 3. A shop's server behind Caddy

1. Point a domain at the machine, with ports 80 and 443 reaching it, or use its LAN address for a shop that's only
   reached on its own network.
2. Make `docker/.env`, next to [`docker/docker-compose.yml`](../../docker/docker-compose.yml):

   ```bash
   DOMAIN=tickets.myshop.com   # or 192.168.1.20
   CERTIFICATE=public          # private for a LAN address, a .local name or localhost
   TZ=America/New_York
   ADMIN_PASSWORD=             # optional, the first admin password
   ```

3. Build the server on the host, then start both containers, from the repo root:

   ```bash
   ./gradlew :server:installDist -PwithWeb
   docker compose -f docker/docker-compose.yml up -d --build
   ```

4. Open `https://tickets.myshop.com`, or point a client at it (Settings > Server), and log in as `admin`. Set up the
   shop profile on the admin screen's Server tab before taking money.

To use the published image instead of building, the [Docker Hub page](../../docker/dockerhub.md#running-it-in-production-example)
has the same compose file pointing at `mr0btain/ticketman`.

## After that

- **Backups**: `BACKUP_DIR` is `/backups` in the compose file, a daily backup kept in the `ticketman-backups` volume.
  Download one any time from the admin screen. See [Backup](../operations.md#backup) and [Restore](../operations.md#restore).
- **Upgrading**: take a backup, then `git pull`, build again and `docker compose -f docker/docker-compose.yml up -d --build` (or pull the newer
  image). The database is migrated on start.
- **Another proxy already on the machine**: [Behind another proxy](../operations.md#behind-another-proxy).
- **A certificate devices don't trust out of the box**: [Private certificates](../operations.md#private-certificates).
- **Customers reaching the status page from the internet**: [The customer status page](../operations.md#the-customer-status-page).
- **Why the image copies the host's build**: [The Docker image](../operations.md#the-docker-image).
- **Every setting**: [Configuration](../configuration.md).
