# Testing

How TicketMan is tested, where each kind of test lives, and the helpers and traps in each. Read this before
writing a test, or when one fails for a reason that isn't the code under test.

## Running them

```bash
./gradlew :server:test                 # server: routes, services, database, HTTPS
./gradlew :shared:jvmTest              # shared: money, invoice math, rules, caches, time helpers
./gradlew :composeApp:desktopTest      # client: commonTest + desktopTest

./gradlew :server:test --tests "org.mr0btain.ticketman.TicketTest"   # one class
./gradlew :composeApp:desktopTest --tests "*ScreenRenderTest"         # pattern

python3 scripts/check_api_docs.py      # docs/api against the routes, see the API reference
```

Results are in each module's `build/test-results/` (JUnit XML) and `build/reports/tests/` (HTML). With `-q` a
passing run prints nothing.

| Kind | Source set | What's there |
|------|-----------|--------------|
| Server | `server/src/test/` | One class per area: `AuthTest`, `JwtTest`, `SessionTest`, `AccountTest`, `StationTest`, `CheckInTest`, `CounterTest`, `InvoiceTest`, `InventoryTest`, `TicketTest`, `ImageTest`, `AdminTest`, `RestoreTest`, `ProxyTest`, `LoginLimiterTest`, `WebClientTest`, ... |
| Shared | `shared/src/commonTest/` | Pure logic: `MoneyTest`, `InvoiceMathTest`, `UserRulesTest`, `LruCacheTest`, `TimeHelpersTest` |
| Client logic | `composeApp/src/commonTest/` | Handlers, repositories, seeds, pagers, storage, print templates, formatting. Network calls go to a `MockEngine` |
| Client, desktop only | `composeApp/src/desktopTest/` | Things that need the JVM: Java2D printing, QR codes checked with ZXing, the pinned HTTPS engine, streaming file bodies, render tests, `UiKitRulesTest` |
| End to end | `scripts/counter_e2e.py` | The whole counter flow against a real server, over HTTP |
| Docker | `scripts/docker_smoke.sh` | The image and `docker-compose.yml` behind Caddy: the end to end walk, a restore finished by restarting, and a clean stop |
| Docker, by hand | `scripts/docker_local.sh` | The same setup left running at `https://localhost:8443`, for the desktop client and a browser on this machine |

## Server tests

Server tests run the real `Application.module()` in Ktor's `testApplication`, with a fresh H2 database in a temp
folder, and call it over HTTP with the test client.

### The helpers

`testApplication` doesn't read `application.yaml`, so
[`TestServer.kt`](../server/src/test/kotlin/org/mr0btain/ticketman/TestServer.kt) supplies what the module needs:

| Helper | Does |
|--------|------|
| `createTestDataDir()` | A temp folder for the test's database and photos |
| `configureTestServer(dataDir, developmentMode = true, extraConfig = emptyMap())` | Sets `env.dataPath` and the `jwt.*` settings (secret `test-secret`, audience `test-audience`), adds any `extraConfig` entries (like `"env.proxy" to "on"`), and runs `module()`. HTTPS is off, since there's no `tls.port` |
| `cleanupTestDataDir(dataDir)` | Shuts H2 down, deletes the folder, and fails if it couldn't |

The usual shape:

