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
```

There is no lint task configured. Test coverage is minimal (server only). `testApplication` doesn't load `application.yaml`, so server tests use the helpers in `server/src/test/.../TestServer.kt`: `createTestDataDir()`, `configureTestServer(dataDir)` (supplies `env.dataPath` and `jwt.*` via `MapApplicationConfig`), and `cleanupTestDataDir(dataDir)` in `@AfterTest` (shuts H2 down, then deletes the dir). `AuthTest.kt` shows the login → bearer request → refresh flow.

Server config lives in `server/src/main/resources/application.yaml`, overridable by env vars: `PORT`, `SECRET` (JWT secret), `NAME`, `DATA_PATH` (where the H2 DB is created; falls back to the platform `AppDir`). `Application.module()` contains a `generateTestData` flag that seeds the DB with mock tickets/customers/inventory/users.

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
4. `server/.../routing/<Entity>Route.kt` — mounted under `/api/data/<entity>` (users under `/api/user`, auth under `/api/auth`) in `Routing.kt`
5. `composeApp/.../network/database/<Entity>RemoteRepositoryImpl.kt` — extends abstract `RemoteRepository<T>`, whose route paths (`/add`, `/fetch`, `/get`, `/update`, `/remove/{id}`, `/search`, `/get/notes/{id}`, ...) must match the server route
6. `composeApp/.../data/<entity>/` — client-side `*Cache` and `*Seed` (mock data)

### Server
`Application.module()` → `configureDatabase(path)` builds a `LocalDatabase` (H2 file DB at `<path>/server/database/`) exposing one `*DatabaseImpl` per entity plus `TokenDatabase`. `JwtService`/`UserService` handle login and access/refresh tokens. `plugins/Security.kt` registers three JWT auth providers keyed to `AccessLevel`: default (`NORMAL`), `"restricted"`, and `"admin"`; routes wrap handlers in `authenticate { }` / `authenticate("admin") { }`. `routing/Helpers.kt#getIdentifierFromToken()` extracts the calling user's UUID from the JWT. Server-side X25519/Ed25519 encryption lives in `encryption/ServerEncryptionHandler.kt` (the client-side counterpart is currently commented out).

### Client
- DI: Koin, started in each platform entry point via `initKoin()` with `viewModelModule` and `dataModule`.
- `MainViewModel` is the app-lifetime state holder: owns the `RemoteHandler`, per-entity caches and `OffsetPager`s, and settings.
- `RemoteHandler` builds a Ktor client (with bearer-token auth/refresh using `LocalStorage`) and holds one remote repository per entity; `setUrl()` verifies the server is reachable and propagates the base URL to every repo. Repositories no-op when `serverUrl` is blank.
- `LocalStorage` is currently an in-memory stub (hard-coded dev credentials) — not real persistence.
- UI: `ui/screens/main/expanded/` (desktop/wide layouts, Panel → ListPane + InfoPane per entity) vs `ui/screens/main/mobile/` (only customer started), chosen by window size class. Navigation in `ui/screens/navigation/`.
- Platform specifics go through `expect`/`actual` (`getHttpEngine`, `IsDebug`, `InitAppDir`, `Pdf`, `VerticalScrollBar`, `AppLogger`).

### Conventions
- Logging uses `AppLogger.{e,d,i,m}(tag, message)` with hierarchical string tags declared as constants at the top of each class, e.g. `"$TAG:GetById"`. Follow this pattern in new code.
- Errors: wrap risky calls in `appLogTryCatch(TAG) { ... }` (server/shared) or `networkErrorTryCatch(TAG) { ... }` (client network), and return `Result`/`SimpleResult` rather than throwing.
- Server DB access is inside Exposed `transaction { }` blocks.
