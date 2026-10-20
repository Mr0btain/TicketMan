# Platforms

The client is one codebase built for desktop (JVM), Android and the web (Wasm). Almost everything is in
`commonMain`; what has to differ goes through `expect`/`actual`. This page lists every one of those, what each
platform does, and what isn't finished.

## Entry points

| Platform | Entry point | Starts Koin | Notes |
|----------|-------------|-------------|-------|
| Desktop | `composeApp/src/desktopMain/.../main.kt` | Inside the `Window` | 1460×800 window. Kiosk mode switches it to full screen through `AppWindow.fullscreen`. `FileKit.init("TicketMan")` |
| Android | `androidApp/.../MainActivity.kt` + `TicketManApplication` | In `Application.onCreate`, once per process, with the `Context` KSafe needs | Hides the system bars in kiosk mode. Attaches `AndroidPrintHost` |
| Web | `composeApp/src/wasmJsMain/.../main.kt` | Before anything is shown | Waits for KSafe's cache, then `ComposeViewport`. Kiosk mode asks the browser for full screen |

All of them call the same `App()`.

## `expect` declarations

In `composeApp` (`composeApp/src/commonMain/.../`):

| Declaration | File | Desktop | Android | Web |
|-------------|------|---------|---------|-----|
| `getHttpEngine(trust)` | `components/network/HttpEngine.kt` | OkHttp that checks certificates with `ServerTrust` (shared, `jvmSharedMain`) | Same as desktop | Ktor's Js engine, the browser checks certificates |
| `defaultServerAddress()` | `components/network/DefaultServerAddress.kt` | `http://localhost:8080` on a debug build, blank otherwise | Blank | The page's own origin |
| `BuildConfigImpl.isDebug()` | `components/debug/IsDebug.kt` | `-DticketmanDebug=true` system property | The app is debuggable (a debug build) | The page came from `localhost` |
| `platformModule` | `components/injection/PlatformModule.kt` | `KSafe` | `KSafe` with the app `Context` | `KSafe` |
| `initAppDir()` | `components/injection/InitAppDir.kt` | `AppDir()` | `AppDir(context)` | `AppDir()`, with no folder |
| `printDocument(document, paper)` | `components/print/PrintDocument.kt` | Java2D `PrinterJob` | `WebView` + `PrintManager` | Hidden iframe |
| `saveFile(bytes, name, ext)` | `components/data/SaveFile.kt` | FileKit save dialog | FileKit save dialog | Browser download |
| `saveDownload(...)` | `components/network/transfer/Download.kt` | Save dialog, then stream | Save dialog, then stream | Browser download of the link |
| `fileBody(file, type)`, `fileBodyLimitBytes` | `components/network/transfer/Upload.kt` | Streams the file, no limit | Streams the file, no limit | Reads it whole, up to 512 MB |
| `shrinkForUpload(bytes)` | `components/image/ImageClient.kt` | Sends as is | Scales photos over 1.5 MB to 2560px JPEG | Sends as is |
| `imageDiskCache(context, size)` | `components/image/ImageClient.kt` | Coil disk cache in FileKit's cache dir | Coil disk cache in the app cache dir | None, the browser caches |
| `alwaysWide` | `ui/screens/navigation/AlwaysWide.kt` | `true`: always the nav rail | `false`: the window's width decides | `false` |
| `TmVerticalScrollbar(...)`, `tmShowsScrollbars` | `ui/kit/foundation/TmScrollbar.kt` | Desktop scrollbars, shown | None | None |

In `shared` (see [DTOs, caches and platform helpers](../shared/dtos-and-platform.md#platform-helpers)): `AppLogger`,
`AppDir`, `getPlatform()`.

## Adding an `expect`

1. Declare it in `commonMain` in `<Name>.kt`, with a KDoc saying what each platform should do.
2. Add `<Name>.desktop.kt`, `<Name>.android.kt` and `<Name>.wasmJs.kt` beside it in each source set, same package.
   When desktop and Android do the same thing, one `<Name>.jvmShared.kt` in `jvmSharedMain` covers both.
3. Add a row to the table above.

`jvmSharedMain` is a source set that desktop and Android both build from, added in `composeApp/build.gradle.kts`
with `applyDefaultHierarchyTemplate { group("jvmShared") }`. It holds the OkHttp engine and the certificate checks
in `PinningTrustManager.kt`, and OkHttp is a dependency there.

Calls in `composeApp` always bind to `composeApp`'s own `actual`s at compile time. An `actual` has to live in the
same module as its `expect`, so `androidApp` can't supply one.

## Android

The manifest asks for `INTERNET` and allows plain HTTP (`usesCleartextTraffic`), since a server can still be
reached on its HTTP port. Remove that once servers redirect HTTP to HTTPS everywhere.

## Web

A real server serves the web client itself (see [Running a server](../operations.md#web-client)), so the page and
the API share one address. With no address saved, the web client tries the origin it was loaded from and keeps it
if a TicketMan server answers there. Logs go to the browser console.

## Known gaps

- **Android hasn't been run end to end yet.** It compiles, but hasn't been opened on a device. That's the
  [fix plan's last phase](../FIX-PLAN.md#phase-11-android-and-other-machines).
- **Web has been run in headless Chromium only**, served by `-PwithWeb`: login, records, a note with a photo, the
  counter search, ticket card, check-in, printing (the hidden iframe gets the right receipt), backup download and
  restore upload, the admin tabs and settings all worked. Firefox and Safari haven't been tried, and neither has
  kiosk mode's full screen, which a headless browser can't show.
- **`androidApp` needs compileSdk 37** (FileKit 0.16 requires it). Building the APK also needs an AAPT2 that runs
  on the build machine: Gradle downloads the x86 Linux one, which fails on ARM Linux.
- **iOS is parked.** The source sets and `iosApp` exist but the targets are commented out, and the `iosMain`
  files lack several `actual`s. See [decisions](../decisions.md#ios-parked-not-removed).

## See also

- [Overview](../architecture/overview.md)
- [Transfers and photos](transfers-and-photos.md)
- [Printing](printing.md)
- [Add platform-specific code](../how-to/add-a-platform-actual.md)
