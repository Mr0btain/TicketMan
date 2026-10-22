# DTOs, caches and platform helpers

The rest of `shared`: the request and response bodies that aren't plain models, the LRU cache, and the
`expect`/`actual` helpers for logging, platform and data folders.

## Requests and responses

When a route needs more than one model, or something that isn't a model, its body is a class in
`shared/.../server/request/` or `server/response/`. Being in `shared` means a route and the client call that hits
it can't disagree on the shape.

### Requests

| Class | Sent to | Holds |
|-------|---------|-------|
| `LoginRequest` | `POST /api/auth` | Username, password, and this device's id and name |
| `RefreshTokenRequest` | `/api/auth/refresh` | The refresh token |
| `AccountRequest` | `/api/auth/request-account` | Asking for an account |
| `InviteRequest`, `ApproveRequest` | `/api/user/invite`, `/api/user/approve/{id}` (admin) | Making an invite, approving a request with an access level |
| `AccountSetupRequest` | `/api/user/setup` | First login setup for an approved request: details, password, PIN |
| `InviteSetupRequest` | `/api/auth/setup/invite` | First login for an invite: the setup code, details, password, PIN |
| `PasswordResetRequest` | `/api/user/password/{id}` (admin) | A new password |
| `PinLoginRequest`, `SetPinRequest` | `/api/auth/pin`, `/api/user/me/pin` | PIN login with station credentials; setting your own PIN |
| `StationRegistrationRequest` | `/api/station/register` | Device id and a name |
| `CheckInRequest` | `/api/counter/check-in` | Ticket, customer, tech, deposit and signature, saved together |
| `PickupRequest` | `/api/counter/pickup` | Ticket and the payments taken |
| `InvoiceUpdateRequest`, `PaymentRequest` | `/api/invoice/...` | Charges and discount; one payment |
| `RepeatCheckRequest` | `/api/counter/repeat-check` | Customer and device, to flag a repeat repair |
| `BackupRequest`, `SeedRequest` | `/api/admin/...` | Whether to include photos; how much test data |
| `PagedRequest`, `SearchQuery` (in `data/`) | The server, from the `?offset=&limit=` and `?query=&limit=` of paged reads and searches | Offset and page size; query text and limit |

### Responses

| Class | From | Holds |
|-------|------|-------|
| `AuthResponse` | `POST /api/auth`, PIN login, invite setup | User id, tokens, and a `LoginStatus` (`OK`, `NEEDS_SETUP`, `INVITE_SETUP`, `PENDING_APPROVAL`) |
| `RefreshTokenResponse` | Refresh | New access token |
| `InviteResponse`, `PendingUserResponse` | Admin user routes | The invite code and expiry; requests and invites waiting |
| `CheckInResponse` | Check-in | The saved ticket and its invoice |
| `CounterSearchResponse`, `CounterQueueResponse` | Counter | Search hits; the queue buckets (ready, due today, overdue, just checked in) |
| `DailyReport` | `/api/admin/report/daily` | A day's takings by payment method, sales, tax and ticket counts |
| `AdminStatsResponse` | `/api/admin/stats` | The admin overview's numbers |
| `SessionResponse` | `/api/admin/sessions` | One session: who, which device, `SessionKind` (`PASSWORD` or `PIN`), times |
| `StationRegistrationResponse` | Station register | The station and its credentials (the only time the secret is sent) |
| `BackupTicket`, `BackupManifest`, `PendingRestore` | Backup and restore | Download link details; what's in a backup; a restore waiting for restart |
| `ServerInfoResponse` | `/api/admin/server` | Name, version, uptime, data path and sizes, Java and OS, dev mode, backups, and the proxy diagnostics (`trustedProxies`, `clientAddress`, `forwarded`, `timeZone`) |
| `LoginLimitsResponse`, `LoginLock` | `/api/admin/logins` | Usernames and addresses locked out for wrong passwords, and whether the server wide ceiling is hit |

`BackupManifest.CURRENT_FORMAT_VERSION` is the backup format. Bump it when the archive layout changes; the server
refuses to restore backups with a newer version than it knows.

## `LruCache`

[`LruCache<K, V>(maxSize)`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/data/cache/LruCache.kt) is a
small least recently used cache with O(1) `get`, `put` and `remove`. Reading an entry makes it the most recent;
going over `maxSize` drops the oldest.

It's **not thread safe**. Even `get` reorders entries. The client's caches (`CustomCache` and its subclasses) use it
from the main thread only.

## Platform helpers

`shared` has `actual`s for Android, JVM (used by both the desktop client and the server) and Wasm.

| Declaration | What it gives | Per platform |
|-------------|---------------|--------------|
| `AppLogger` (`logging/`) | Logging, see [Results and errors](results-and-errors.md#logging) | JVM: `java.util.logging` and stdout. Android and Wasm: their own consoles |
| `AppDir` (`util/`) | `path`: where the app keeps its files. `currentPlatform` | JVM: per OS app data folder ([table](../configuration.md#data_path)). Android: the app's data dir. Wasm: `null`, a browser has no folder |
| `getPlatform()` (root) | A `Platform` whose `name` is the suggested device name | JVM: `COMPUTERNAME` or `HOSTNAME`, else "<OS> computer". Android: the maker and model. Wasm: "Web browser" |
| `util.Platform` enum | `Linux`, `MacOS`, `Windows`, `Android`, `IOS`, `WasmJs` | - |

There are two things named `Platform`: the interface returned by `getPlatform()` (in the root package) and the enum
in `util/`. They're unrelated.

`Constants.kt` has `SERVER_PORT = 8080`, the default port.

## Tests

`shared/src/commonTest` covers `Money`, `InvoiceMath`, `LruCache`, the UUID time helpers and the username and PIN
rules. Run them with `./gradlew :shared:jvmTest`.

## See also

- [Domain models](domain-models.md)
- [Results and errors](results-and-errors.md)
- [Money](money.md)
