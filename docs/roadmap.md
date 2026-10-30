# Roadmap / TODO

What's planned to add or fix.

## TODOs

- **View Models** The main view model is getting pretty full, maybe we can split it up to each screen having their own
  view model. States now survive switching screens either way (`navigateTo` saves each screen's state), so this is only
  about tidiness. Most of it is wiring between handlers that call each other (admin into counter and records, calendar
  into counter, logout into everything), and the kiosk uses the counter outside the nav host, so those would still need
  something above the screens to connect them.

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
