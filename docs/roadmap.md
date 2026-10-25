# Roadmap

What's planned beyond what TicketMan does now, in the order it's meant to be built. Each phase leans on the ones
before it: the customer status page needs statuses and updates customers may see, notifications link to the status
page, and so on. Numbers in brackets are the feature numbers this plan was drawn up from, so they stay easy to refer
to.

Every phase follows the usual rules: new columns go in `migratedTables` with a default, one off data fixes in
`runOnce`, `API_VERSION` goes up when older clients can't follow, `docs/api` is kept in step (both doc checks pass),
and each phase gets server tests plus a step in `scripts/counter_e2e.py`.

## Phase 0: Foundations (done)

- **Custom statuses** [3]. Shops name their own ticket statuses (Diagnosing, Awaiting parts, Awaiting customer,
  Unrepairable, ...), each with a tone, whether customers see it, and which built-in state it acts like (open, on
  hold, done). The built-in ones (In progress, On hold, Ready for pickup, Picked up) stay as fixed rows, so the
  queue, pickup and the reports keep working.
- **Customer visible updates.** A "show the customer" flag on notes, off by default, so internal notes never leak.
- **The shop's public address.** `ShopProfile.publicUrl`, used for status links and the claim slip's QR.

## Phase 1: Customer status page [15] (done)

