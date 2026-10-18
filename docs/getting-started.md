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

Use `./gradlew` in Git Bash, macOS or Linux, and `.\gradlew.bat` in PowerShell. The first build downloads a lot of
dependencies and takes a while.

## Run the server

```bash
PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
```

- `PORT` defaults to 8080. HTTPS listens on `SSL_PORT`, 8443 by default, with a certificate the server makes in
  `DATA_PATH` the first time. Clients ask you to check its fingerprint (printed in the log) before trusting it.
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

On first start, set the server address (`http://localhost:8098`) under Settings > Server and data, then log in as
`admin` from the foot of the nav rail. The desktop client is the
main target and the most complete.

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
on another `PORT`, using the server's `http://` address: the browser hasn't been told to trust the server's own
certificate. To try the web client the way a shop gets it, served by the server itself, run
`./gradlew :server:run -PwithWeb` and open `https://localhost:8443` (see
[Running a server](operations.md#web-client)).

### Android

```bash
./gradlew :androidApp:assembleDebug
```

The APK lands in `androidApp/build/outputs/apk/debug/`. Like the web app, it doesn't get past start up yet; see
[Platforms](client/platforms.md#known-gaps). Install it with `adb install` or run the `androidApp`
configuration from Android Studio. An emulator reaches the host machine's server at `http://10.0.2.2:<port>`.

To only check that the shared code compiles for Android, without the app: `./gradlew :composeApp:compileAndroidMain`.

### Desktop installers

`./gradlew :composeApp:packageDistributionForCurrentOS` builds a `.deb`, `.msi` or `.dmg` for the machine you're on.

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
font size or shape, or uses a Material component. See [CLAUDE.md](../CLAUDE.md) under UI kit for the rules.

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
```

It walks a day at the counter over HTTP the way the client does: shop setup, an account request and approval,
first login setup, registering and approving a station, PIN logins, check-in, the queue, pickup with tax and
change, and the sessions list. It stops at the first response it didn't expect.

## Gotchas

- **The web dev server only works with a development server.** CORS is only on with `-Pdevelopment`, so a server
  started without it blocks the dev server's requests.
- **Port 8080 is the default for both the server and the web dev server.** Give the server another `PORT` when
  running both.
- **Server tests don't read `application.yaml`.** They use the helpers in `server/src/test/.../TestServer.kt`
  instead. The in-process test engine also handles big or cut-off request bodies differently from Netty, so check
  upload and download changes against a real `:server:run`.
- **iOS doesn't build.** Its targets are commented out.

## See also

- [Configuration](configuration.md)
- [Running a server](operations.md)
- [Architecture overview](architecture/overview.md)
