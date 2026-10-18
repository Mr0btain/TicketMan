# TicketMan developer docs

Start with [Getting started](getting-started.md), then read the [architecture overview](architecture/overview.md).

## Getting going

- [Getting started](getting-started.md): tools, building and running each target, tests, hot reload
- [Configuration](configuration.md): server settings and the env vars that override them, dev mode, test data
- [Running a server](operations.md): first start, the data folder, backup and restore, stations and kiosks

## Architecture

- [Overview](architecture/overview.md): the modules, what each is for and how they fit together
- [Entity layers](architecture/entity-layers.md): the six layers every record type is built from, followed for Ticket
- [Request lifecycle](architecture/request-lifecycle.md): one save from a button press to the database and back

## Shared module

- [Domain models](shared/domain-models.md): `MainItem`, ids and creation time, every record type
- [Results and errors](shared/results-and-errors.md): `Result`, `Error`, the try/catch helpers, logging
- [Money](shared/money.md): cents everywhere, `Money` and `InvoiceMath`
- [DTOs, caches and platform helpers](shared/dtos-and-platform.md): request and response bodies, `LruCache`, `AppDir`

## Server

- [Database layer](server/database.md): `LocalDatabase`, `Database<T>`, `dbQuery`/`dbWrite`, notes on records
- [Migrations](server/migrations.md): adding tables, columns and indexes safely; data fixes
- [Ticket and invoice numbers](server/numbering.md): how numbers are handed out without clashing
- [Authentication and sessions](server/auth.md): login, tokens, per device sessions, the auth providers
- [Users and accounts](server/users-and-accounts.md): status and access level, requests, invites, first login setup
- [PINs and stations](server/pins-and-stations.md): station registration, PIN login, kiosk mode
- [Counter](server/counter.md): check-in, invoices, pickup, the queue
- [Photos](server/photos.md): upload, processing, linking, serving, the sweep
- [Backup and restore](server/backup-restore.md): building, downloading, staging and applying
- [Audit log](server/audit-log.md): what's logged and how to log something new

## Client

- [Networking](client/networking.md): `RemoteHandler`, the two HTTP clients, connecting, token refresh, repositories
- [App state and handlers](client/state.md): start up, `MainViewModel`, what each handler owns
- [Records](client/records.md): pagers, caches, selection, seeds and saving
- [Storage](client/storage.md): `LocalStorage`, what's kept on the device
- [Transfers and photos](client/transfers-and-photos.md): streaming big files, uploading and showing photos
- [Printing](client/printing.md): print documents, templates, each platform's printer
- [Platforms](client/platforms.md): entry points, every `expect`/`actual`, what's unfinished

## Still to come

The API reference, the UI kit and screens, testing and how-to guides. [PLAN.md](PLAN.md) lists every planned page and
which ones are done.

## About these docs

[STYLE.md](STYLE.md) covers how pages are written and the template new pages start from.
[`CLAUDE.md`](../CLAUDE.md) at the repo root is a dense summary of the same ground, kept for AI assistants.
