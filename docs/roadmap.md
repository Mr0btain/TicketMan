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

## Phase 1: Customer status page [15]

- A small server rendered HTML page at `/status/{token}`, not the web client (that's megabytes and needs a login).
- Each ticket has an unguessable status token, which staff can replace to cut off an old link. The claim slip's QR
  points at the link; the device tag keeps the ticket number.
- A lookup form as well: ticket number plus the last 4 digits of the phone, rate limited.
- Shows the device, a status timeline (customer visible statuses only), the promised date, updates and photos marked
  for the customer, the estimate and the balance, and the shop's contact details. Never internal notes, the
  passcode, or the customer's details in full.
- A documented Caddy setup that puts only `/status/*` on the internet.

## Phase 2: Talking to customers

- **Notifications** [1]. Email over SMTP and SMS through a provider interface (Twilio first). An outbox table sent
  and retried in the background, templates the shop edits, the status link in every message, opt in on the
  customer, and each message logged on the ticket. Sent on chosen status changes, an estimate being ready, ready
  for pickup, and pickup reminders.
- **Estimate approval** [2]. Quote lines on the open invoice, approved or declined on the status page (with a
  signature) or recorded by staff, keeping the amount, time, method and who. "Awaiting approval" sends the estimate
  out; approving moves the ticket on.

## Phase 3: Counter money

- **Sales without a ticket** [4]. `Invoice.ticketIdentifier` nullable, so the counter sells straight from stock.
- **Refunds and returns** [5]. A negative payment with a reason, optionally putting parts back in stock, admin only
  (or a new permission), audit logged.
- **Store credit and gift cards** [16]. A balance on the customer, gift card codes, both taken as payment methods.
- **Multiple tax rates** [17]. Tax classes on items and charges, kept on the invoice when it's made.

## Phase 4: Inventory and purchasing

- **Item details** [6]. SKU, barcode, category and a reorder point per item, which the low stock list uses.
- **Suppliers and purchase orders** [7]. Ordering, and receiving into stock through `adjustStock`. Parts ordered
  for a ticket put it in "Awaiting parts" and move it on when they arrive.
- **Labels and scanning** [8]. A barcode label printout, and the counter search taking input from a scanner.

## Phase 5: Repair depth

- **Customer devices** [11]. Device records owned by a customer that tickets point to, so each device has a
  history. Several devices at one check-in become linked tickets.
- **Intake checklist and passcode** [10]. A checklist per device type filled at check-in and printed on the claim
  slip. The passcode is stored encrypted, shown only to techs, its reads audit logged, cleared at pickup.
- **Warranty** [9]. A warranty period on a repair or charge, warranty returns linked to the original and free by
  default; the repeat check shows "under warranty until".
- **Storage fees and abandoned devices** [18]. A fee after a number of days ready, abandoned after more, with the
  terms on the claim slip and the customer told through Phase 2.

## Phase 6: Running the business

- **Reports** [12]. Sales and profit by period, tax collected, tech productivity and commission from the work log,
  turnaround from status history, refunds.
- **Appointments** [13]. A calendar, appointments turning into check-ins, and later booking from the public page.
- **Time clock** [19]. Clock in and out by PIN at a station, with timesheets.
- **Accounting export** [20]. QuickBooks and Xero friendly CSV, building on the invoice export.

## Phase 7: Big integrations

- **Payment processing** [14]. Card terminals and payment links (Stripe Terminal or Square), and paying the balance
  from the status page.
- **More than one location** [21]. A location on tickets, stock, stations, users and numbering. It touches nearly
  every query, so it comes last.

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

## Open questions

- Phase 1: will the server be reachable from the internet, or should the status page live elsewhere and be pushed
  updates?
- Phase 2: which SMS provider, and will shops bring their own SMTP?
- Phase 7: which payment processor, and is "more than one location" one server for several shops or one server
  per shop with reporting across them?
- iOS: is it shared by sideloading, or is a paid Apple Developer account worth it for TestFlight and the App Store?
