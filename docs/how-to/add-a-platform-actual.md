# Add platform-specific code

Something common code needs that each platform does its own way, like printing, saving a file or where to look for
a server. Kotlin Multiplatform's `expect`/`actual` declares it once in common code and implements it per platform.
[Platforms](../client/platforms.md) lists every one there is and what each platform does. The example here is the
existing `defaultServerAddress()`.

## 1. Pick the shape

- **`expect fun`** for a single call: `defaultServerAddress()`, `printDocument(...)`, `saveDownload(...)`. The
  common case.
- **`expect val` / `expect class`** for a value or a type: `tmShowsScrollbars`, `BuildConfigImpl`.
- **A Koin binding** in `platformModule` for an object other classes are built with, like the `KSafe` store. Its
  `actual` is a Koin `module { }` per platform.
- **An interface passed in** when tests need to swap it: `RemoteHandler` takes an `HttpClientEngine`, so tests
  give it a `MockEngine` while the app gives it `getHttpEngine(...)`. Prefer this for anything you'll want to fake.

## 2. Declare it in common code

In `composeApp/src/commonMain/.../<package>/<Name>.kt`, with a KDoc saying what each platform should do:

```kotlin
/**
 * Where to look for a server before one is saved. On web that's the address the page came from, since the server
 * serves the web client itself. On a desktop debug build it's a development server on this machine
 * (`http://localhost:8080`). Blank elsewhere
 */
expect fun defaultServerAddress(): String
```

Keep the signature to common types. Anything platform specific (a `Context`, a browser `File`) stays inside the
`actual`s.

## 3. Write each `actual`

Same package, one file per source set, named `<Name>.<platform>.kt`:

| Source set | Folder | Builds into |
|------------|--------|-------------|
| `desktopMain` | `composeApp/src/desktopMain/kotlin/...` | Desktop |
| `androidMain` | `composeApp/src/androidMain/kotlin/...` | Android |
| `wasmJsMain` | `composeApp/src/wasmJsMain/kotlin/...` | Web |
| `jvmSharedMain` | `composeApp/src/jvmSharedMain/kotlin/...` | Desktop **and** Android |

When desktop and Android do the same thing, write one `actual` in `jvmSharedMain` instead of two. That's what
`getHttpEngine` does, since both use OkHttp:

```kotlin
// jvmSharedMain/.../components/network/HttpEngine.jvmShared.kt
actual fun getHttpEngine(trust: ServerTrust): HttpClientEngine = OkHttp.create { ... }

// wasmJsMain/.../components/network/HttpEngine.wasmJs.kt
actual fun getHttpEngine(trust: ServerTrust): HttpClientEngine = Js.create()
```

`defaultServerAddress` is the other way round: desktop and Android differ (a debug desktop build looks for a
server on `localhost`), so each has its own `actual` in `desktopMain` and `androidMain`.

`jvmSharedMain` is set up in `composeApp/build.gradle.kts` (`applyDefaultHierarchyTemplate { group("jvmShared") }`)
and has OkHttp as a dependency. A platform needing a library adds it to that source set's dependencies there.

The `actual`s must be in `composeApp`, the same module as the `expect`. `androidApp` can't supply one; it can only
pass things in (like `AndroidPrintHost.attach` from `MainActivity`).

## 4. Compile every target

Each target only checks its own `actual`s, so build them all:

```bash
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileKotlinWasmJs :composeApp:compileAndroidMain
```

A missing `actual` fails the target it's missing from with "expected ... has no actual declaration". iOS needs one
too, in `iosMain`, but its targets only exist on a Mac: from Linux, GitHub's CI (`iOS compile`) is what catches a
missing or broken one.

## 5. Test what you can

- `commonTest` runs on the JVM, so it gets the desktop or `jvmShared` `actual`. Test common code that calls the
  `expect` there.
- Desktop specifics go in `desktopTest`, like `Java2DPrintLayoutTest` and `PinnedHttpsTest`.
- Android and web `actual`s have no tests; try them by running the app on each. See
  [Getting started](../getting-started.md).

## 6. Docs

Add a row to the [`expect` declarations table](../client/platforms.md#expect-declarations) saying what each
platform does.

## Gotchas

- **Web can't block.** There are no threads to wait on in the browser, so an `actual` doing I/O there has to be
  `suspend` or callback based. Make the `expect` `suspend` if any platform needs it.
- **Web reads files whole.** Browser files can't be streamed from Kotlin/Wasm the way the JVM streams them, which
  is why `fileBody` on web reads the file into memory and `fileBodyLimitBytes` caps it. Check `isTooBigToSend`
  before uploading anything large.
- **Desktop is also "jvm".** `shared`'s jvm target is used by both the desktop client and the server, so it can't
  tell them apart. For something only the desktop client does, put the `actual` in `composeApp`'s `desktopMain`,
  like `alwaysWide`, rather than checking `getPlatform().name`.

## See also

- [Platforms](../client/platforms.md)
- [Getting started](../getting-started.md)
