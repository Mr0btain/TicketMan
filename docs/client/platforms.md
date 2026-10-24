# Platforms

The client is one codebase built for desktop (JVM), Android, the web (Wasm) and iOS (only on a Mac, and not yet
tested, see [iOS](#ios)). Almost everything is in
`commonMain`; what has to differ goes through `expect`/`actual`. This page lists every one of those, what each
platform does, and what isn't finished.

## Entry points

| Platform | Entry point | Starts Koin | Notes |
|----------|-------------|-------------|-------|
| Desktop | `composeApp/src/desktopMain/.../main.kt` | Inside the `Window` | 1460×800 window. Kiosk mode switches it to full screen through `AppWindow.fullscreen`. `FileKit.init("TicketMan")` |
| Android | `androidApp/.../MainActivity.kt` + `TicketManApplication` | In `Application.onCreate`, once per process, with the `Context` KSafe needs | Edge to edge, so the shells pad by `shellInsets`. Kiosk mode hides the system bars and pins the app (lock task mode, reported in `AppWindow.pinned`). Attaches `AndroidPrintHost` |
| Web | `composeApp/src/wasmJsMain/.../main.kt` | Before anything is shown | Waits for KSafe's cache, then `ComposeViewport`. Kiosk mode asks the browser for full screen |
| iOS | `composeApp/src/iosMain/.../MainViewController.kt`, shown by `iosApp`'s `ContentView` | The first time the view controller is made | Edge to edge, so the shells pad by `shellInsets`. Kiosk pinning is Guided Access, reported in `AppWindow.pinned` while it's on |

All of them call the same `App()`.

## `expect` declarations

In `composeApp` (`composeApp/src/commonMain/.../`):

| Declaration | File | Desktop | Android | Web | iOS |
|-------------|------|---------|---------|-----|-----|
| `getHttpEngine(trust)` | `components/network/HttpEngine.kt` | OkHttp that checks certificates with `ServerTrust` (shared, `jvmSharedMain`) | Same as desktop | Ktor's Js engine, the browser checks certificates | Ktor's Darwin engine, certificates checked by `DarwinTrust` with `ServerTrust` |
| `defaultServerAddress()` | `components/network/DefaultServerAddress.kt` | `http://localhost:8080` on a debug build, blank otherwise | Blank | The page's own origin | Blank |
| `BuildConfigImpl.isDebug()` | `components/debug/IsDebug.kt` | `-DticketmanDebug=true` system property | The app is debuggable (a debug build) | The page came from `localhost` | A debug binary (Xcode's Debug configuration) |
| `platformModule` | `components/injection/PlatformModule.kt` | `KSafe` | `KSafe` with the app `Context` | `KSafe` | `KSafe` (keychain) |
| `initAppDir()` | `components/injection/InitAppDir.kt` | `AppDir()` | `AppDir(context)` | `AppDir()`, with no folder | `AppDir()`, Application Support |
| `printDocument(document, paper)` | `components/print/PrintDocument.kt` | Java2D `PrinterJob` | `WebView` + `PrintManager` | Hidden iframe | `UIPrintInteractionController` with the HTML |
| `saveFile(bytes, name, ext)` | `components/data/SaveFile.kt` | FileKit save dialog | FileKit save dialog | Browser download | FileKit save dialog |
| `saveDownload(...)` | `components/network/transfer/Download.kt` | Save dialog, then stream | Save dialog, then stream | Browser download of the link | Save dialog, then stream |
| `fileBody(file, type)`, `fileBodyLimitBytes` | `components/network/transfer/Upload.kt` | Streams the file, no limit | Streams the file, no limit | Reads it whole, up to 512 MB | Streams the file, no limit |
| `shrinkForUpload(bytes)` | `components/image/ImageClient.kt` | Sends as is | Scales photos over 1.5 MB to 2560px JPEG | Sends as is | Sends as is |
| `imageDiskCache(context, size)` | `components/image/ImageClient.kt` | Coil disk cache in FileKit's cache dir | Coil disk cache in the app cache dir | None, the browser caches | Coil disk cache in Caches |
| `alwaysWide` | `ui/screens/navigation/AlwaysWide.kt` | `true`: always the nav rail | `false`: the window's width decides | `false` | `false` |
| `TmVerticalScrollbar(...)`, `tmShowsScrollbars` | `ui/kit/foundation/TmScrollbar.kt` | Desktop scrollbars, shown | None | None | None |

In `shared` (see [DTOs, caches and platform helpers](../shared/dtos-and-platform.md#platform-helpers)): `AppLogger`,
`AppDir`, `getPlatform()`.

## Adding an `expect`

1. Declare it in `commonMain` in `<Name>.kt`, with a KDoc saying what each platform should do.
2. Add `<Name>.desktop.kt`, `<Name>.android.kt`, `<Name>.wasmJs.kt` and `<Name>.ios.kt` beside it in each source
   set, same package. iOS only compiles on a Mac, so GitHub's CI is what checks the iOS one from Linux.
   When desktop and Android do the same thing, one `<Name>.jvmShared.kt` in `jvmSharedMain` covers both.
3. Add a row to the table above.

`jvmSharedMain` is a source set that desktop and Android both build from, added in `composeApp/build.gradle.kts`
with `applyDefaultHierarchyTemplate { group("jvmShared") }`. It holds the OkHttp engine and the certificate checks
in `PinningTrustManager.kt`, and OkHttp is a dependency there.

Calls in `composeApp` always bind to `composeApp`'s own `actual`s at compile time. An `actual` has to live in the
same module as its `expect`, so `androidApp` can't supply one.

## Android

`minSdk` is 25 (Android 7.1), set once in `gradle/libs.versions.toml` for `shared`, `composeApp` and
`androidApp`. Android 7.0 doesn't trust Let's Encrypt's root (ISRG Root X1, added in 7.1.1), so it couldn't connect
to a proxy with the usual public certificate. 7.1.0 is also API 25 without it, but it only shipped on Pixel and
Nexus devices, which all got 7.1.1.

The manifest asks for `INTERNET` and allows plain HTTP (`usesCleartextTraffic`), since a server can still be
reached on its HTTP port. Remove that once servers redirect HTTP to HTTPS everywhere.

Things that only show up on Android:

- **Edge to edge.** `MainActivity` calls `enableEdgeToEdge()`, so the app draws under the status and navigation
  bars. `WideAppShell`, `PhoneAppShell` and `KioskShell` pad their content by `shellInsets` (system bars and the
  camera cutout, not the keyboard) and leave the backdrop full bleed. Desktop and web have no insets.
- **The regex engine is ICU**, which refuses some patterns the JVM takes, like a bare `}`. Shared code that only
  ever ran on the JVM can throw `PatternSyntaxException` here the first time it runs; escape braces.
- **Touch, not keys.** Compose's `LocalInputModeManager` says whether someone is touching or typing. The counter
  only focuses its search field, and only shows its F-key hints, while they're typing (`usingKeyboard()`,
  `keyHint()` in `ui/screens/counter/KeyHints.kt`), so a phone doesn't open the on screen keyboard on its own.
- **Printing** goes through the system print dialog. It asks for an 80mm page for receipts, but Android only keeps
  that size if the chosen printer offers it; with no printer chosen yet the preview shows Letter.

To try it in the emulator against a dev server on this machine, forward the port into the emulator and use
`localhost` as the address, which also skips the "Not secure" warning:

```bash
adb reverse tcp:8098 tcp:8098      # then connect to http://localhost:8098
```

The emulator's alias for the host, `http://10.0.2.2:<port>`, also works where the host's firewall lets it through.

## Web

A real server serves the web client itself (see [Running a server](../operations.md#web-client)), so the page and
the API share one address. With no address saved, the web client tries the origin it was loaded from and keeps it
if a TicketMan server answers there. Logs go to the browser console.

## iOS

The iOS targets (`iosArm64`, `iosSimulatorArm64`) are only added when Gradle runs on a Mac, since they need Xcode;
everywhere else the build leaves them out without a word. `iosApp` is the Xcode project: its build phase runs
`:composeApp:embedAndSignAppleFrameworkForXcode`, and `ContentView` shows `MainViewController()`. Open
`iosApp/iosApp.xcodeproj` in Xcode and run it on a simulator, or set `TEAM_ID` in
`iosApp/Configuration/Config.xcconfig` to run it on a device.

- **Certificates.** `DarwinTrust` does what `PinningTrustManager` does on the JVM through the Security framework:
  the system's authorities, then the authority trusted for that address, else it refuses the certificate into
  `ServerTrust` with the proxy's CA (fetched from `ServerTrust.AUTHORITY_PATH`) or the top certificate. iOS doesn't
  give apps a certificate's names, so a wrong name says the certificate isn't for the address used.
- **Plain HTTP** is only allowed to local addresses (`NSAllowsLocalNetworking` in `Info.plist`); iOS refuses it to
  anything on the internet. The first connection to a server on the local network asks for permission.
- **Kiosk mode** can't pin the app itself: Guided Access is started by triple clicking the side button, or by the
  app on a supervised device. `AppWindow.pinned` is true while it's on and unknown otherwise, so the kiosk doesn't
  offer a pin button that can't work.
- **Photos** go up as they are, like desktop and web, until FileKit's iOS compressor is checked to keep them upright.
- **The device name** admins see is the model and system version, since iOS hides the name someone gave the device.

## Known gaps

- **Android has only been run in the emulator** (a phone-sized Android 17 image): connecting, logging in, the
  saved session coming back, records, a note with a photo from the system picker (uploaded, turned upright,
  shown), the counter's search, check-in with a signature, pickup with payment, printing a claim slip to the
  system print dialog, the admin overview, and kiosk mode with pinning. Not yet on a real device, a tablet, or an
  actual printer.
- **Web has been run in headless Chromium only**, served by `-PwithWeb`: login, records, a note with a photo, the
  counter search, ticket card, check-in, printing (the hidden iframe gets the right receipt), backup download and
  restore upload, the admin tabs and settings all worked. Firefox and Safari haven't been tried, and neither has
  kiosk mode's full screen, which a headless browser can't show.
- **`androidApp` needs compileSdk 37** (FileKit 0.16 requires it). Building the APK also needs an AAPT2 that runs
  on the build machine: Gradle downloads the x86 Linux one, which fails on ARM Linux.
- **iOS has never been built or run.** The code was written on Linux, where it can't compile, so the first build
  on a Mac is expected to need fixes. Testing it is a phase of the [roadmap](../roadmap.md#phase-ios-test-and-fix-needs-a-mac).

## See also

- [Overview](../architecture/overview.md)
- [Transfers and photos](transfers-and-photos.md)
- [Printing](printing.md)
- [Add platform-specific code](../how-to/add-a-platform-actual.md)