```kotlin
class ThingTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun something() = testApplication {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().add(User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL))

        val token = json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest("tech", "tech_pass")))
            }.bodyAsText()
        ).accessToken!!

        val response = client.get("/api/user/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

- **Make users straight in the database.** After `startApplication()`, the database classes (`UserDatabase()`,
  `CustomerDatabase()`, ...) work on the test's database, since Exposed uses the connection `module()` opened.
  That's quicker than going through invites and approval. Plain text passwords are hashed on the way in.
- **Log in over HTTP** to get a token, as `AuthTest` and most classes do in a private `setup()` or `login()`.
- **Make tokens by hand** when testing the auth checks themselves: `JwtTest.token(...)` signs one with
  `test-secret` and whatever claims the test wants changed.
- **Restart on the same data** by running two `testApplication` blocks in one test with an H2 `SHUTDOWN` between
  them. `RestoreTest` and `DatabaseWriteTest.notesLeftBehindAreRemovedOnStart` do this, to test what happens on
  start.
- **Swap the clock** where a class takes one: `RequestLimiter(now = { clock })`, `LoginLimiter(now = { clock })`,
  `TicketDatabase(currentYear = ...)`.

### Pitfalls

- **`startApplication()` first.** Calling a database class before the module has run hits no database, or the
  last test's.
- **Always clean up.** H2 is opened with `DB_CLOSE_DELAY=-1`, so it stays open and holds its files until shut
  down. Skipping `cleanupTestDataDir` leaves the next test talking to the wrong database.
- **Development mode is on by default** in `configureTestServer`, as in Ktor's tests, so dev only routes (seeding,
  CORS) are allowed. Pass `developmentMode = false` to test a real server's behaviour.
- **The in-process engine isn't Netty.** It doesn't stream or cut off big bodies the way Netty does. Check upload,
  download and restore changes against a real `./gradlew :server:run` too.
- **Rate limits count within a test.** Each `testApplication` gets fresh limiters, but a test that logs in with a
  wrong password ten times will see `429` after that.
- **Decode with `ignoreUnknownKeys`** in tests, so a field added to a response doesn't break unrelated tests.

## Client tests

Client tests are in `commonTest`, so they run on the JVM with `desktopTest` and need no device. Anything that
talks to the server does it through Ktor's `MockEngine`.

### `MockEngine` and `RemoteHandler`

`RemoteHandler` takes its engine as a parameter, so a test builds one on a `MockEngine` and an in-memory store:

```kotlin
private val engine = MockEngine { request ->
    when (request.url.encodedPath) {
        SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
        "/api/data/customer/fetch" -> respond(Json.encodeToString(customers), HttpStatusCode.OK, jsonHeaders)
        else -> respond("", HttpStatusCode.NotFound)
    }
}

