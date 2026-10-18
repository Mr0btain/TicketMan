# Documentation plan

Developer documentation for TicketMan, written in phases. Tick a box when its page is merged. Each phase (or each
part of Phase 4) is sized for one session and one PR.

Order: 0 → 2 → 1 → 3 → 4a–c → 5 → 6 → 4d → 7 → 8 → 9. The architecture pages come early because almost everything
else links to them. The API reference comes late because it is large and mechanical, and the routes may still change.

Audience: developers working on TicketMan. There is no guide for shop staff.

## Phase 0: Groundwork

- [x] `docs/README.md`: index of every page
- [x] `docs/STYLE.md`: style guide and page template
- [x] `README.md`: rewritten with a screenshot, quick start, accurate status and links into `docs/`
- [x] `CLAUDE.md` links to `docs/` for detail and stays a short summary

## Phase 1: Getting started and running a server

- [x] `getting-started.md`: tools to install, building and running each target, hot reload
- [x] `configuration.md`: `application.yaml`, env vars, dev mode, seeding test data
- [x] `operations.md`: first start and the admin password, data folder layout, backup and restore, photo sweep,
      approving stations and setting up kiosks

## Phase 2: Architecture overview

- [x] `architecture/overview.md`: what each module is for and how they depend on each other
- [x] `architecture/entity-layers.md`: the six layers every entity is mirrored across, followed for Ticket
- [x] `architecture/request-lifecycle.md`: one save from the UI to the database and back, and how errors return

## Phase 3: The `shared` module

- [x] `shared/domain-models.md`: `MainItem`, creation time from the `Uuid`, notes, `StoredImage`, each entity
- [x] `shared/results-and-errors.md`: `Result`, the error enums, `networkErrorTryCatch`, `appLogTryCatch`
- [x] `shared/money.md`: `Money` in cents and `InvoiceMath`
- [x] `shared/dtos-and-platform.md`: request and response DTOs, caches, `AppLogger`, `AppDir`, `Platform`

## Phase 4: Server

### 4a. Database layer
- [x] `server/database.md`: `Database<T>`, `dbQuery`/`dbWrite`, transactions, notes on records
- [x] `server/migrations.md`: `migratedTables`, `addUserColumns`, reserved words, data fixes
- [x] `server/numbering.md`: ticket and invoice numbers

### 4b. Auth and users
- [x] `server/auth.md`: JWT providers, per device sessions, token lifetimes, password hashing
- [x] `server/users-and-accounts.md`: statuses, access levels, account requests, invites, setup
- [x] `server/pins-and-stations.md`: PINs and lockout, stations, PIN sessions, kiosk mode

### 4c. Services
- [x] `server/counter.md`: check-in, pickup, invoices and tax
- [x] `server/photos.md`: `ImageStore`, processing, variants, the pending → linked → deleted lifecycle, sweep, caching
- [x] `server/backup-restore.md`: backup and restore internals
- [x] `server/audit-log.md`: what gets logged and how

### 4d. API reference (one page per route file, checked against the routes with a script)
- [ ] `api/README.md`: conventions (auth providers, status codes, bodies)
- [ ] `api/auth.md`
- [ ] `api/user.md`
- [ ] `api/admin.md`
- [ ] `api/counter.md`
- [ ] `api/invoice.md`
- [ ] `api/station.md`
- [ ] `api/shop.md`
- [ ] `api/image.md`
- [ ] `api/customer.md`
- [ ] `api/ticket.md`
- [ ] `api/inventory.md`
- [ ] `api/notes.md`

## Phase 5: Client core

- [x] `client/networking.md`: `RemoteHandler`, repositories, token refresh, `credentialFallback`
- [x] `client/state.md`: `MainViewModel` and each handler, what state each owns, how they are connected
- [x] `client/records.md`: seeds, caches, `OffsetPager`, syncing notes
- [x] `client/storage.md`: `LocalStorage` and `KeyValueStore`
- [x] `client/transfers-and-photos.md`: large transfers, `ImageClient`, Coil
- [x] `client/printing.md`: `PrintDocument` and each platform's printer
- [x] `client/platforms.md`: every `expect`/`actual`

## Phase 6: UI

- [ ] `ui/kit.md`: theme tokens, palettes and fonts, glass versus solid, component catalog with gallery images
- [ ] `ui/kit-rules.md`: what `UiKitRulesTest` checks and why
- [ ] `ui/screens/navigation.md`
- [ ] `ui/screens/records.md`
- [ ] `ui/screens/counter.md`
- [ ] `ui/screens/kiosk.md`
- [ ] `ui/screens/admin.md`
- [ ] `ui/screens/settings.md`
- [ ] `ui/screens/login.md`

## Phase 7: Testing

- [ ] `testing.md`: server test helpers and their pitfalls, `MockEngine`, render tests, the end to end script

## Phase 8: How-to guides

- [ ] `how-to/add-a-field.md`
- [ ] `how-to/add-an-entity.md`
- [ ] `how-to/add-a-route.md`
- [ ] `how-to/add-a-column.md`
- [ ] `how-to/add-a-kit-component.md`
- [ ] `how-to/add-a-theme-or-font.md`
- [ ] `how-to/add-a-platform-actual.md`
- [ ] `how-to/add-a-printable-document.md`

## Phase 9: Code comments and upkeep

- [ ] KDoc on the base classes (`Database`, `RemoteRepository`), the services and the handlers
- [ ] Dokka (optional): generated API site from the KDoc
- [ ] "Docs updated?" item on the PR checklist
- [ ] `decisions.md`: why H2, why per device sessions, why photo deletion waits a day, and other choices
