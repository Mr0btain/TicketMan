# Configuration

Every server setting, where it comes from, and the few things that change in development mode. Client settings are
covered briefly at the end.

## Server settings

Settings live in
[`server/src/main/resources/application.yaml`](../server/src/main/resources/application.yaml). Each value written
as `"$VAR:default"` is read from the environment variable `VAR`, falling back to the default after the colon.

| Setting | Env var | Default | What it does |
|---------|---------|---------|--------------|
| `ktor.deployment.port` | `PORT` | `8080` | Port for plain HTTP, on all interfaces |
| `tls.port` | `SSL_PORT` | `8443` | Port for HTTPS. `0` turns HTTPS off |
| `tls.httpMode` | `HTTP_MODE` | `on` | What the plain HTTP port does once HTTPS is on: `on`, `redirect` or `off` |
| `tls.keyStore` | `SSL_KEYSTORE` | blank | A PKCS12 file with the certificate to use. Blank means the server makes its own |
| `tls.keyStorePassword` | `SSL_KEYSTORE_PASSWORD` | blank | Password for `SSL_KEYSTORE` |
| `tls.keyAlias` | `SSL_KEY_ALIAS` | blank | Which key in `SSL_KEYSTORE` to use. Blank means the first one |
| `tls.privateKeyPassword` | `SSL_KEY_PASSWORD` | blank | Only when the key's password isn't the keystore's |
| `cors.hosts` | `CORS_HOSTS` | blank | Development mode only: hosts allowed to call the server from a browser, like `localhost:8080`. Blank allows any |
| `jwt.secret` | `SECRET` | a fixed string in the file | Signs every access and refresh token |
| `jwt.audience` | - | `my-audience` | Put in tokens and checked on every request |
| `jwt.issuer` | - | `http://localhost` | Put in tokens and checked by the verifier |
| `jwt.realm` | - | `TicketMan Server` | Realm the auth providers report |
| `env.name` | `NAME` | `Server` | Server name shown on the admin screen's Server tab |
| `env.dataPath` | `DATA_PATH` | blank | Folder for the database, photos, backups and restores. Blank means the platform's app data folder |
| `env.adminPassword` | `ADMIN_PASSWORD` | blank | Password for the `admin` user made when no admin exists. Blank means a random one is logged |

Settings with no env var can only be changed by editing the file.

### `SECRET`

The default secret is in the repo, so anyone can forge tokens for a server that keeps it. Always set `SECRET` to a
long random string on a real server, and keep it the same across restarts. Changing it signs everyone out, since
every existing token stops verifying.

The server logs an error on start when `SECRET` isn't set. The IDE's **Server[Run]** configuration doesn't set it,
so that's expected while developing.

### HTTPS

The HTTPS connector is added in `createServer()` in `Application.kt`, not through Ktor's own `ktor.security`
settings, because the certificate may have to be made before the server starts. It listens on the same host as
plain HTTP. See [Running a server](operations.md#https) for setting up certificates and trusting them on devices.

### `DATA_PATH`

Everything the server stores goes under `<DATA_PATH>/server/`. See [Running a server](operations.md#the-data-folder)
for what's inside.

When `DATA_PATH` is blank the server uses `AppDir` from `shared`:

| OS | Folder |
|----|--------|
| Linux | `$XDG_DATA_HOME/TicketMan`, or `$HOME/.local/share/TicketMan` when that isn't set |
| macOS | `$HOME/Library/Application Support/TicketMan` |
| Windows | `%APPDATA%\TicketMan` |

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

### Seeding on start

`Application.module()` also has a `generateTestData` flag, `false` in the repo. Set it to `true` to seed 1000
tickets, 400 customers, 100 inventory items and 25 users every time the server starts. It's for quick local
experiments only, so don't commit it switched on.

## Logging

The server logs to standard out through Logback, configured in
[`logback.xml`](../server/src/main/resources/logback.xml) at `info` level. Lines from TicketMan's own code come
through `AppLogger` and start with a tag in brackets, like `[TicketRoute:Get]`, which makes it easy to filter for
one area.

## Client settings

The client has no config file. What it needs is set from the app and saved on the device by `LocalStorage`:

| Setting | Set from |
|---------|----------|
| Server address, device name, page size, search result limit, cache size | Settings > Server and data |
| Trusted server certificates | Saying yes when asked to trust one |
| Remember login, start tab | Settings > Login |
| Saved username and password, tokens | Logging in |
| Theme, font, dark mode, reduce transparency | Settings > Appearance |
| Ticket list scope (mine or all) | Records screen |
| Device id and name, station credentials | Made on first start, and when registering a station |

The device id is how the server tells this device's session apart from others, so logging in again on the same
device replaces its old session instead of adding one.

## Known gaps

- The commented out `postgres` block in `application.yaml` isn't used. H2 is the only database.

## See also

- [Getting started](getting-started.md)
- [Running a server](operations.md)
