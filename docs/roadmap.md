# Roadmap / TODO

What's planned to add or fix.

## TODOs

- **View Models** The main view model is getting pretty full, maybe we can split it up to each screen having their own
  view model. States now survive switching screens either way (`navigateTo` saves each screen's state), so this is only
  about tidiness. Most of it is wiring between handlers that call each other (admin into counter and records, calendar
  into counter, logout into everything), and the kiosk uses the counter outside the nav host, so those would still need
  something above the screens to connect them.

## From comparing other repair shop apps

Compared in November 2026 against RepairShopr, RepairDesk, RO App (Orderry), CellSmart, RepairQ and mHelpDesk. The
core (status page, estimates, warranty, storage, locations, card payments, stored value, purchasing, booking,
calendar, time clock, reports) is on par or ahead. The gaps are messaging, a repair price menu, integrations and how
quickly a shop can start. Each item lists its options, the picked one first when there's a pick.

Order: C1 → A1 → A2 → B1 → A3 → A5, A4 → A6, A7, then the rest.

### Remove or trim

- **C1. Shims for older clients, before 1.0 ships.** `versionNoticeOf` treats 0.x to 1.0 as a major version apart, so
  0.9 clients get `426` from a 1.0 server. Done: the one request `POST /backup` and the client's fallback to it, a
  null `revision` on an update skipping the check (now `400`), and the user update keeping the home location when
  none is sent. Left: log downloads are audited as `BACKUP` so older clients can read the log; give them their own
  action once `ticketman.version` is 1.0.0, and decode an unknown `AuditAction` leniently so later minor versions
  can add some. A server too old to report its release is only warned about (`versionNoticeOf`); blocking it would
  need the client tests' ping mocks to send a version. Data migrations and the warnings for retired HTTPS settings
  stay: those are about old databases and old config, not old clients.
- **C2. Time off balances** (allowances, carry over, floating holidays) are payroll territory; other apps stop at the
  schedule and the clock. Options: put it behind a module switch (B2), keep requests and approval but drop balances,
  or keep it.
- **C3. Release targets.** Keep iOS compiling in CI but don't ship it until it's tested; pick one container registry
  and one Linux package besides the DEB.

### Add

- **A1. Repair price menu.** Done as a catalog table ([Repair menu API](api/catalog.md)): models with their
  repairs, edited on the admin Repair menu tab and ticked on check-in's Repair step onto the invoice. Next: a CSV
  import of the menu (a supplier's price list), warning at check-in when a repair's part is out of stock at the
  location, offering the menu when adding lines to an open ticket's invoice, and per-location prices if a shop asks.
- **A2. Two-way texts.** Options: inbound Twilio texts through a webhook under `/status/`, kept per customer and
  ticket, with a conversation panel and unread badge on the ticket card; email replies from an IMAP mailbox later; or
  only a Reply-To header to the shop's address.
- **A3. Ticket board.** Options: a column per status (custom ones included) with drag to change status; a read-only
  wall screen served like the status page, refreshing itself; both.
- **A4. Check-out checklist and before/after photos.** Options: a second checklist per `DeviceKind` needed before
  ready for pickup, required or not per shop; or optional and printed on the slip and invoice.
- **A5. Review request after pickup.** Options: a `REVIEW_REQUEST` message N days after pickup with the shop's review
  link; a 1 to 5 rating on the status page for our own reports (it must not decide who gets the review link, Google
  forbids gating).
- **A6. Moving in from another app.** Options: CSV imports of tickets and invoices as history, with a column mapping
  step; RepairShopr's API.
- **A7. Public API and webhooks.** Options: admin-made API keys as a session kind with an access level, audit logged;
  outbound webhooks from audit events, HMAC signed, retried through the outbox loop.
- **A8. Custom fields.** Options: per `DeviceKind` in the shop profile like checklists, JSON on the ticket; or typed
  and searchable on tickets, customers and inventory.
- **A9. Business customers** (only if aiming at IT shops): several contacts, invoices on account with terms and
  statements, then recurring invoices.
- **A10. Customer account page**: a link listing all of a customer's tickets, invoices and warranties, built on
  `StatusPage` and `RequestLimiter`.
- **A11. Smaller:** a work timer filling a work entry's minutes; serial or IMEI per stock unit; buying devices in
  (cash or store credit, becoming serialized stock); a customer facing display for the cart and signature.
- **Skipped:** an AI receptionist, marketing campaigns (an export to a mailing service covers it), loyalty points
  (store credit covers most of it), parts supplier APIs (closed; a CSV price list does the job).

### Change

- **B1. Getting started.** Options: a first run wizard (shop profile, tax, users, checklists, sample data); a one shop
  mode where the desktop app runs the server itself; an installer setting up the server and Caddy as services.
- **B2. Module switches** in the shop profile hiding screens and admin tabs a shop doesn't use (purchasing, calendar,
  time clock, time off, payments, booking).
- **B3. Languages.** Options: translate what customers see first (status page, messages, prints); or move every
  string into Compose resources.

## iOS app

The code is written; testing it needs a Mac. See
[Platforms](client/platforms.md#ios) for how it fits together.

- **Test every `actual`**: the Darwin HTTP engine with `DarwinTrust` checking certificates against 
 ` ServerTrust`, `AppDir`, `AppLogger`, `saveFile`, `saveDownload`, `fileBody`, `printDocument` 
   (`UIPrintInteractionController` with `HtmlPrintRenderer`'s HTML), `shrinkForUpload`, `imageDiskCache`,
 ` TmVerticalScrollbar`, `defaultServerAddress`, `alwaysWide`, the platform module (KSafe in the keychain), 
   and kiosk pinning through Guided Access.
- **Walk through it on a simulator and a real iPhone and iPad**, the way Android was: connecting (local network
  permission, plain HTTP to a local address, refused to an internet one), trusting a Caddy CA through the trust
  dialog and the proxy's `ca.pem`, a wrong name, logging in and the session coming back after a restart, records,
  a note with a photo (does it come out upright; then whether `shrinkForUpload` can use FileKit's compressor),
  check-in with a signature, pickup, printing a claim slip to AirPrint (80mm and Letter, photos in the HTML),
  saving an invoice PDF and a backup to Files, restoring from one, the admin tabs, the keyboard over the counter's
  fields, and kiosk mode with Guided Access.
- **Fix what it finds**, add what only shows up on iOS to [Platforms](client/platforms.md#ios), and run
  `scripts/counter_e2e.py`'s flow by hand against the device.
- **Turn the release on.** Once it works, build the IPA by default (a `skip_ios` input and `SKIP_IOS` variable like
  the others) and let a failed iOS build stop the release.
- **Later, with a paid Apple Developer account:** a signed IPA, or TestFlight and the App Store, with the certificate
  and provisioning profile as secrets.

## Phase macOS: Test the desktop app

- **Run it from source and from the DMG** on Apple Silicon: `./gradlew :composeApp:run`, then the released DMG
  dragged to Applications (unsigned, so `xattr -dr com.apple.quarantine /Applications/TicketMan.app` first, see
  [Releasing](releasing.md)). Check the version shown inside (macOS refuses a bundle version starting with 0).
- **Walk through it** like Linux and Windows: connecting and trusting a Caddy CA, logging in and the session coming
  back after a restart (KSafe's store on macOS), records with photos, check-in with a signature on a trackpad,
  pickup, printing a claim slip and an item label through Java2D to a macOS printer, saving an invoice PDF and a
  backup with the file dialogs, restoring, the admin tabs, keyboard shortcuts (F keys need `fn` on most Mac
  keyboards), the app icon, and kiosk mode going fullscreen.
- **Intel Macs**: try the x64 DMG (built on `macos-15-intel`) the same way.
- **Fix what it finds** and note what only shows up on macOS in [Platforms](client/platforms.md).
- **Later, with a paid Apple Developer account:** sign and notarize the DMG (Compose's
  `macOS { signing { } notarization { } }`) so it opens without the quarantine step.
