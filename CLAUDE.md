fi# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

TicketMan is a ticket-tracking app for repair shops. It is a Kotlin Multiplatform project: a Compose Multiplatform client (desktop is the primary, most complete target) talking over HTTP to a Ktor server backed by an H2 database via Exposed. Package root everywhere is `org.mr0btain.ticketman`.

## Commands

Use `./gradlew` (Git Bash) or `.\gradlew.bat` (PowerShell).

```bash
./gradlew :composeApp:run                    # Run desktop client (main: org.mr0btain.ticketman.MainKt)
./gradlew :composeApp:hotReloadDesktopDev    # Desktop with Compose hot reload (see .run/ config)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  # Web client
./gradlew :androidApp:assembleDebug          # Android APK
./gradlew :server:run                        # Ktor server (Netty, port $PORT or 8080)
./gradlew :server:run -Pdevelopment          # Server with io.ktor.development=true

./gradlew :server:test                                   # Server tests
./gradlew :server:test --tests "org.mr0btain.ticketman.ApplicationTest"  # Single test class
./gradlew :shared:jvmTest                                # Shared module tests (JVM)
./gradlew :composeApp:desktopTest                        # Client tests (commonTest + desktopTest)

# End to end: start a dev server, then walk check-in -> pickup over HTTP
PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
python3 scripts/counter_e2e.py http://localhost:8098
```