- A small server rendered HTML page at `/status/{token}`, not the web client (that's megabytes and needs a login).
- Each ticket has an unguessable status token, which staff can replace to cut off an old link. The claim slip's QR
  points at the link; the device tag keeps the ticket number.
- A lookup form as well: ticket number plus the last 4 digits of the phone, rate limited.
- Shows the device, a status timeline (customer visible statuses only), the promised date, updates and photos marked
  for the customer, the estimate and the balance, and the shop's contact details. Never internal notes, the
  passcode, or the customer's details in full.
- A documented Caddy setup that puts only `/status/*` on the internet.

Built as above ([Status page API](api/status.md), [Running a server](operations.md#the-customer-status-page)). Links
stay working after pickup until staff replace them; the counter search takes a scanned status link too.

## Phase 2: Talking to customers (done)

- **Notifications** [1]. Email over SMTP and SMS through a provider interface (Twilio first). An outbox table sent
  and retried in the background, templates the shop edits, the status link in every message, opt in on the
  customer, and each message logged on the ticket. Sent on chosen status changes, an estimate being ready, ready
  for pickup, and pickup reminders.
- **Estimate approval** [2]. Quote lines on the open invoice, approved or declined on the status page (with a
  signature) or recorded by staff, keeping the amount, time, method and who. "Awaiting approval" sends the estimate
  out; approving moves the ticket on.

Built as above ([Messages API](api/notify.md), [Estimates API](api/estimate.md),
[Running a server](operations.md#messages-to-customers)), with two changes. Estimates are their own records whose
lines go onto the invoice when approved, rather than unapproved lines sitting on it, so invoice totals and pickup
never see work nobody agreed to. The status page signature is the customer's typed name, since the page has no
scripts to draw one with. Which statuses an estimate moves the ticket to is the shop's choice in its profile.

## Phase 3: Counter money (done)

- **Sales without a ticket** [4]. `Invoice.ticketIdentifier` nullable, so the counter sells straight from stock.
- **Refunds and returns** [5]. A negative payment with a reason, optionally putting parts back in stock, admin only
  (or a new permission), audit logged.
- **Store credit and gift cards** [16]. A balance on the customer, gift card codes, both taken as payment methods.
- **Multiple tax rates** [17]. Tax classes on items and charges, kept on the invoice when it's made.

Built as above ([Counter API](api/counter.md#post-apicountersale), [Invoices API](api/invoice.md#post-apiinvoiceidentifierrefund),
[Gift cards API](api/giftcard.md), [Money](shared/money.md#more-than-one-rate)). Refunds are admin only, on paid
invoices; an open invoice's payment is still just taken off. Gift cards are sold as a line on any invoice and loaded
when it's paid, and voiding one that's been spent from is refused. Store credit comes from refunds and admin
adjustments. Tax classes sit beside the shop's main rate rather than replacing it, and the discount is shared
between rates by how much each is charged on.

## Phase 4: Inventory and purchasing (done)

- **Item details** [6]. SKU, barcode, category and a reorder point per item, which the low stock list uses.
- **Suppliers and purchase orders** [7]. Ordering, and receiving into stock through `adjustStock`. Parts ordered
  for a ticket put it in "Awaiting parts" and move it on when they arrive.
- **Labels and scanning** [8]. A barcode label printout, and the counter search taking input from a scanner.

Built as above ([Inventory API](api/inventory.md), [Purchasing](server/purchasing.md), [Printing](client/printing.md),
[Counter screen](ui/screens/counter.md#scanning)). Which statuses ordering and receiving move a ticket to is the
shop's choice in its profile, like estimates'. Receiving also sets the item's cost to what the order paid. Barcodes
are Code 128 from an in-house encoder, printed on a new 57 x 32mm label paper size; a scanner is just a keyboard
that presses Enter, so the counter, sale and pickup searches look the code up on Enter.

## Phase 5: Repair depth (done)

- **Customer devices** [11]. Device records owned by a customer that tickets point to, so each device has a
  history. Several devices at one check-in become linked tickets.
- **Intake checklist and passcode** [10]. A checklist per device type filled at check-in and printed on the claim
  slip. The passcode is stored encrypted, shown only to techs, its reads audit logged, cleared at pickup.
- **Warranty** [9]. A warranty period on a repair or charge, warranty returns linked to the original and free by
  default; the repeat check shows "under warranty until".
- **Storage fees and abandoned devices** [18]. A fee after a number of days ready, abandoned after more, with the
  terms on the claim slip and the customer told through Phase 2.

Built as above ([Customers API](api/customer.md), [Tickets API](api/ticket.md#passcode),
[Counter](server/counter.md#storage-and-abandoned-devices)). Existing tickets got device records from what was typed
on them. Passcodes are sealed under a key from the server secret, so moving them to a new server needs the same
`SECRET`. The warranty starts at pickup and lasts as long as the longest labor or part line gives (the shop's default
unless a line says). The storage fee is a line synced onto the invoice whenever it's opened, waived by taking it off;
abandoned devices move to their own list in the queue rather than to a status.

## Phase 6: Running the business (done)

- **Reports** [12]. Sales and profit by period, tax collected, tech productivity and commission from the work log,
  turnaround from status history, refunds.
- **Appointments** [13]. A calendar, appointments turning into check-ins, and later booking from the public page.
- **Time clock** [19]. Clock in and out by PIN at a station, with timesheets.
- **Accounting export** [20]. QuickBooks and Xero friendly CSV, building on the invoice export.

Built as above ([Reports API](api/report.md), [Appointments API](api/appointment.md),
[Time clock API](api/timeclock.md), [Invoices API](api/invoice.md#get-apiinvoiceexport)), except booking from the
public page, which is still to come. Profit uses each part's cost kept on its invoice line when the invoice is paid;
invoices paid before that count their parts as free, and the report says how many. Commission is a percent of the
labor each tech bills, kept apart from users so only admins see it. Appointments get a reminder a day ahead through
Phase 2's messages. Staff can clock in from their own device as well as by PIN at a station, where nobody is signed
in. The QuickBooks and Xero exports are one row per line of each paid invoice, with the shop's own item, account
and tax rate names, plus a payments export for the bank.

## Phase 7: Big integrations

- **Payment processing** [14] (done). Card terminals and payment links (Stripe Terminal or Square), and paying the
  balance from the status page.
- **More than one location** [21] (done). One server for every shop, with a location on tickets, stock, stations, users and
  numbering. Each location can work on its own: its counter, queue, stock and staff show only its own, so running
  one shop day to day doesn't mean wading through the others. Customers stay company wide: looking one up shows
  their tickets at every location, and their store credit, gift cards and devices go with them. Reports and admin
  can look at one location or all of them. It touches nearly every query, so it comes last.

Payments are built as above ([Payments API](api/payment.md)), with Stripe and Square behind one
`PaymentProcessor`, so a shop picks either. Every try at a card is a charge the server follows itself, asking the
processor while the counter waits, every so often after, and when a webhook says so (webhooks are optional and only
say what to ask about). A paid charge lands on its invoice as a card payment; one taken for a sale or a deposit
before its invoice exists is claimed by the payment that names it. Refunds of card payments go back onto the card.
A simulated processor stands in on development servers for the tests and `scripts/counter_e2e.py`. Still to come:
check in deposits on a reader from the counter's wizard (the server takes them already), sending a payment link by
email or text, and tipping and surcharges.

Locations are built as above ([Locations API](api/location.md)). Each location numbers its own tickets, invoices and
orders with its code, keeps its own stock counts of the shared catalog, and can have its own address, contacts and
tax rates; the main one, made from what was there before, keeps the old numbers. Devices say where they work with
every request: a station is fixed to its location, staff start at their home location and can pick another, and
admins can look at all of them at once. Tickets move between locations with their open invoice. Still to come:
moving stock between locations (a transfer, rather than counting it down at one and up at the other by hand), and a
customer picking a location when booking from the public page.

## iOS app

The code is written (on Linux, where it can't compile); testing it needs a Mac. See
[Platforms](client/platforms.md#ios) for how it fits together.

- **Port the app** (done, untested). iOS targets on in `shared` and `composeApp` when Gradle runs on a Mac,
  `iosApp` wired to the `ComposeApp` framework (bundle `org.mr0btain.ticketman`, iOS 16+), and every `actual`: the
  Darwin HTTP engine with `DarwinTrust` checking certificates against `ServerTrust`, `AppDir`, `AppLogger`,
  `saveFile`, `saveDownload`, `fileBody`, `printDocument` (`UIPrintInteractionController` with
  `HtmlPrintRenderer`'s HTML), `shrinkForUpload`, `imageDiskCache`, `TmVerticalScrollbar`, `defaultServerAddress`,
  `alwaysWide`, the platform module (KSafe in the keychain), and kiosk pinning through Guided Access.
- **Build it in CI** (done, not yet run). An `iOS compile` job on `macos-latest` in `.github/workflows/ci.yml`.
- **Release it** (done, opt in). An unsigned IPA in the GitHub release when `build_ios` or `BUILD_IOS` is set.

## Phase iOS: Test and fix (needs a Mac)

Not tied to the numbered phases: done whenever work moves to a macOS machine.

- **Make it build.** `./gradlew :composeApp:compileKotlinIosSimulatorArm64`, then the app from Xcode on a simulator.
  Expect fixes in the Security framework and Foundation calls in `DarwinTrust`, `PrintDocument.ios.kt` and
  `MainViewController.kt`, which were written without a compiler.
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

## Phase macOS: Test the desktop app (needs a Mac)

The desktop app is the same JVM code as on Linux and Windows, and the release builds arm64 and x64 DMGs on GitHub's Mac
runners, but nobody has run it on a Mac yet.

- **Run it from source and from the DMG** on Apple silicon: `./gradlew :composeApp:run`, then the released DMG
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

## Open questions

- Phase 1 (settled): the server is reached from the internet through the proxy, which lets outside addresses
  reach only `/status`. Nothing is pushed elsewhere.
- Phase 2 (settled): Twilio for texts behind an `SmsSender` interface, and shops bring their own SMTP server.
- Phase 7 (settled): both Stripe and Square, behind one interface. More than one location is one server for every
  shop, each able to keep to its own tickets, stock and staff, with customers shared across them.
- iOS: is it shared by sideloading, or is a paid Apple Developer account worth it for TestFlight and the App Store?