val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
```

- **Answer the ping.** `setUrl()` checks `SERVER_PING_PATH` for `SERVER_PING_BODY` before it takes an address. A
  mock that doesn't answer it leaves every repository with a blank URL, and they quietly do nothing.
- **Send JSON headers** (`ContentType.Application.Json`) with JSON bodies, or the client won't read them.
- **Record requests** in the engine (`requests += "${request.method.value} ${request.url.encodedPath}"`) to check
  what a handler sent, or didn't: `RecordsAccessTest` checks Restricted users send no writes this way.
- A single repository can be tested on its own `HttpClient(MockEngine { ... })`, like
  `RemoteRepositoryResultTest`.
- `InMemoryKeyValueStore` stands in for KSafe. It's in `commonTest` with `LocalStorageTest`.

### Coroutines

Handlers launch their work on a scope they're given, and requests finish on the mock engine's own threads, so a
test has to let both run:

- Pass `backgroundScope` (from `runTest`) as the handler's scope. It's cancelled when the test ends.
- After an action, run the test's coroutines and give the engine a moment, until the result shows or it clearly
  won't. Several tests have a `settle()` for this:

  ```kotlin
  private suspend fun TestScope.settle(done: () -> Boolean) {
      repeat(300) {
          advanceUntilIdle()
          if (done()) return
          withContext(Dispatchers.Default) { delay(10) }
      }
  }
  ```

  `advanceUntilIdle()` alone isn't enough: it doesn't wait for the engine's threads.
- **Testing `MainViewModel`** needs `Dispatchers.setMain(StandardTestDispatcher())` in `@BeforeTest` and
  `resetMain()` after, since it runs on `viewModelScope`. See `MainViewModelPagerTest`.
- **Time** is a parameter where it matters: `KioskHandler` takes `now`, so `KioskHandlerTest` moves the idle timer
  along without waiting.

### Desktop only tests

| Test | Checks |
|------|--------|
| `Java2DPrintLayoutTest` | Draws the printouts into images the way they'd go to a printer, and reads the QR code back off the page |
| `QrCodeTest` | The in-house QR encoder's codes decode with ZXing |
| `PinnedHttpsTest` | The OkHttp engine against a real HTTPS server with a private certificate: refused until trusted, then only that certificate, or ones from that same CA (a chain like Caddy's, whose intermediate changes), and the CA fetched from the proxy |
| `FileBodyTest` | A picked file goes up as it's read, with its size known so the server can check for room first |
| `UiKitRulesTest` | Screens only style through the UI kit. See [Kit rules](ui/kit-rules.md) |
| `KitGalleryRenderTest`, `ScreenRenderTest` | Render tests, below |

## Render tests

`KitGalleryRenderTest` and `ScreenRenderTest` draw the component gallery and the main screens to PNGs with sample
data, using `ImageComposeScene`. They're the quickest way to look over a UI change without a server or running the
app:

```bash
./gradlew :composeApp:desktopTest --tests "*RenderTest"
```

| Test | Writes to | Draws |
|------|-----------|-------|
| `KitGalleryRenderTest` | `composeApp/build/kit-gallery/` | The gallery in every theme and mode, every font, with reduce transparency, and 720 px wide; the Appearance settings |
| `ScreenRenderTest` | `composeApp/build/screen-renders/` | Counter ticket, pickup, check-in, kiosk lock, server trust, admin overview, users and server (direct and behind a proxy), the records tabs and view only, each in dark and light |

- **They only check a file was written.** Nothing compares pixels. Open the PNGs and look.
- **Screens are drawn with sample data**, built in the test from the real models, and with the screen
  composables' own parameters, not a view model. A screen that only takes a `MainViewModel` can't be rendered
  this way; split out a composable that takes its state as parameters.
- **Fonts load in the background.** The tests draw several frames, sleeping between them, before keeping one.
  Copy `render()` for a new test rather than drawing one frame.
- **Photos show their placeholder.** `LocalImageClient` is null, so `TmImage` has no server to load from.
- The images in `docs/images/` come from these tests, so they can be redone the same way after a UI change.

## End to end

[`scripts/counter_e2e.py`](../scripts/counter_e2e.py) walks the whole counter and kiosk flow against a real
server over HTTP, the way the client calls it, and stops at the first surprise:

```bash
PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
python3 scripts/counter_e2e.py http://localhost:8098

# Or behind Caddy, the way a shop runs it (see Getting started > Behind a proxy)
scripts/dev_proxy.sh
python3 scripts/counter_e2e.py https://localhost:8444 --insecure
```

Its 21 steps cover: the admin setting up the shop and stock; an account request approved and set up; an invited
Restricted clerk; registering a station and turning on kiosk mode; PIN logins; a check-in refused without a
signature, then made with a deposit; search, lookup by number and the repeat check; the clerk refused a change; a
pickup refused while money is owed, then paid with change and stock taken; the end of day report; the invoice as a PDF and in the CSV export; sessions; the
audit log; and the big transfers: a 13 MB photo upload put on a note, a backup with photos downloaded whole and
by range and checked against its sha256, and uploaded back to restore (then cancelled).

With `--restart` the restore isn't cancelled. A customer added after the backup was made should vanish: the script
calls `POST /api/admin/restart`, waits for `/api/ping` to go and come back, logs in again and checks the restore
was applied, Jane and the photo are back and the later customer is gone. It needs a server that something starts
again (`RESTART_ON_EXIT=true`), like the Docker image, or a shell loop around `bin/server` that runs it again when it
exits with code 3.

- **Start on an empty data folder.** The script makes fixed usernames and expects the `admin` that
  `ADMIN_PASSWORD` creates on a first start. A second run on the same folder stops at step 3 with `Username is
  already taken`. Delete the folder (or pick a new `DATA_PATH`) between runs; for `dev_proxy.sh` that's
  `build/dev-proxy-data`.
- It only needs Python's standard library.
- **Run it through the proxy after changing uploads, downloads or the proxy settings.** The test engine isn't
  Netty, and a proxy adds its own limits; `scripts/dev_proxy.sh` is the closest to a shop's setup without Docker.
  `--insecure` is for Caddy's local certificate.
- Run it after changing check-in, pickup, invoices, stations or PIN login. It's what catches a change that passes
  each unit test but breaks the flow between them.

### Docker

[`scripts/docker_smoke.sh`](../scripts/docker_smoke.sh) builds the server (`:server:installDist -PwithWeb`) and the
image, starts [`docker-compose.yml`](../docker-compose.yml) as its own project (`ticketman-smoke`, on
`https://localhost:8445` with Caddy's local CA and its own network range), waits for the health check, checks
a handshake without SNI (how Java connects to `localhost`) and the plain HTTP redirect, runs
`counter_e2e.py --insecure --restart` through Caddy, checks Docker started the server again, and stops it the way
`docker stop` does, expecting exit code 143 and `Database closed` in the log. It removes its containers and volumes
afterwards; `--keep` leaves them running, `--no-build` skips both builds' Gradle step.

