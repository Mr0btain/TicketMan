# Getting started

How to build and run each part of TicketMan, run the tests, and look over UI changes quickly.

## What to install

| Tool | Needed for | Notes |
|------|------------|-------|
| JDK 17 or newer | Everything | Developed on JDK 21. Gradle itself comes with the repo (`./gradlew`) |
| IntelliJ IDEA or Android Studio | Editing (optional) | With the Kotlin Multiplatform plugin. Run configurations are checked in under `.run/` |
| Android SDK with platform 37 | Android builds only | FileKit 0.16 needs `compileSdk` 37. Point `sdk.dir` in `local.properties` at the SDK |
| A recent browser | The web client | Chrome or Firefox, with Wasm GC support |
| Python 3 | The end to end script | Standard library only |
| Caddy | Trying the server behind a proxy (optional) | [caddyserver.com/docs/install](https://caddyserver.com/docs/install). No Docker needed |

Use `./gradlew` in Git Bash, macOS or Linux, and `.\gradlew.bat` in PowerShell. The first build downloads a lot of
dependencies and takes a while.

## Run the server

```bash
PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
```

- `PORT` defaults to 8080. The server only speaks plain HTTP; HTTPS is a reverse proxy's job (see
  [Behind a proxy](#behind-a-proxy) and [Configuration](configuration.md#https)). Clients connect to
  `http://localhost:8098` without a warning, since it never leaves the machine.
- `DATA_PATH` is where the database and photos go. Leave it out and the server uses the platform's app data
  folder (`~/.local/share/TicketMan` on Linux), which is probably not what you want for a scratch server.
- `ADMIN_PASSWORD` sets the password of the `admin` user made on first start. Without it a random password is
  printed in the log.
- `-Pdevelopment` turns on Ktor's development mode, which also allows seeding test data from the admin screen.

The server is ready when the log shows `Responding at http://0.0.0.0:8098`. Opening that address in a browser
returns an empty `200`, which is what clients use to check the server is up.

All server settings are on the [configuration](configuration.md) page.

## Run a client

### Desktop

```bash
./gradlew :composeApp:run
```

On first start, set the server address (`http://localhost:8098`) under Settings > Server, then log in as
`admin` from the foot of the nav rail. The desktop client is the
main target and the most complete.

A debug build (`./gradlew :composeApp:run -DticketmanDebug=true`) with no address saved tries
`http://localhost:8080`, so a server started with `./gradlew :server:run -Pdevelopment` and the default `PORT`
connects with nothing typed. The address is saved once a server answers there.

### Desktop with hot reload

```bash
./gradlew :composeApp:hotRunDesktop --mainClass org.mr0btain.ticketman.MainKt --auto
```

`--auto` recompiles and reloads when a source file is saved. Without it, trigger a reload by running
`./gradlew :composeApp:hotReloadDesktopMain` from another terminal. In the IDE, use the **Desktop [Run, hot]🔥**
run configuration.

### Web

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

This starts a webpack dev server and opens the app in the browser, with hot reload. Point it at a server started
with `-Pdevelopment` (so it allows the dev server's origin, see [Configuration](configuration.md#development-mode))
on another `PORT`, using the server's `http://` address. To try the web client the way a shop gets it, served by
the server itself behind Caddy, run `scripts/docker_local.sh` and open `https://localhost:8443` (see
[In Docker](#in-docker)).

### Android

```bash
./gradlew :androidApp:assembleDebug
```

The APK lands in `androidApp/build/outputs/apk/debug/`. Install it with `adb install` or run the `androidApp`
configuration from Android Studio. To reach a dev server on this machine from the emulator, run
`adb reverse tcp:<port> tcp:<port>` and connect to `http://localhost:<port>` (see
[Platforms](client/platforms.md#android)). It needs a JDK on the path or in `JAVA_HOME`, like any Gradle task.

To only check that the shared code compiles for Android, without the app: `./gradlew :composeApp:compileAndroidMain`.

### Desktop installers

`./gradlew :composeApp:packageDistributionForCurrentOS` builds a `.deb`, `.msi` or `.dmg` for the machine you're on,
named for it (`TicketMan-Windows-0.8.0-x64.msi`, see [releasing](releasing.md#building-by-hand)).

### Behind a proxy

Shops run the server behind a reverse proxy that does HTTPS ([Configuration](configuration.md#https)).
To try that setup from the repo, without Docker, install Caddy and run:

```bash
scripts/dev_proxy.sh
```

It starts a development server on `http://localhost:8080` with `TRUSTED_PROXIES=127.0.0.1,::1`, then Caddy on `https://localhost:8444` in front of it
([`scripts/dev/Caddyfile`](../scripts/dev/Caddyfile)). Ctrl+C stops both. `--no-server` starts only Caddy, in
front of a server you already have on `SERVER_PORT`. The data goes in `build/dev-proxy-data` unless `DATA_PATH`
says otherwise, and the admin password is `admin_pass`.

Point a client at `https://localhost:8444`:

- **Private certificate.** Caddy's certificate comes from its own local CA, which nothing trusts, so the desktop
  client shows the trust dialog for the CA, the way a shop with no domain sees it. The script never adds the CA to
  the system's trust store.
- **Public certificate.** The script exports Caddy's CA to `build/dev-truststore.p12`. Run the client with it as
  the only trusted CA and the certificate passes the system check, with no dialog, the way a public certificate
  does:

  ```bash
  ./gradlew :composeApp:run -DticketmanDebug=true -PdevTrustStore=build/dev-truststore.p12
  ```

- **A proxy's 413.** Anything under `https://localhost:8444/tiny/` reaches the server with the prefix taken off,
  but Caddy refuses bodies over 1 KB, so a photo upload there shows how the client takes a proxy turning a big
  request away.

The admin Server tab (`GET /api/admin/server`) should show `forwarded` true and the client's address as `::1` or
`127.0.0.1`. The [end to end script](#end-to-end) runs through the proxy too.

### In Docker

The shop setup itself, the image behind Caddy from [`docker-compose.yml`](../docker-compose.yml), runs on this
machine with:

```bash
scripts/docker_local.sh              # builds the server and image, starts both at https://localhost:8443
scripts/docker_local.sh --down       # stops it and deletes its data
```

It needs Docker and a user that can run it. On first start it's filled with made up customers, tickets, invoices
and staff (`SEED_TEST_DATA`, see [Configuration](configuration.md#seeding-on-start)); `--empty` starts a fresh one
without them. Log in as admin / `admin_pass`, `tech` / `tech_pass` (Normal) or `clerk` / `clerk_pass` (Restricted). The script prints Caddy's CA fingerprint
and how to connect: the desktop client with `./gradlew :composeApp:run -DticketmanDebug=true` asks to trust the CA,
while `-PdevTrustStore=build/docker-truststore.p12` trusts it already, like a public certificate. A browser on the
same machine opens `https://localhost:8443` and accepts its warning. `scripts/docker_smoke.sh` runs the automated
check of the same setup (see [Testing](testing.md#docker)). Building and running the image for a shop is on
[Running a server](operations.md#the-docker-image).

## Run the tests

```bash
./gradlew :server:test                 # Server
./gradlew :shared:jvmTest              # Shared module
./gradlew :composeApp:desktopTest      # Client: commonTest and desktopTest

# One class
./gradlew :server:test --tests "org.mr0btain.ticketman.ApplicationTest"
```

There's no lint task.

`composeApp:desktopTest` includes `UiKitRulesTest`, which fails if a screen outside `ui/kit` hard codes a color,
font size or shape, or uses a Material component. See [Kit rules](ui/kit-rules.md).

[Testing](testing.md) covers how to write tests: the server test helpers, `MockEngine` for client tests, and what
trips people up in each.

### Look at UI changes without running the app

Two tests draw the UI with sample data to PNG files:

```bash
./gradlew :composeApp:desktopTest --tests "*ScreenRenderTest*"      # -> composeApp/build/screen-renders/
./gradlew :composeApp:desktopTest --tests "*KitGalleryRenderTest*"  # -> composeApp/build/kit-gallery/
```

`ScreenRenderTest` draws the main screens (records, counter, check-in, pickup, admin, kiosk lock) in light and dark.
`KitGalleryRenderTest` draws the component gallery in each theme and font. This is the quickest way to check a UI
change, and the screenshots in these docs come from it.

### End to end

With a fresh dev server running (`ADMIN_PASSWORD=admin_pass` on an empty `DATA_PATH`):

```bash
python3 scripts/counter_e2e.py http://localhost:8098
python3 scripts/counter_e2e.py https://localhost:8444 --insecure   # through scripts/dev_proxy.sh
```

It walks a day at the counter over HTTP the way the client does: shop setup, an account request and approval,
first login setup, registering and approving a station, PIN logins, check-in, the queue, pickup with tax and
change, and the sessions list. Then it uploads a 13 MB photo, downloads a backup and uploads it to restore, which
are what a proxy's body limits and timeouts break. It stops at the first response it didn't expect. `--insecure`
skips checking the certificate, for Caddy's local one.

## Gotchas

- **The web dev server only works with a development server.** CORS is only on with `-Pdevelopment`, so a server
  started without it blocks the dev server's requests.
- **Port 8080 is the default for both the server and the web dev server.** Give the server another `PORT` when
  running both.
- **Server tests don't read `application.yaml`.** They use the helpers in `server/src/test/.../TestServer.kt`
  instead. The in-process test engine also handles big or cut-off request bodies differently from Netty, so check
  upload and download changes against a real `:server:run`.
- **iOS doesn't build.** Its targets are commented out; iOS is parked (see [decisions](decisions.md#ios-parked-not-removed)).

## See also

- [Configuration](configuration.md)
- [Running a server](operations.md)
- [Architecture overview](architecture/overview.md)
