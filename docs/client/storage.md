# Storage

What the client keeps on the device between runs, and where. There's no local database: records always come from
the server. The device only keeps settings, login tokens, this device's identity and a photo cache.

## `LocalStorage` and `KeyValueStore`

[`LocalStorage`](../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/components/data/local/LocalStorage.kt)
is a class of typed properties over a string key value store:

```kotlin
var pageSize: Int
    get() = _pageSize
    set(value) {
        _pageSize = value
        store.putString(PAGE_SIZE_KEY, value.toString())
    }
```

Everything is read once when it's built and kept in memory; setting a property writes it through straight away.

[`KeyValueStore`](../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/components/data/local/KeyValueStore.kt)
is the interface underneath (`getString`, `putString`, `delete`), so `LocalStorage` isn't tied to a library:

| Implementation | Used by | Backed by |
|----------------|---------|-----------|
| `KSafeKeyValueStore` | The app | KSafe (`eu.anifantakis:ksafe`): encrypted storage per platform, built in each platform's `platformModule` |
| `InMemoryKeyValueStore` | Tests (`composeApp/src/commonTest`) | A map |

`KSafeKeyValueStore` uses KSafe's direct (non suspending) calls, because Ktor's `loadTokens` isn't suspending. They
read KSafe's in-memory cache and write to disk in the background. On the web KSafe loads asynchronously, so
`main()` waits for `awaitCacheReady()` before showing the app; reads before that would get defaults.

## What's kept

| Key area | Properties | Notes |
|----------|------------|-------|
| Session | `accessToken`, `refreshToken` | Cleared by `clearSession()` on logout or when refreshing fails |
| Credentials | `username`, `password` | Only saved when **Remember login** is on. Logout clears the password and keeps the username to prefill the form. `clearCredentials()` removes both |
| Server | `serverUrl` | |
| This device | `deviceId`, `deviceName` | `deviceId` is a random UUID made the first time it's read. `deviceName` defaults to the platform's name |
| Station | `stationCredentials` | The station id and secret, set when the device registers as a station |
| Appearance | `darkMode`, `themeId`, `fontId`, `reduceTransparency` | |
| Lists | `pageSize`, `searchLimit`, `cacheSize`, `defaultTab`, `ticketScope` | |
| Login | `rememberLogin` | |

`SettingsHandler` is the only thing screens use to change settings; it exposes each as a `StateFlow` and writes it
through `LocalStorage`.

## The photo cache

Photos are cached by Coil, not `LocalStorage`: 15% of memory, plus a 250 MB disk cache from `imageDiskCache()` in
the platform's cache folder (none on the web, where the browser caches). Photos never change, so cached copies are
used without asking the server. See [Transfers and photos](transfers-and-photos.md).

## Adding a setting

1. Add a private backing field, a `KEY` constant and a default in `LocalStorage`'s companion.
2. Add the property with a setter that writes through, and read it in the `init` block.
3. Expose it from `SettingsHandler` as a `StateFlow` with a setter, and use that from the screen.

## Gotchas

- **The password is stored** (encrypted by KSafe) when Remember login is on, so the client can log back in when a
  refresh token is refused. Turn Remember login off on shared devices.
- **Values are trimmed when saved**, including the password. A password with a space at either end is saved
  without it, and logging back in with the saved one then fails.
- **Clearing app data loses the device id and station credentials.** The device then looks new to the server: old
  sessions stay listed until removed, and the station has to be registered and approved again.

## See also

- [App state and handlers](state.md)
- [Networking](networking.md)
