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
| `getHttpEngine()` | `components/network/HttpEngine.kt` | OkHttp | **`TODO()`** | **`TODO()`** |
| `BuildConfigImpl.isDebug()` | `components/debug/IsDebug.kt` | `-DsyncSphereDebug=true` system property | **`TODO()`** | **`TODO()`** |
| `platformModule` | `components/injection/PlatformModule.kt` | `KSafe` | `KSafe` with the app `Context` | `KSafe` |
| `initAppDir()` | `components/injection/InitAppDir.kt` | `AppDir()` | `AppDir(context)` | `AppDir()` |
| `printDocument(document, paper)` | `components/print/PrintDocument.kt` | Java2D `PrinterJob` | `WebView` + `PrintManager` | Hidden iframe |
| `saveFile(bytes, name, ext)` | `components/data/SaveFile.kt` | FileKit save dialog | FileKit save dialog | Browser download |
| `saveDownload(...)` | `components/network/transfer/Download.kt` | Save dialog, then stream | Save dialog, then stream | Browser download of the link |
| `fileBody(file, type)` | `components/network/transfer/Upload.kt` | Streams the file | Streams the file | Reads it whole |
| `shrinkForUpload(bytes)` | `components/image/ImageClient.kt` | Sends as is | Scales photos over 1.5 MB to 2560px JPEG | Sends as is |
| `imageDiskCache(context, size)` | `components/image/ImageClient.kt` | Coil disk cache in FileKit's cache dir | Coil disk cache in the app cache dir | None, the browser caches |
| `TmVerticalScrollbar(...)`, `tmShowsScrollbars` | `ui/kit/foundation/TmScrollbar.kt` | Desktop scrollbars, shown | None | None |

In `shared` (see [DTOs, caches and platform helpers](../shared/dtos-and-platform.md#platform-helpers)): `AppLogger`,
`AppDir`, `getPlatform()`.

## Adding an `expect`

1. Declare it in `commonMain` in `<Name>.kt`, with a KDoc saying what each platform should do.
2. Add `<Name>.desktop.kt`, `<Name>.android.kt` and `<Name>.wasmJs.kt` beside it in each source set, same package.
3. Add a row to the table above.

Calls in `composeApp` always bind to `composeApp`'s own `actual`s at compile time. An `actual` has to live in the
same module as its `expect`, so `androidApp` can't supply one.

## Known gaps

- **Android and web don't start.** `MainViewModel` gets its engine from `getHttpEngine()`, and `App()` calls
  `BuildConfigImpl().isDebug()`. Both are `TODO()` in `composeApp`'s Android and Wasm source sets, so the view model
  and `App()` throw `NotImplementedError` as soon as they're reached. Desktop is unaffected. Fixing it means OkHttp
  (Android) and the Js engine (web) in `getHttpEngine()`, and a real `isDebug()`.
- **Stale files in `androidApp`.** `androidApp/src/main/kotlin/.../components/` and `ui/composables/` hold older
  copies of `getHttpEngine` (OkHttp), `isDebug`, `initAppDir` and a scrollbar. They predate moving these into
  `composeApp`, and `composeApp` doesn't use them. Move what's useful into `composeApp/src/androidMain` and delete
  the rest.
- **The debug flag is named after an old project.** The desktop debug switch is the `syncSphereDebug` system
  property, passed through by `composeApp/build.gradle.kts`. With it set, the nav shows the component gallery.
- **iOS** source sets exist but the targets are commented out.

## See also

- [Overview](../architecture/overview.md)
- [Transfers and photos](transfers-and-photos.md)
- [Printing](printing.md)
