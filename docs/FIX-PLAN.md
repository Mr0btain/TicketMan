# Fix plan

Everything the docs found that should change, gathered from every page's **Known gaps** and **Gotchas**, the
`TODO` comments in the code, and issues found while writing the docs. Grouped into phases, roughly in the order
they're worth doing: bugs people will hit first, then security, data, API cleanup, the client, code health,
platforms, and running behind a reverse proxy in Docker.

Tick an item when it's merged. When one is done, also fix the doc page it came from (its **Source**), usually by
taking it out of that page's Known gaps. Things that aren't problems, just rules to follow, are in
[Rules to keep](#rules-to-keep) at the end, not in the phases.

Each phase is sized for a PR or two. Items marked **(decide)** need a choice made before the work; the item says
what the options are.

## Phase 1: Bugs people will hit

Small, user visible, no design questions.

- [x] **Phones can't log out, and logging in is hard to find.** `PhoneAppShell` has no account controls. Add the
      user menu (or log in button) to the phone layout, or a Log in / Log out row at the top of Settings. *Done:*
      an Account panel at the top of Settings (`AccountPanel`).
      *Source:* [navigation](ui/screens/navigation.md), [settings](ui/screens/settings.md)
- [x] **Wrong login hint on phones.** The records screen says "Log in from the bottom of the sidebar". Word it for
      both layouts, or point at whatever the item above adds. *Source:* [records](ui/screens/records.md)
- [x] **Failed deletes go unreported.** `CustomerRecords`, `TicketRecords` and `InventoryRecords` `remove()` ignore
      the server's answer and clear the selection first, so a refused or failed delete looks like it worked until
      the list reloads. Show a snackbar on failure, and only clear the selection once the server says yes.
      *Source:* found while writing the KDoc
- [x] **`InvoiceScreen` can't be reached.** It's registered in `NavigationHost` but nothing links to it. Add an
      Invoices item to the nav (or a tab on the admin screen), or delete the screen. **(decide)**
      *Source:* [navigation](ui/screens/navigation.md)
- [x] **A crash waiting to happen.** `RecordsScreen`'s delete dialog has `DataType.USER -> TODO(...)`. Replace it with
      a no-op branch (users are deleted on the admin screen). *Source:* [records](ui/screens/records.md)
- [x] **Cancelling a dialog on a record deleted elsewhere.** Cancel reloads the record, which fails if another device
      deleted it meanwhile. Clear the selection and refresh the list when the reload finds nothing.
      *Source:* [client records](client/records.md#gotchas)
- [x] **The low stock threshold isn't saved.** It goes back to 5 on restart. Save it in `LocalStorage` like the
      other settings. *Source:* [admin](ui/screens/admin.md#known-gaps)
- [x] **Admin links stack screens.** `AdminScreen` uses `navigate()` instead of `navigateTo()`, so each link adds to
      the back stack. *Source:* [navigation](ui/screens/navigation.md#gotchas)
- [x] **Ticket dialog stays open after Save**, while the customer and inventory dialogs close. Pick one behaviour
      for all three. **Decided:** all three close on Save. *Source:* [records](ui/screens/records.md#dialogs)

## Phase 2: Security and accounts

- [x] **`/set/user` takes any user as a technician**, even Restricted or deactivated ones. Apply check-in's rule
      (active, Normal access or higher). *Done:* `400 That technician can't take tickets`.
      *Source:* [ticket API](api/ticket.md#post-apidataticketsetuseridentifier)
- [x] **Password and username rules aren't applied everywhere.** `POST /api/user/add` and `/password/{id}` don't
      check the 8 character minimum, and `/update` doesn't check `UsernameRules`. *Done:* `PasswordRules` in shared,
      used by the server and the client's dialogs; `/update` only checks a changed username.
      *Source:* [users and accounts](server/users-and-accounts.md#rules-shared-with-the-client)
- [x] **Remove unused routes and code** that skip the usual checks:
  - `POST /api/user/add/all` (no username rules, no taken check; nothing calls it),
  - `POST /api/auth/user` and `ServerUserClient.checkLoginCredentials` (nothing calls it any more; `auth.md`
    wrongly says the client does),
  - `Database.removeImage(id)` (doesn't check the owner; nothing calls it).

  Update `docs/api/` and run `check_api_docs.py`. *Done:* also `Database.addAll` and its overrides, which only
  `/add/all` used. *Source:* [user API](api/user.md#gotchas), [auth](server/auth.md#gotchas),
  [database](server/database.md#gotchas)
- [x] **Pending photos can be fetched by anyone logged in** who knows the id. Only serve a `PENDING` photo to the user
      who uploaded it. *Source:* [photos](server/photos.md#3-serving)
- [x] **The default `SECRET` is public.** Refuse to start outside development mode when `SECRET` isn't set (it
      only logs an error now), or make and keep a random one in the data folder like the certificate. **(decide)**
      **Decided:** make and keep one (`ServerSecret`, `<dataPath>/server/secret`). The yaml default is now blank.
      *Source:* [auth](server/auth.md#tokens), [configuration](configuration.md#secret)
- [x] **Expired sessions are never removed.** `TokenTable` keeps a row per login until someone logs it out, even
      after the refresh token has expired. Delete expired sessions in the hourly sweep or on start, and drop the
      `TODO` in `Application.kt`. *Done:* `expiresAt` column, `removeExpired()` in the hourly sweep, expired
      sessions refused straight away. *Source:* found while checking the TODOs
- [x] **Refresh tokens aren't rotated.** Issue a new refresh token on each refresh and refuse the old one, so a
      stolen one stops working once the real device refreshes. The client already stores what it's given.
      *Done:* it didn't store it, so `ServerUserClient.refreshTokens` does now. Tokens also got a `jti`, since two
      made in the same second were identical.
      *Source:* [auth](server/auth.md#tokens), [decisions](decisions.md#refresh-tokens-last-a-year-and-are-swapped-on-each-use)
- [x] **The backup download link is a bearer credential** for 30 minutes and lands in browser history. Make it
      single use, or shorten its life once the download has started. **Decided:** each use moves its expiry to 5
      minutes from then (`RESUME_WINDOW`), so resuming still works. *Source:* [backup](server/backup-restore.md#gotchas)
- [x] **Kiosk exit and idle lock are client only.** Tell the server when a kiosk is left, and give PIN sessions a
      server side idle expiry (say, the station's idle timeout plus a margin) so a switched off kiosk doesn't leave
      someone signed in for 12 hours. *Done:* idle timeout plus 10 minutes, kept alive by the kiosk while in use;
      `POST /api/auth/kiosk/leave`. *Source:* [PINs and stations](server/pins-and-stations.md#kiosk-sessions-on-the-server),
      [kiosk](ui/screens/kiosk.md#leaving-kiosk-mode)
- [x] **Rate limits behind a proxy.** Behind a reverse proxy every request has the proxy's address. Add an opt-in
      setting to trust `X-Forwarded-For` (Ktor's `XForwardedHeaders`). Keeping counts across restarts is optional.
      *Done:* `TRUST_PROXY`, taking the last address in the header. Counts still reset on restart.
      *Source:* [auth](server/auth.md#wrong-password-limit), [configuration](configuration.md#trusted_proxies)
- [x] **Not enough access answers `401`, not `403`.** A Restricted user hitting a write route looks like an expired
      token, so the client tries a refresh first. Answer `403` when the token is fine but the access level isn't,
      and have the client show "You don't have access" instead of asking for a login. *Done:* `NetworkError.FORBIDDEN`
      now reads "You don't have access to that". *Source:* [request lifecycle](architecture/request-lifecycle.md#gotchas),
      [auth](server/auth.md#checking-each-request)
- [x] **Plain HTTP is on by default.** Once devices have moved to `https://` addresses, default `HTTP_MODE` to
      `redirect`. *Done:* blank means `redirect` (still `on` in development mode), and clients with an `http://`
      address follow the redirect and save the `https://` one. *Source:* [operations](operations.md#plain-http)

## Phase 3: Data and the server

- [x] **The tax rate isn't locked at check-in.** Saving an open invoice's lines reads the shop's current rate.
      Either keep the rate the invoice was made with, or keep updating it and show the change. **(decide)**
      **Decided:** an invoice keeps the rate it was made with ([decisions](decisions.md#invoices-keep-the-tax-rate-they-were-made-with)).
      *Source:* [counter](server/counter.md#gotchas), [money](shared/money.md#gotchas)
- [x] **Invoice, charge and payment ids aren't v7.** They default to `Uuid.random()`. Make them `Uuid.generateV7()`
      like every other id, so times can be read from them consistently. *Source:* [domain models](shared/domain-models.md#gotchas)
- [x] **Audit entries are written in their own transaction**, so one inside a rolled back change still lands, and a
      failed write is only logged. Add a variant that joins the caller's transaction, and use it inside check-in and
      pickup. *Done:* `log()` itself goes through `dbWrite`, so it joins any open transaction; check-in, pickup and
      void log inside theirs. *Source:* [audit log](server/audit-log.md#gotchas)
- [x] **The audit log is never trimmed.** Add a retention setting (say, two years) applied by the sweep.
      *Done:* `AUDIT_LOG_DAYS`, 730 by default.
      *Source:* [audit log](server/audit-log.md#gotchas)
- [x] **Restore leftovers pile up.** Remove `pre-restore-*` folders and `restore/failed-*.zip` files older than a
      set age, or list them on the admin screen with a delete button. *Done:* swept after `RESTORE_KEEP_DAYS` (30). *Source:* [operations](operations.md#known-gaps)
- [x] **`server/tls/` isn't in backups**, so a restored server on a new machine makes a new certificate and every
      device asks again. Include it (it's secret, like the database). *Done:* backup format 2, restored before the
      certificate is read. *Source:* [operations](operations.md#known-gaps)
- [x] **The made certificate isn't renewed.** It lasts ten years. Show its expiry on the admin Server tab with a
      warning in the last year, and a way to make a new one. **Decided:** the new one waits in `tls/next/` and is
      used from the next start (`POST /api/admin/certificate/renew`). *Source:* [operations](operations.md#known-gaps)
- [x] **No scheduled backups.** Add a daily backup to a folder (and how many to keep) as a server setting.
      **Decided:** environment variables, `BACKUP_DIR`, `BACKUP_KEEP`, `BACKUP_PHOTOS`, shown on the Server tab.
      *Source:* [operations](operations.md#known-gaps)
- [x] **A database only restore leaves photos missing.** Warn before restoring a backup without photos, and say
      so on the restored server. *Done:* the confirm dialog (by file name), the waiting restore (by manifest) and
      the restored server's Server tab (`ServerInfoResponse.lastRestore`). *Source:* [backup](server/backup-restore.md#gotchas)
- [x] **Some databases predate `dbQuery`/`dbWrite`.** Move the ones still using `appLogTryCatch` + `transaction`
      over, so failures are reported the same way. *Source:* [overview](architecture/overview.md#known-gaps)
- [x] **Some list routes answer `404` for a database error.** `/fetch` and `/search` on users, customers, tickets
      and inventory turn a failed read into `404`; it should be `500`. *Source:* [API overview](api/README.md#status-codes)
- [x] **Logins without a device id add a session every time.** Old clients and scripts pile up sessions. Expired
      session clean up (Phase 2) limits it; optionally refuse a blank `deviceId`. *Done:* not refused (scripts and
      tests rely on it), but only the newest 10 such sessions are kept per user. *Source:* [auth](server/auth.md#gotchas)

## Phase 4: API and plumbing cleanup

These change both sides, so do them together, and check `check_api_docs.py` after each.

- [x] **Route paths are strings on both sides.** Move the path strings into `shared` (constants per route file)
      so a rename breaks the build instead of the app. *Done:* `ApiRoutes.kt`, read by `check_api_docs.py` too. *Source:* [entity layers](architecture/entity-layers.md#gotchas),
      [networking](client/networking.md#gotchas)
- [x] **Hand kept lists of record types.** Replace them with one registry so a new record type can't be missed:
  - `RemoteHandler.setUrl()` sets each repository's URL by name (keep the repositories in a list),
  - `ImageDatabase.markUnreferencedDeleted` and `LocalDatabase.removeDetachedNotes` list every note join table
    (ask each `Database` for its note table instead).

  *Done:* `RemoteHandler.repositories` and `LocalDatabase.recordDatabases`.

  *Source:* [networking](client/networking.md#gotchas), [add a record type](how-to/add-an-entity.md)
- [x] **Reads that are `POST`.** Move `/get`, `/fetch`, `/search` and `/get/notes/{id}` to `GET` with path and
      query parameters, like the newer routes. *Done:* also the invoice and audit pages, `/fetch/mine` and the
      counter search. The station routes and repeat check stay `POST` (a secret, a whole device). *Source:* [API overview](api/README.md#gotchas),
      [decisions](decisions.md#route-paths-shared-reads-as-get)
- [x] **`/add` answers differ by record type** (customers `202` and no body, inventory `201` and no body, tickets
      `201` with the ticket). Make them all `201` with the saved record. *Done:* users too. *Source:* [API overview](api/README.md#gotchas)
- [x] **Reads can't tell "nothing there" from "couldn't load".** Some repository reads return null or an empty list
      on failure. Return a `TmResult` where the screen should say the load failed. *Done:* every `RemoteRepository`
      read, invoices, audit and counter search; the records lists show `OffsetPager.loadError`. A record whose
      notes didn't load no longer opens as having none (saving it would have deleted them).
      *Source:* [request lifecycle](architecture/request-lifecycle.md#gotchas)
- [x] **Restricted users can't list users** (`/api/user/get`, `/fetch`, `/search` need Normal), unlike every other
      record type. Decide whether that's on purpose; if not, open them to Restricted with passwords still blanked.
      **(decide)** **Decided:** opened to Restricted. *Source:* [user API](api/user.md#gotchas)
- [x] **No API versioning.** Fine while client and server ship together. Revisit if a shop could run mismatched
      versions. **Decided:** a version check, not versioned routes: `X-TicketMan-Api` on `/api/ping`, and the client
      warns on a mismatch ([API overview](api/README.md#versions)). *Source:* [API overview](api/README.md#versions)
 
## Phase 5: The client

- [x] **Counter on narrow windows.** The queue panel is always 360 dp beside the main area and the top bar doesn't
      wrap. Stack them, or move the queue behind a button, on narrow widths. *Done:* `CounterLayout`: under 900 dp
      the buttons are icons and the queue is a dialog behind its own button. The ticket card, pickup and check-in
      wrap and stack to fit a phone, with narrow render tests. *Source:* [counter](ui/screens/counter.md#known-gaps)
- [x] **Admin screen reloads everything** on every visit and every Refresh, one request per tab. Load a tab's data
      when it's opened. *Done:* `AdminHandler.refresh(tab)`, plus the waiting accounts for the Users badge.
      *Source:* [admin](ui/screens/admin.md#known-gaps)
- [x] **Settings:** move Start tab out of the Login group and make it a dropdown rather than click to cycle; lay
      the panels out in two columns on wide windows. *Done:* a Records group (start tab, page size, search limit,
      cache size) with a `CHOICE` setting type; Server and data is now Server; two columns from 1000 dp
      (`SettingsLayout`). *Source:* [settings](ui/screens/settings.md)
- [x] **Notes edited on two devices.** `syncNotes` overwrites a note edited elsewhere since the record was opened.
      Warn when a note changed on the server since it was loaded, or send the version it was edited from.
      *Done:* `syncNotes` diffs against the notes as loaded (`seed.savedNotes`): another device's edit or delete
      isn't overwritten (a snackbar says so), and notes it added are no longer deleted, which they were.
      *Source:* [client records](client/records.md#gotchas)
- [x] **Old name format.** `cleanName()` treats `:` as a name separator, left from an older way of storing names,
      and there's a `TODO` about middle names. Store names as typed and drop the `:` handling, with a data fix for
      old rows. *Done:* one Name field in every form, names stored and shown as typed (the server no longer
      lowercases them), user search ignores case, and a one time fix (`runOnce("legacy-names")`, the new
      `DataFixTable`) rewrites old `first:last` and lowercase names. `API_VERSION` is 3, since older clients still
      write the old format. *Source:* [domain models](shared/domain-models.md#gotchas), `App.kt` TODO
- [x] **`Address.fromString` and `Device.fromString`** cut two word values apart. Nothing calls them; delete them.
      *Source:* [domain models](shared/domain-models.md#gotchas)
- [x] **Currency is always `$`.** Add a currency symbol (or locale) to the shop profile and use it in `Money.format`.
      *Done:* `ShopProfile.currencySymbol` and `Money.format(cents, symbol)`; the client reads it through the kit's
      `LocalCurrencySymbol`. The symbol always goes first with a dot before the cents. *Source:* [money](shared/money.md#gotchas)
- [x] **Cache size changes apply on the next start.** Either rebuild the caches when it changes or say so in the
      setting's description (it does say "applies on restart" now; keep or fix). *Done:* `LruCache.resize`, applied
      straight away. *Source:* [state](client/state.md#gotchas)

## Phase 6: Code health

- [x] **Split the big handlers.** `CounterHandler` and `AdminHandler` are each over 500 lines. Split by area
      (counter: search and queue, check-in, pickup and invoices, printing; admin: one per tab group).
      *Done:* `counter.checkIn`, `counter.pickup`, `counter.invoices`, `counter.printer`, and `admin.accounts`,
      `admin.devices`, `admin.server`, each sharing a small `CounterSupport` / `AdminSupport`; tickets load through
      `RemoteHandler.loadTicketDetail`. *Source:* [state](client/state.md#gotchas), [counter](ui/screens/counter.md#known-gaps), [admin](ui/screens/admin.md#known-gaps)
- [x] **Leftovers to remove:**
  - `AdminScreen`'s unused `snackBarState` and `coroutineScope` parameters ([navigation](ui/screens/navigation.md)),
  - `isTicketForNewCustomer` in `RecordsScreen` ([records](ui/screens/records.md)),
  - `SettingItem.enabled`'s `TODO`, and the log tags declared inside `SettingsScreen` ([settings](ui/screens/settings.md)),
  - the commented out `postgres` block in `application.yaml` ([configuration](configuration.md)).

  *Done:* also `SettingsScreen`'s unused `scope`, `NavigationHost`'s parameters that only fed those, `App()`'s
  `println` of the platform on every recomposition, and the commented out client `AppLogger.kt`.
- [x] **Stale and open `TODO` comments.** Delete the ones already done, and turn real ones into items here:
  - done already: "Secure the storage of these, maybe encrypt?" (`CreateUserHttpClient`, `CreateDatabaseHttpClient`;
    KSafe encrypts), "timeout for session tokens" (`Application.kt`; see Phase 2), "Handle encryption in the rest of
    these routes" (`UserRoute`; passwords are hashed and HTTPS is on), "Find new way of using identifier"
    (`ServerUserClient`; there's a device id now), "Setup getting user info from server" (`ServerUserClient`;
    `/api/user/me` does it), "Add logging" (`AuthRoute`; logins are audited),
  - ideas, to keep or drop: import database data from a file, photos on every record type, assigning existing
    tickets to a customer, starting a new customer from the ticket dialog, hours worked and parts on tickets,
    exporting invoices, a reorder prompt for low stock (`App.kt`, `CustomerDialog`, `TicketDialog`, `TicketPanel`,
    `InventoryPanel`),
  - checks: `AppDir.jvm.kt` "test this" (twice), `ConfigureDatabase.kt` "work with OS file dir", `AppLogger`
    "store these", `AppDir.wasmJs.kt`.

  *Done:* there are no `TODO`s left. The ideas are under [Ideas](#ideas); checking `AppDir` on macOS and Windows
  is in Phase 7; `DATA_PATH` already falls back to the OS folder; the web `AppDir` has no folder (`null`); logs
  aren't stored, which Docker and the OS service manager handle.
- [x] **Tighten `UiKitRulesTest`.** It's a text search, so it misses `Color.Red`, a `TextStyle(...)` on a screen,
      Material 2 imports and Material subpackages, and it flags banned text inside comments. Catch the first three,
      allow `material3.windowsizeclass`, and skip comments. *Done:* comments and strings are blanked first, and
      `rulesCatchWhatTheyShould` checks the rules on a made up file. *Source:* [kit rules](ui/kit-rules.md#gotchas)
- [x] **`SettingItem` is cast by `type`.** A mismatched item is quietly not drawn. Make it a sealed class per type.
      *Done:* `SettingItem.Switch`, `Text`, `Action` and `Choice`; `SettingType` is gone.
      *Source:* [settings](ui/screens/settings.md)
- [x] **Telling desktop apart by name.** `App()` checks `getPlatform().name.contains("Java")`. Use an `expect` (or a
      `Platform` value) instead. *Done:* `alwaysWide` in `ui/screens/navigation/`.
      *Source:* [add platform code](how-to/add-a-platform-actual.md#gotchas)
- [x] **Logging.** `AppLogger.d` isn't hidden in release builds, and `LOG_LEVEL` is only read once. Make debug
      logging follow `isDebug()`. *Done:* `LOG_LEVEL` (which nothing set) is replaced by `AppLogger.debug`, which
      `d` checks on every call; clients set it from `isDebug()`, the server in development mode.
      *Source:* [results and errors](shared/results-and-errors.md#gotchas)
- [x] **Two `Result` types.** `kotlin.Result` and the app's `Result` are both in scope. Consider renaming the app's
      (`Outcome`?) to stop mix-ups. Optional. *Done:* the app's is `TmResult` (`error/TmResult.kt`);
      `EmptyResult` keeps its name. *Source:* [results and errors](shared/results-and-errors.md#gotchas)

## Phase 7: Platforms, CI and docs

- [x] **Run the web client end to end** in a browser, served by `-PwithWeb`. Same list as Android.
      *Source:* [platforms](client/platforms.md#known-gaps) *Done:* in headless Chromium (Playwright): login,
      records, a note with a photo, counter search and ticket card, the whole check-in wizard with a drawn
      signature, printing a receipt (the iframe's HTML checked), backup download, restore upload and the restart
      that applies it, the admin tabs, settings, registering a station, kiosk mode and PIN sign in. Fixed along the
      way, on every platform: a failed first page made `OffsetPager` ask for the next one three times (and a later
      scroll skipped page 0); the lists showed "Couldn't load" under the "not logged in" banner; the ticket card
      showed change given as "Balance -$0.10"; note dialogs said "Edit note" once typing started; "Promised thu oct
      22" lowercased day names; a station the server no longer knows wasn't forgotten (the API docs said it was);
      the suggested device name was "Java 21" / "Web with Kotlin/Wasm"; `counter_e2e.py` still sent `first:last`
      names. On web only: kiosk full screen is refused without a click, which left an uncaught promise error; it
      now asks again on the next click or key press. Escape didn't close dialogs in the browser; `TmDialog` handles
      it itself now.
- [x] **Large uploads on web** are read into memory whole. Stream them if Kotlin/Wasm allows, or cap the size the
      web client offers. *Done:* capped. Ktor's browser engine builds the whole body before sending, so it can't
      stream; `fileBodyLimitBytes` is 512 MB on web (none elsewhere) and the restore card says when a backup is
      too big, pointing at the desktop app. *Source:* [add platform code](how-to/add-a-platform-actual.md#gotchas)
- [x] **iOS.** The targets are commented out and `iosApp` doesn't build. Decide whether to support it; if not,
      remove the `iosMain` source sets and `iosApp`. **(decide)** *Done:* parked, files kept
      ([decisions](decisions.md#ios-parked-not-removed)). *Source:* [platforms](client/platforms.md#known-gaps)
- [x] **CI.** A Gitea Actions workflow that runs `:server:test`, `:shared:jvmTest`, `:composeApp:desktopTest`,
      `check_api_docs.py` and the docs link check (from [STYLE.md](STYLE.md#keeping-them-current)) on every PR.
      Add `:composeApp:wasmJsBrowserTest` if a headless browser is available there. *Done:*
      `.gitea/workflows/ci.yml`, with the link check now `scripts/check_doc_links.py`; it also compiles web and
      Android and keeps the render PNGs. No `wasmJsBrowserTest`, since the runner has no Chrome.
      *Source:* [testing](testing.md#gotchas)
- [x] **Font licence.** `composeApp/licenses/` has Barlow's and Atkinson's but not JetBrains Mono's (also OFL).
      Add it. *Done:* `JetBrainsMono-OFL.txt`. *Source:* found while writing [add a theme or font](how-to/add-a-theme-or-font.md)
- [x] **Docs to correct:**
  - `server/auth.md` says the client still calls `POST /api/auth/user`; it doesn't (fixed by removing it in Phase 2),
  - `server/migrations.md` says the order of `LocalDatabase.tables` matters because of foreign keys; check whether
    Exposed's `SchemaUtils.create` sorts tables by reference (the list already has `CustomerNoteTable` before
    `NoteTable`) and correct the page.

  *Done:* `auth.md` was already right after Phase 2. Exposed 1.5's `SchemaUtils.create` does sort by reference
  (`sortTablesByReferences`), so `migrations.md` now says the order doesn't matter.
- [x] **Dokka** (optional): a generated API site from the KDoc. *Done:* skipped; the `docs/` pages and KDoc in
      the IDE cover it. *Source:* [PLAN.md](PLAN.md)

## Phase 8: Behind a reverse proxy by default

The normal setup is now the server in a Docker container behind a reverse proxy that handles HTTPS, usually with
a public certificate. The server's own HTTPS and pinned certificate stay as the "direct" setup. Development mode
keeps working outside a container with no proxy, so the server and client can be tried from the repo as they
are now. Do this phase before 9 and 10.

- [x] **A `PROXY` setting** (`env.proxy`). It only changes defaults; a variable set by hand still wins:

  | | `PROXY=on` (default) | `PROXY=off` (direct) |
  |---|---|---|
  | `SSL_PORT` | `0` | `8443` |
  | `HTTP_MODE` | `on` | `redirect` |
  | Forwarded headers | from `TRUSTED_PROXIES` | ignored |

  Development mode (`-Pdevelopment`) defaults to `off`, with plain HTTP on as now, so `:server:run -Pdevelopment`
  still answers on `http://localhost:8080` and `https://localhost:8443`. Put it in a `ProxySettings` next to
  `TlsSettings`, read by both `createServer` and `module()`. If HTTPS is turned on in proxy mode, `HttpsRedirect`
  mustn't send people to `:8443`. *Source:* reverse proxy plan

  *Done:* `ProxySettings` in `components/tls/`; `TlsSettings.from` takes the mode for its blank defaults (a missing
  `tls.port`, as in tests, is still off). Behind a proxy `HttpsRedirect` is never installed, and `HTTP_MODE=redirect`
  is logged as ignored.
- [x] **`TRUSTED_PROXIES`.** A list of IPs and CIDR ranges. Forwarded headers are only used when
      `call.request.local.remoteAddress` is in it (our own small plugin in place of `XForwardedHeaders`, keeping
      the last proxy rule). `TRUST_PROXY=true` stays as a deprecated "trust any address" with a warning.
      **(decide)** the default: loopback plus private ranges (`10/8`, `172.16/12`, `192.168/16`, `fc00::/7`)
      works on any Docker network, but lets a LAN device fake its address if someone publishes the port; loopback
      only is safer but needs setting in every container. *Source:* [auth](server/auth.md#wrong-password-limit)
      *Done:* loopback plus private ranges (decided). `plugins/Proxy.kt` checks the connection's address against
      the list (`IpRange`, IPv4 and IPv6, no name lookups) before using the last `X-Forwarded-For` entry and
      `X-Forwarded-Proto`, and marks the call `ForwardedByProxy` for the diagnostics item. Bad entries are logged.
- [x] **Startup log.** In proxy mode, say HTTPS is the proxy's job and list the trusted ranges instead of the
      fingerprint lines. Warn when the time zone is UTC and `TZ` isn't set: `CounterDatabase`, `TicketDatabase`
      and `StatsDatabase` use `ZoneId.systemDefault()`, so "today" and the yearly number rollover would follow UTC.
      *Done:* `startupNotes` (`StartupLog.kt`), logged from `createServer`, with warnings for `TRUST_PROXY`, bad
      `TRUSTED_PROXIES` entries, an empty trusted list and UTC without `TZ`. `ProxyTest` covers the settings, the
      plugin over the test engine, and the log; checked against a real `:server:run` too.
- [x] **Diagnostics on the admin screen.** Add `proxy`, `clientAddress` (what the server sees for this request),
      `forwarded` (whether the forwarded headers were used) and `timeZone` to `ServerInfoResponse`, all with
      defaults so `API_VERSION` stays. Lets an admin see the proxy setup works: a phone showing `127.0.0.1` means
      it doesn't. *Done:* the four fields plus `trustedProxies`, filled from the request (`request.origin` and the
      `ForwardedByProxy` mark `plugins/Proxy.kt` sets). `ProxyTest` checks them through a trusted proxy, an untrusted
      one and direct. Showing them on the Server tab is Phase 9's "Admin Server tab" item.
- [ ] **Existing installs.** Changing the default turns off HTTPS on 8443, so devices pinned to `https://ip:8443`
      would stop connecting. **(decide)** stay direct when `PROXY` isn't set and `server/tls/` already holds a made
      certificate (with a warning to set `PROXY`), or skip this if there are no installs yet.
- [ ] **Develop and test outside a container.**
  - Desktop debug builds (`-DticketmanDebug=true`) default the server address to `http://localhost:8080`
    (`defaultServerAddress` is blank on desktop now), so `:server:run -Pdevelopment` plus `:composeApp:run`
    connects with nothing typed.
  - `scripts/dev_proxy.sh`: runs Caddy (installed on the machine, no Docker) in front of a dev server, using a
    `scripts/dev/Caddyfile` with `https://localhost:8444 { tls internal; reverse_proxy localhost:8080 }` and a
    small `request_body max_size` path to try a `413`. Starts the server with `PROXY=on HTTP_MODE=on
    TRUSTED_PROXIES=127.0.0.1,::1` and `-Pdevelopment`.
  - Testing the public certificate path: Caddy's local CA isn't in the JVM's trust store, so the client shows the
    trust dialog (the self-signed proxy case). For the no-dialog case, the script exports Caddy's root into
    `build/dev-truststore.p12`, and `:composeApp:run -PdevTrustStore=<file>` passes it as
    `javax.net.ssl.trustStore`, which `PinningTrustManager`'s system check reads.
  - `counter_e2e.py` takes the proxy address too (`python3 scripts/counter_e2e.py https://localhost:8444
    --insecure`) and adds a big photo upload, a backup download and a restore upload, since those are what
    proxies break.
  - Write this up in [getting started](getting-started.md) and [testing](testing.md), and add the commands to
    `CLAUDE.md`.
- [ ] **Tests.** `ProxySettings`/`TlsSettings` defaults in each mode, in development mode, and overrides winning;
      trusted proxies in and out of the list, IPv4 and IPv6 CIDRs, the last proxy rule, and old `TRUST_PROXY`;
      the new `ServerInfoResponse` fields.
- [ ] **Docs.** [Configuration](configuration.md): `PROXY`, `TRUSTED_PROXIES`, `TZ`, the proxy mode defaults,
      `TRUST_PROXY` deprecated. [Auth](server/auth.md): the trusted proxy rule. [Decisions](decisions.md): a
      "Behind a reverse proxy by default" entry (why, the trusted range costs), and the "Its own certificate"
      entry reworded as the direct option. `CLAUDE.md`: the config paragraph and the HTTPS bullet.

## Phase 9: The client behind a proxy

With a public certificate the system check in `PinningTrustManager` passes and the trust dialog never shows, so
the user types the address and logs in. These cover what goes wrong around that.

- [ ] **Address hints.** The placeholder (`ServerAddressField`) and Settings description say `192.168.1.20:8443`.
      Change them to the proxy form (`tickets.myshop.com`), with the IP and port as the no-proxy example.
- [ ] **Connecting by the wrong name.** Typing the IP when the certificate is for a domain passes the trust check
      but fails `PinningHostnameVerifier`. Nothing reaches `ServerTrust.refuse`, so it shows as "unreachable".
      Record the names the certificate covers and say "This server's certificate is for `tickets.myshop.com`,
      connect with that name".
- [ ] **Errors the proxy makes.** These come back as HTML. Map `413` to "Bigger than the proxy allows" (photos,
      restore) and `502`/`503`/`504` to "The proxy is up but the TicketMan server isn't answering", and let
      `setUrl` tell that apart from unreachable.
- [ ] **Admin Server tab.** When `proxy` is on, show "HTTPS: handled by reverse proxy", the client address, whether
      it was forwarded, and the time zone. Hide the fingerprint, expiry and Renew rows, which describe a
      certificate devices don't see. Add render tests for both modes.
- [ ] **Trust dialog wording.** It now only appears for a self-signed certificate, which in proxy mode isn't on the
      admin screen. Say "the fingerprint your administrator gave you" when it can't be checked there.
- [ ] **Timeouts.** Check the backup create call and `transferTimeout()` uploads against a proxy's read timeout
      (nginx's default is 60s). If making a backup can take longer, make it start then poll.
- [ ] **Android 7.0.** `minSdk` is 24, which lacks Let's Encrypt's root (added in 7.1.1).
      **(decide)** raise `minSdk` to 25, or say so in [platforms](client/platforms.md).
- [ ] **Docs.** [Networking](client/networking.md): public certificates, the hostname error, proxy errors.
      [Settings](ui/screens/settings.md) and [admin](ui/screens/admin.md) for the new wording and rows.

## Phase 10: Docker image

- [ ] **Build the server without the Android SDK.** The Gradle build loads the Android plugin, which likely fails
      in a plain container. **(decide)** build on the host or in CI and copy `server/build/install/server` into the
      image, or add a property that leaves out `androidApp` and the Android targets so the build can run inside
      Docker. Do this first; the rest needs it.
- [ ] **`server/Dockerfile`.** `eclipse-temurin` JRE, amd64 and arm64. Runs `bin/server` from
      `:server:installDist -PwithWeb`. `DATA_PATH=/data` as a volume, `PORT=8080` exposed, `PROXY=on`,
      `JAVA_OPTS=-XX:MaxRAMPercentage=75`, a non-root user owning `/data`, and a `HEALTHCHECK` on `/api/ping`
      (the JRE image has no curl). Check `docker stop` shuts H2 down cleanly.
- [ ] **`docker-compose.yml` example** with Caddy (public certificate with no setup): volumes for `/data` and
      `/backups` (`BACKUP_DIR`), `TZ`, `restart: unless-stopped`, the server on `expose` rather than `ports`, and a
      named network with a fixed subnet that `TRUSTED_PROXIES` is set to exactly. A note on joining Traefik's or
      Nginx Proxy Manager's network instead.
- [ ] **Restart from the admin screen.** A restore needs a restart, which an admin can't do in a container.
      `POST /api/admin/restart` (admin, audited) answers `202` then exits non-zero so the restart policy brings it
      back. Only offered when `RESTART_ON_EXIT=true` (the image sets it). The exit goes through something tests
      can replace. Add the button next to a waiting restore, and the path to `ApiRoutes.kt` and `docs/api/admin.md`.
- [ ] **Smoke test.** `scripts/docker_smoke.sh`: `docker compose up` with Caddy on its local CA, wait for the health
      check, run `counter_e2e.py` through it, restore with the restart button, check the data came back.
- [ ] **Docs.** [Operations](operations.md): First start becomes `docker compose up`, open the address, log in as
      `admin` (password from `ADMIN_PASSWORD` or `docker logs`); a "Behind another proxy" section with nginx
      settings (`client_max_body_size`, `proxy_request_buffering off` for restore and upload, `proxy_buffering
      off` for backup downloads, longer timeouts, forwarded headers, query strings kept out of the access log
      since the backup `?token=` is a credential); the HTTPS section becomes "Without a proxy". Add the Docker
      build to `CLAUDE.md`.

## Phase 11: Android, and other machines

Last, on a machine that can build and run them: the Android SDK tools Gradle downloads are x86, so ARM Linux
can't build the APK, and there's no emulator for it.

- [ ] **Run Android end to end** on a device: login, records, counter, photos, printing, kiosk. Fix what breaks and
      update [Platforms](client/platforms.md). Needs compileSdk 37 and an AAPT2 that runs on the build machine.
      *Source:* [platforms](client/platforms.md#known-gaps)
- [ ] **Kiosk lockdown on Android.** Use Android's lock task mode (`startLockTask()` when kiosk mode turns on,
      `stopLockTask()` when an admin leaves it) so a kiosk can't be swiped out of. Without being the device owner
      Android asks the user to pin the screen first; say so on the kiosk screen.
      *Source:* [kiosk](ui/screens/kiosk.md#known-gaps)
- [ ] **Check `AppDir` on macOS and Windows.** `AppDirTest` pins down the per OS folders
      (`~/Library/Application Support/TicketMan`, `%APPDATA%\TicketMan`), but only Linux has been run. Start the
      desktop client and a server without `DATA_PATH` on each and check where the files go.
      *Source:* `AppDir.jvm.kt`'s old `TODO`s

## Phase 12: Added ideas
- [ ] Import data into the database from a file (a CSV of customers or inventory from another system).
- [ ] Assign existing tickets to a customer from the customer dialog.
- [ ] Hours worked and parts used recorded on a ticket as it's worked on, feeding its invoice.
- [ ] Allow exporting invoices (CSV or PDF) for the books, add a PDF option from counter so it can be emailed.
- [ ] Option to make a new customer from the ticket dialog, the way check in does.
- [ ] Instead of refresh buttons on admin screen, record and others that may need to refresh, Lets 
      make the app periodically check for updates from the server over an adjustable interval.

## Ideas

Features that came up as `TODO` comments but were never started. Not planned; pick one up, or drop it, when
deciding what's next.
- Photos on every record type, not just notes and inventory. Customers, tickets and users already have the shape.
- A reorder prompt when an item's stock drops to a set level, at login or when it happens.

## Rules to keep

Not work to do, but the gotchas and must-dos the docs list. Keep these in mind on every change; most are also on
the PR template.

**Database and server**
- New columns need a default or must be nullable; add the table to `migratedTables` (or the column to
  `addUserColumns()` for `user`). Avoid SQL reserved words as column names. ([Migrations](server/migrations.md))
- Fields the server owns (a ticket's number, pickup time, stock) stay out of `update`.
- When deleting a record, take its notes first, clear join tables without a cascade, then delete. ([Database](server/database.md#gotchas))
- An outer transaction that adds a ticket or invoice wraps itself in `tickets.whileNumbering { invoices.whileNumbering { ... } }`, ticket first. ([Numbering](server/numbering.md))
- Never delete photo files directly; mark rows deleted and let the sweep do it. ([Photos](server/photos.md#gotchas))
- Stream big bodies (`receiveChannel().copyToFile`, `respondFile`); never `receive<ByteArray>()` or `respondBytes`.
- In a multi-step `transaction { }`, write the audit entry inside it as the last step, so it rolls back with the
  change. ([Audit log](server/audit-log.md#writing-an-entry))
- Check uploads, downloads and restore against a real `./gradlew :server:run`; the test engine isn't Netty.
- Add fields to the server before any client sends them; the server refuses unknown fields.

**Client**
- A new seed field goes in `fields()`, or edits never make the seed dirty.
- A new repository is constructed in `RemoteHandler` **and** added to its `repositories`.
- A new record type's database goes in `LocalDatabase.recordDatabases`. ([Add a record type](how-to/add-an-entity.md))
- New paths go in shared's `ApiRoutes.kt`, never as strings in a route file or repository. Bump `API_VERSION` when
  older clients couldn't use the change.
- Don't turn a failed read into an empty list a save could write back.
- Write actions check `canEdit()` and their buttons hide for people who can't use them; the server checks again.
- Show ticket numbers with `Ticket.label`, which works before the ticket has a number.
- Mock engines in tests answer `SERVER_PING_PATH` with `SERVER_PING_BODY`, and send JSON headers. ([Testing](testing.md#client-tests))

**UI**
- Screens style only through the kit; new UI goes in `ui/kit` with a gallery entry first. ([Kit rules](ui/kit-rules.md))
- Glass for floating layers only; anything glass sits over needs `glassSource()`.
- Numbers (ticket numbers, money, PINs, SKUs) use the `numeric*` styles.
- The counter's F-keys and Escape are taken by the counter; fields there can't use them.

**Docs**
- Fix the doc page in the same PR as the change; run `check_api_docs.py` after route changes and
  `check_doc_links.py` after moving pages (CI runs both). ([STYLE.md](STYLE.md#keeping-them-current))
- Add a [decisions](decisions.md) entry for a choice someone might want to undo later.
