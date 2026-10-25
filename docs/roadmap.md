# Roadmap

What's planned beyond what TicketMan does now, in the order it's meant to be built. Each phase leans on the ones
before it: the calendar views draw the staff schedule, and time off requests land on it.

Every phase follows the usual rules: new columns go in `migratedTables` with a default, one off data fixes in
`runOnce`, `API_VERSION` goes up when older clients can't follow, `docs/api` is kept in step (both doc checks pass),
and each phase gets server tests plus a step in `scripts/counter_e2e.py`.

## Done so far

Phases 0 to 7 are built: custom statuses and customer visible updates, the [customer status page](api/status.md),
[messages](api/notify.md) and [estimates](api/estimate.md), counter money (sales, refunds, store credit and
[gift cards](api/giftcard.md), tax classes), [inventory and purchasing](server/purchasing.md), repair depth (customer
devices, intake checklists and passcodes, warranties, storage fees), running the business ([reports](api/report.md),
[appointments](api/appointment.md), the [time clock](api/timeclock.md), accounting exports), and the big
integrations ([card payments](api/payment.md) and [more than one location](api/location.md)). How each works is in
the linked pages and [the docs index](README.md); git history has how they were planned.

## Phase 8: Staff schedule (done)

Who works when, kept beside the appointments on the calendar. Admins write it; everyone logged in sees it. Built
([Staff schedule API](api/schedule.md)) as shifts, time off, training and other entries per person, time off in
whole days, a shift repeating on chosen weekdays until a date as a series, and changes or removals going to one entry,
the later ones or the whole series. Each person's scheduled hours per day come from their timed entries.

## Phase 9: Calendar views (done)

The calendar has Day, Week, Month and Staff views, remembered per device, each with back, today and forward. Day
shows who's working on a timeline of the day's hours above its appointments; Week is the list of days with each
day's staff; Month is day squares with what runs over more than a day drawn as bars across them, broken at each week
and picked up on the next, and each square's appointments and who's on shift; Staff is a row per person and a
column per day for two weeks, entries as blocks from start to end, rows growing for overlaps, the day's scheduled
hours under each row, the names fixed while the days scroll sideways on a phone. Admins add an entry from the Staff
board's empty cells or the Schedule button, and open one to change it from any view. Still to come: booking an
appointment straight from an empty time slot, and dragging blocks to move them.

## Phase 10: Time off requests and budgets

- **Budgets.** Admins set each person's time off allowance per year, in hours, on the admin Users tab. A person's
  balance is the allowance less approved time off in that year (and what's waiting, shown apart).
- **Requests.** Staff ask for time off from the calendar or Settings: the days, part of a day in hours, and a note.
  It shows on the schedule as waiting, and can't be more than the balance left. Admins approve or decline it with a
  reason on an admin Requests list (a badge on the tab, like account requests); approving turns it into a Time off
  entry and takes it from the balance. Staff can withdraw one still waiting.
- **Seen by everyone.** Approved time off is on every view; waiting requests only to the person and admins.
- **Told.** The person sees the answer on their next open of the calendar, and in the audit log; no messages to
  staff yet.

## Phase 11: Leftovers

Smaller pieces left from earlier phases, picked up as they're needed.

- **Booking from the status page.** Customers picking an appointment time themselves, at a location, from the
  times staff are scheduled.
- **Card readers at check-in.** Taking a deposit on a reader from the counter's wizard (the server takes them
  already), and sending a payment link by email or text.
- **Tips and surcharges** on card payments.
- **Stock transfers** between locations, rather than counting down at one and up at the other by hand.
- **Clocking against the schedule.** The time clock and timesheets showing scheduled hours beside worked ones, late
  starts and missed shifts.

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

- Phase 10: does a time off allowance start over on 1 January, or on each person's start date, and does unused time
  carry over? Built as starting over each calendar year with no carry over until decided.
- iOS: is it shared by sideloading, or is a paid Apple Developer account worth it for TestFlight and the App Store?
