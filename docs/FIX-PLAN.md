# Fix plan

When one is done, also fix the doc page it came from (its **Source**), usually by
taking it out of that page's Known gaps. Things that aren't problems, just rules to follow, are in
[Rules to keep](#rules-to-keep) at the end, not in the phases.

## Phase 0: HTTPS removal, a decisive choice.
- [x] Completely remove HTTPS from server side. Only talk through HTTP and let a reverse proxy in the docker container handle all
      the https. Right now we use caddy. Allow the client to connect through https with a valid public certificate and show the trust dialog if it's
      a private made one. But add a warning to ensure that they understand what they are doing and that it's not a public cert. We also need to setup
      a way to test this locally by running a docker container and connecting with the desktop client and through the browser on the same machine.
      We need to figure out any other decisions we may need to make or things that need changed to align with this change.
- [x] Since the reverse proxy is handling https, we need a new waw to identify and handle too many login attempts. Let's go through some
      options and see what we can come up with.

## Phase 1: Fixups
- [ ] Editing an invoice at the counter replaces all its lines. If someone logs work while the invoice is open at the 
      counter and the counter then saves, that new line is lost. Fix this so it can work together.
- [ ] Deleting a ticket deletes its invoice, even a paid one. We need to change this so paid invoices can stay indefinitely 
      and let admins decide if they should be removed or not.
- [ ] Photos on users as well. The basic structure is there just needs added in. also an option to make a User ID with
      a QR that we can use later when we add the ability to clock employees times.

## Phase 2: Android, and other machines

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
