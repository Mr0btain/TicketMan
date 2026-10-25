# TicketMan developer docs

Start with [Getting started](getting-started.md), then read the [architecture overview](architecture/overview.md).

## Getting going

- [Getting started](getting-started.md): tools, building and running each target, tests, hot reload
- [Configuration](configuration.md): server settings and the env vars that override them, dev mode, test data
- [Running a server](operations.md): first start, the data folder, backup and restore, stations and kiosks
- [Releasing](releasing.md): the version, building each package, the release workflows and their secrets

## Architecture

- [Overview](architecture/overview.md): the modules, what each is for and how they fit together
- [Entity layers](architecture/entity-layers.md): the six layers every record type is built from, followed for Ticket
- [Request lifecycle](architecture/request-lifecycle.md): one save from a button press to the database and back

## Shared module

- [Domain models](shared/domain-models.md): `MainItem`, ids and creation time, every record type
- [Results and errors](shared/results-and-errors.md): `TmResult`, `Error`, the try/catch helpers, logging
- [Money](shared/money.md): cents everywhere, `Money` and `InvoiceMath`
- [DTOs, caches and platform helpers](shared/dtos-and-platform.md): request and response bodies, `LruCache`, `AppDir`

## Server

- [Database layer](server/database.md): `LocalDatabase`, `Database<T>`, `dbQuery`/`dbWrite`, notes on records
- [Migrations](server/migrations.md): adding tables, columns and indexes safely; data fixes
- [Ticket and invoice numbers](server/numbering.md): how numbers are handed out without clashing
- [Authentication and sessions](server/auth.md): login, tokens, per device sessions, the auth providers
- [Users and accounts](server/users-and-accounts.md): status and access level, requests, invites, first login setup
- [PINs and stations](server/pins-and-stations.md): station registration, PIN login, kiosk mode
- [Counter](server/counter.md): check-in, invoices, pickup, sales, refunds, store credit and gift cards, the queue
- [Purchasing](server/purchasing.md): suppliers, purchase orders, receiving into stock, tickets waiting on parts
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

## UI

- [UI kit](ui/kit.md): theme tokens, palettes and fonts, glass versus solid, every component, the gallery
- [Kit rules](ui/kit-rules.md): what `UiKitRulesTest` checks, and adding a component, theme or font
- [App shell and navigation](ui/screens/navigation.md): the shells, destinations, adding a screen
- [Records screen](ui/screens/records.md): the shared records layout, dialogs, view only
- [Counter screen](ui/screens/counter.md): views, shortcuts, check-in wizard, pickup, sales
- [Purchasing screen](ui/screens/purchasing.md): orders, receiving, suppliers
- [Kiosk](ui/screens/kiosk.md): the lock screen, PIN pad, idle lock, leaving kiosk mode
- [Admin screen](ui/screens/admin.md): every tab and what it's backed by
- [Settings screen](ui/screens/settings.md): what each setting does, adding one
- [Login](ui/screens/login.md): connecting, trusting a server, logging in, first login setup

## API reference

- [Overview](api/README.md): auth providers, bodies, status codes, and the script that checks these pages
- [Auth](api/auth.md), [Users](api/user.md), [Admin](api/admin.md), [Counter](api/counter.md),
  [Invoices](api/invoice.md), [Stations](api/station.md), [Shop profile](api/shop.md), [Photos](api/image.md)
- [Customers](api/customer.md), [Tickets](api/ticket.md), [Inventory](api/inventory.md), [Notes](api/notes.md)
- [Messages](api/notify.md) and [Estimates](api/estimate.md): talking to customers
- [Gift cards](api/giftcard.md) and [Purchasing](api/purchase.md)
- [Status page](api/status.md): the customer's page at `/status`, the one part meant for the internet

## Testing

- [Testing](testing.md): server test helpers and pitfalls, `MockEngine`, coroutines in client tests, render tests,
  the end to end script

## How-to guides

- [Add a field to a record](how-to/add-a-field.md): every layer, with `Customer.company` as the example
- [Add a record type](how-to/add-an-entity.md): a new entity end to end, and the lists that have to learn about it
- [Add an API route](how-to/add-a-route.md): database call, route, client repository, handler, tests, docs
- [Add a database column](how-to/add-a-column.md): so existing databases get it too
- [Add a UI kit component](how-to/add-a-kit-component.md): in the kit, in the gallery, then on the screen
- [Add a theme or font](how-to/add-a-theme-or-font.md): a palette or interface font, and what to check
- [Add platform-specific code](how-to/add-a-platform-actual.md): an `expect` and its `actual`s
- [Add a printout](how-to/add-a-printable-document.md): a new slip or report

## Decisions

- [Decisions](decisions.md): why H2, per device sessions, photo deletion waiting a day, cents, client-made ids,
  the UI kit rules and other choices, with what each costs

## Roadmap

- [Roadmap](roadmap.md): the features planned next, in phases

## About these docs

[STYLE.md](STYLE.md) covers how pages are written, the template new pages start from, and how they're kept
current.
[`CLAUDE.md`](../CLAUDE.md) at the repo root is a dense summary of the same ground, kept for AI assistants.