There is no lint task configured. Server tests live in `server/src/test`, client tests in `composeApp/src/commonTest` (network repos use Ktor's `MockEngine`; handlers take a `RemoteHandler` built on one) and `composeApp/src/desktopTest` (Java2D printing, ZXing-checked QR codes, UI renders, and `UiKitRulesTest`). `KitGalleryRenderTest` and `ScreenRenderTest` draw the component gallery and the main screens with sample data to PNGs in `composeApp/build/kit-gallery` and `composeApp/build/screen-renders`, which is the quickest way to look over a UI change without running the app. `testApplication` doesn't load `application.yaml`, so server tests use the helpers in `server/src/test/.../TestServer.kt`: `createTestDataDir()`, `configureTestServer(dataDir)` (supplies `env.dataPath` and `jwt.*` via `MapApplicationConfig`), and `cleanupTestDataDir(dataDir)` in `@AfterTest` (shuts H2 down, then deletes the dir). `AuthTest.kt` shows the login → bearer request → refresh flow.

Server config lives in `server/src/main/resources/application.yaml`, overridable by env vars: `PORT`, `SECRET` (JWT secret), `NAME`, `DATA_PATH` (where the H2 DB is created; falls back to the platform `AppDir`), `ADMIN_PASSWORD` (password for the `admin` user created on start when no admin exists; a random one is logged if unset). `Application.module()` contains a `generateTestData` flag that seeds the DB via `seedTestData()`; on a server started with `-Pdevelopment` admins can also seed from the admin screen.

## Modules

- **`shared`** — KMP library (android, jvm, wasmJs; iOS targets commented out). Holds everything both client and server need: `@Serializable` domain models (`data/`), request/response DTOs (`server/request`, `server/response`), the `Result<D, E: Error>` type + per-domain error enums (`error/`), caches, and `expect` declarations (`AppLogger`, `AppDir`, `Platform`) with per-platform `actual`s.
- **`composeApp`** — KMP Compose UI + client logic, built as an Android *library* plus `jvm("desktop")` and `wasmJs`. iOS targets are commented out.
- **`androidApp`** — thin Android application wrapper around `composeApp` (`MainActivity`).
- **`server`** — Ktor JVM app (`EngineMain`, module `ApplicationKt.module`).
- **`iosApp`** — Xcode project; currently not wired up since iOS targets are disabled.

Dependency versions and bundles (`ktorServer`, `ktorClient`, `exposed`, `koin`, `fileKit`) are in `gradle/libs.versions.toml`. Projects are referenced via type-safe accessors (`projects.shared`).

## Architecture

### Domain model pattern
Each main entity (Customer, Ticket, Inventory, User) extends `MainItem` (shared), identified by a `Uuid` whose embedded timestamp is used as the creation time (`identifier.createdAt()`). Entities have attached `Note`s, and notes have `StoredImage`s. This shape is mirrored identically on each layer, so adding a field or entity usually means touching all of them:

1. `shared/.../data/<entity>/` — serializable model
2. `server/.../database/tables/<entity>/` — Exposed DAO tables/entities (plus `<Entity>NoteTable`, join tables), registered in `LocalDatabase.initDatabase()`'s `SchemaUtils.create(...)`
3. `server/.../database/<Entity>DatabaseImpl.kt` — extends abstract `Database<T : MainItem>` (CRUD, paging, notes, images, search)
4. `server/.../routing/<Entity>Route.kt` — mounted under `/api/data/<entity>` (users under `/api/user`, auth under `/api/auth`, admin dashboard under `/api/admin`, shop profile under `/api/shop`) in `Routing.kt`
5. `composeApp/.../network/database/<Entity>RemoteRepositoryImpl.kt` — extends abstract `RemoteRepository<T>`, whose route paths (`/add`, `/fetch`, `/get`, `/update`, `/remove/{id}`, `/search`, `/get/notes/{id}`, ...) must match the server route
6. `composeApp/.../data/<entity>/` — client-side `*Cache` and `*Seed` (mock data)

### Server
`Application.module()` → `configureDatabase(path)` builds a `LocalDatabase` (H2 file DB at `<path>/server/database/`) exposing one `*DatabaseImpl` per entity plus `TokenDatabase`. `JwtService`/`UserService` handle login and access/refresh tokens. Passwords and PINs are PBKDF2-hashed by `PasswordHasher` (legacy plain-text passwords are upgraded on login) and users are blanked with `User.withoutPassword()` before leaving the server. Write routes log to `AuditLogDatabase` via `call.audit(...)`.

- **Sessions**: one row per device in `TokenTable` (`TokenDatabase`/`StoredSession`). Tokens carry a `sid` claim and an access token is valid only while that session row exists for its user. The client sends a stable `deviceId`, so logging in again on the same device replaces its session; other devices are untouched.
- **Users**: `UserStatus` (PENDING/ACTIVE/DEACTIVATED) is separate from `AccessLevel`; `User.canAccess(level)` is the check (active, setup finished, level high enough). `AccessLevel.NONE` only exists for migrating old data. New accounts come from requests (`/api/auth/request-account`, then admin approve) or invites (setup code used as the password), see `AccountService`. Users with `needsSetup` can only reach routes under the `"setup"` auth provider.
- **PINs and stations**: 6 digit PINs (`PinRules`) lock after 5 wrong tries. PIN login (`/api/auth/pin`) needs an admin-approved station's id + secret (`StationDatabase`); PIN sessions last 12h and each one ends the previous PIN session on that station. Stations can be put in kiosk mode with an idle timeout.
- **Money**: always `Long` cents (`Money` in shared). Invoices (`InvoiceDatabase`/`InvoiceService`) are one per ticket; totals come from shared `InvoiceMath`, tax from the shop profile. `POST /api/counter/pickup` pays the invoice, takes parts from stock and closes the ticket together; `POST /api/counter/check-in` makes the ticket, links, deposit and signature together (`CheckInService`).
- **Numbers**: tickets `26-00000042` and invoices `INV-26-00000042`, per year, assigned under a lock with a unique index.
- **Access**: read routes use `authenticate("restricted")` so Restricted users can look; writes use the default (NORMAL) provider.
- **Migrations**: `LocalDatabase.initDatabase()` creates tables, then adds missing columns only for tables in `migratedTables` (add a table there when you give it a column; new columns need a default or nullable). Columns on the `user` table go in `addUserColumns()` because H2 can't list that table's columns (reserved word). Avoid reserved column names (`condition` is stored as `device_condition`). Data fixes run after (`backfillNumbers`, `migrateDeactivatedUsers`, `clearLegacySessions`, `migrateLegacyPrices`). `plugins/Security.kt` registers three JWT auth providers keyed to `AccessLevel`: default (`NORMAL`), `"restricted"`, and `"admin"`; routes wrap handlers in `authenticate { }` / `authenticate("admin") { }`. `routing/Helpers.kt#getIdentifierFromToken()` extracts the calling user's UUID from the JWT. Server-side X25519/Ed25519 encryption lives in `encryption/ServerEncryptionHandler.kt` (the client-side counterpart is currently commented out).

### Client
- DI: Koin, started in each platform entry point via `initKoin()` with `viewModelModule` and `dataModule`.
- `MainViewModel` is the app-lifetime state holder: owns the `RemoteHandler`, per-entity caches and `OffsetPager`s, settings, the logged-in `currentUser`/`isAdmin`/`canEdit`, and per-screen handlers: `AdminHandler` (admin screen), `CounterHandler` (counter: search, queue, ticket card, check-in, pickup, invoices, printing) and `KioskHandler` (kiosk mode, PIN lock, idle timer). Handlers take `canEdit` and refuse changes for Restricted users as well as hiding the buttons.
- Counter screen is `ui/screens/counter/` (the old Dashboard destination). Kiosk mode (`ui/screens/kiosk/`) replaces the whole app with the counter and a PIN lock screen; it turns off `RemoteHandler.credentialFallback` so a locked kiosk can't log the owner back in, and sets `ui/AppWindow.fullscreen`, which each platform entry point applies.
- Printing: `components/print/PrintTemplates` builds a platform-neutral `PrintDocument`; `printDocument()` is expect/actual (desktop: Java2D `PrinterJob`; web: hidden iframe of `HtmlPrintRenderer`; Android: WebView + `PrintManager`, needs `AndroidPrintHost.attach` from `MainActivity`). `QrCode` is an in-house encoder.
- Times on screen go through `components/utils/Formatting.kt` (local time via kotlinx-datetime).
- `RemoteHandler` builds a Ktor client (with bearer-token auth/refresh using `LocalStorage`) and holds one remote repository per entity; `setUrl()` verifies the server is reachable and propagates the base URL to every repo. Repositories no-op when `serverUrl` is blank.
- `LocalStorage` persists settings/tokens through a `KeyValueStore` (KSafe-backed; `InMemoryKeyValueStore` in tests).
- UI: every screen is built from the UI kit (below). `ui/screens/navigation/AppShell.kt` frames the app: a glass nav rail on wide windows (`WideAppShell`, with the user menu and log out at its foot) or a glass bottom bar on phones (`PhoneAppShell`), the backdrop behind everything and the snackbar host. The dashboard screen (`ui/screens/dashboard/components/`) is one `RecordsLayout` per entity: toolbar (`ui/screens/shared/PanelTopBar`), list, and details, stacking on narrow windows.
- Platform specifics go through `expect`/`actual` (`getHttpEngine`, `IsDebug`, `InitAppDir`, `Pdf`, `TmVerticalScrollbar`, `AppLogger`, `saveFile`, `printDocument`).
- `androidApp` currently needs compileSdk 37 to build (FileKit 0.16 requires it); `shared` and `composeApp` compile for Android with `:composeApp:compileAndroidMain`.

### UI kit
All shared UI lives in `composeApp/src/commonMain/.../ui/kit/`, prefixed `Tm`: `theme/` (tokens), `foundation/` (backdrop, glass and solid surfaces, hover/press/focus, control sizes, scrollbar), `buttons/`, `inputs/` (text, money, PIN, password, signature), `selection/` (dropdown, menu, segmented, chips, toggles), `search/`, `containers/` (panel, dialog and confirm dialog, page and pane headers, list rows, tabs, lazy column), `feedback/` (badges for ticket status, priority, access, notices, snackbar, empty and loading states), `data/` (stat tiles, tables, money and ticket number text, images), `navigation/` (rail, top bar, bottom nav, stepper) and `gallery/` (the component gallery, opened from Settings > Appearance, and in the nav on debug builds).
- Screens get colors, type, shapes and spacing from `TmTheme.colors/typography/shapes/spacing` and never hard code them. `UiKitRulesTest` fails on `Color(0x`, `fontSize =`, `RoundedCornerShape(`, `MaterialTheme.` or Material component imports (other than `Text`, `Icon`, `SnackbarHostState`) outside `ui/kit`. Need something new? Add it to the kit and the gallery, then use it.
- Themes: `TmPalettes` (Bench default, Copper, Mono), each with dark and light `TmColors`; add one by adding a `TmPalette` to `TmPalettes.all`. Fonts: `TmFonts` (Barlow default, Atkinson Hyperlegible, System); add one by putting its `.ttf`s in `composeResources/font` and a `TmFont` in `TmFonts.all`. Numbers (ticket and invoice numbers, money, PINs, SKUs) always use the `numeric*` styles (JetBrains Mono). The picked theme, font, dark mode and reduce transparency are saved in `LocalStorage` and exposed by `MainViewModel`.
- Glass: `TmBackdrop` owns a Haze state and draws the background glow; `GlassSurface`/`Modifier.glass()` blur whatever sits behind them, `Modifier.glassSource()` marks content (like a scrolling list) for glass on top to blur. Glass is for floating layers only (rail, bars, dialogs, menus, snackbar); cards and lists use solid `TmSurface`. Reduce transparency turns glass solid.
- Ticket statuses show as `StatusBadge` with the wording in `TicketStatusType.label` ("Ready for pickup" for DONE); tones come from `TmColors.tone(...)` so a state keeps its color across themes.

### Conventions
- Logging uses `AppLogger.{e,d,i,m}(tag, message)` with hierarchical string tags declared as constants at the top of each class, e.g. `"$TAG:GetById"`. Follow this pattern in new code.
- Errors: wrap risky calls in `appLogTryCatch(TAG) { ... }` (server/shared) or `networkErrorTryCatch(TAG) { ... }` (client network), and return `Result`/`SimpleResult` rather than throwing.
- Server DB access is inside Exposed `transaction { }` blocks.