```bash
scripts/docker_smoke.sh
```

Run it after changing the Dockerfile, the compose file, the restart route, or anything about how the server starts
and stops. Your user has to be able to run `docker`.

To try the apps by hand against the same setup, [`scripts/docker_local.sh`](../scripts/docker_local.sh) starts it as
`ticketman-local` at `https://localhost:8443` and leaves it running, keeping its data between runs (`--down` removes
it). A fresh one is filled with made up records to look around in (`SEED_TEST_DATA`; `--empty` skips that). It prints Caddy's CA fingerprint and the commands for the desktop client, either asking to trust the CA or
trusting it already through `build/docker-truststore.p12`; a browser on the same machine opens the address and
accepts its warning. Check the trust dialog, the "Not secure" warning (connect to `http://<this machine's LAN
address>:8080` from `:server:run`), and that the client keeps connecting after Caddy renews its certificate.

## Adding tests

- **A new route**: a server test that calls it over HTTP, for the happy path, each refusal it makes (`400`, `404`,
  `409`) and each access level that shouldn't get in. Then run `check_api_docs.py` and document it.
- **A new handler action**: a `commonTest` test on a `MockEngine`, checking what it sends and what state it ends
  in, and that it refuses when `canEdit()` is false if it changes anything.
- **A new screen or kit component**: add it to `ScreenRenderTest` or the gallery, and look at the render.
- **Money, rules and other shared logic**: a plain test in `shared/src/commonTest/`.

## CI

[`.gitea/workflows/ci.yml`](../.gitea/workflows/ci.yml) runs on every pull request and on pushes to `master`, in
two jobs:

| Job | Runs |
|-----|------|
| Doc checks | `scripts/check_api_docs.py` and `scripts/check_doc_links.py` |
| Tests | `:server:test`, `:shared:jvmTest`, `:composeApp:desktopTest`, then compiles the web and Android clients |

The Tests job installs JDK 21 and an Android SDK with the `compileSdk` platform for its last step, which compiles the
Android client. The test tasks themselves don't need the SDK. When it fails it keeps the HTML test
reports as the `test-reports` artifact, and it always keeps the render tests' PNGs as `screen-renders`, so a UI
change can be looked over from the pull request. Bump `platforms;android-37.0` in the workflows (CI and the APK jobs of both release ones) when
`compileSdk` changes. Newer platforms have a minor version in the package name (`android-37.0`, `android-36.1`);
`sdkmanager --list` shows the exact one.

## Gotchas

- **Paths are relative to the module.** Gradle runs each module's tests from that module's folder, which
  `UiKitRulesTest` and the render tests' `build/...` paths rely on. Running them from an IDE with another working
  directory can fail or write elsewhere.
- **`desktopTest` runs `commonTest` too.** A failure listed under `desktopTest` may be in a common test.
- **Android isn't tested, and web barely.** The commands above run the common tests on the JVM. Gradle also has
  `:composeApp:wasmJsBrowserTest`, which runs them in headless Chrome, but it isn't part of the usual run or CI
  (the runner has no Chrome), and nothing runs on an Android device. CI does compile both. See [Platforms](client/platforms.md#known-gaps).

## See also

- [Getting started](getting-started.md)
- [API reference](api/README.md)
- [Kit rules](ui/kit-rules.md)
