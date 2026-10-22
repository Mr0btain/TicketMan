# Auth API

`/api/auth`, from [`AuthRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/AuthRoute.kt).
Everything that happens before there's a token: logging in, refreshing, asking for an account, finishing an invite,
and the station routes that prove who they are with a station's secret. How sessions and tokens work is in
[Authentication and sessions](../server/auth.md); conventions are in the [API overview](README.md).

## Logging in

### `POST /api/auth`

**Auth:** none

Logs in with a password, or an invite's setup code in place of the password.

- **Body:** `LoginRequest { username, password, deviceId, deviceName }`. Logging in again with the same `deviceId`
  replaces that device's session; a blank one adds a new session every time.
- **`202`** `AuthResponse { id, accessToken, refreshToken, status, setupToken }`, where `status` is:
  - `OK`: logged in,
  - `NEEDS_SETUP`: logged in, but the tokens only work on `setup` routes until `POST /api/user/setup`,
  - `INVITE_SETUP`: the setup code was right; no tokens, a 30 minute `setupToken` for `POST /api/auth/setup/invite`,
  - `PENDING_APPROVAL`: right password for an account request an admin hasn't approved; no tokens.
- **`401`** wrong username, password or code, or a deactivated user. Logged as `LOGIN_FAILED`.
- **`429`** `Too many wrong logins, try again in 30 seconds` (or however long), with a `Retry-After` header in
  seconds: 20 failures from this address in 15 minutes, this username locked after its 5 free tries, or this
  address guessed wrong during a server wide surge. Checked before the password, so the right one waits too. See
  [Wrong password limits](../server/auth.md#wrong-password-limits).

### `POST /api/auth/refresh`

**Auth:** none

Swaps a refresh token for a new access token and a new refresh token, on the same session. The one sent stops
working.

- **Body:** `RefreshTokenRequest { token }`.
- **`202`** `RefreshTokenResponse { token, refreshToken }`: the new access token, and the refresh token to send next
  time. It expires when the old one would have.
- **`401`** the token doesn't verify, is an access token, isn't the one stored for its session (already used, or
  the session ended), the session has expired or sat unused past a kiosk's idle limit, or its user isn't active.

### `POST /api/auth/logout`

**Auth:** setup

Ends the calling device's session, from the token's `sid`. Works mid setup too.

- **`202`** ended. **`404`** the session was already gone.

## New accounts

### `POST /api/auth/request-account`

**Auth:** none

Asks for an account from the login screen. It waits, `PENDING`, for an admin.

- **Body:** `AccountRequest { username, password, name, email, phone }`.
- **`202`** no body.
- **`400`** a bad username or a password under 8 characters. **`409`** the username is taken, or 50 requests are
  already waiting.
- **`429`** `Too many requests, try again later`: 5 an hour per address.

### `POST /api/auth/setup/invite`

**Auth:** none

Finishes an invite with the setup token from logging in with the code. Sets the password, details and PIN, then
logs them in.

- **Body:** `InviteSetupRequest { setupToken, password, name, email, phone, pin, deviceId, deviceName }`.
- **`202`** `AuthResponse` with tokens for a new session.
- **`400`** the setup token has expired, or the name, password or PIN isn't allowed. **`404`** the invite is gone.

## Stations

These take the station's `StationCredentials { stationId, secret }`, handed out once by
`POST /api/station/register`. See [PINs and stations](../server/pins-and-stations.md).

### `POST /api/auth/pin`

**Auth:** none

Signs someone in at a station with their PIN. Ends the previous PIN session on that station.

- **Body:** `PinLoginRequest { station, userIdentifier, pin }`.
- **`202`** `AuthResponse` for a 12 hour PIN session.
- **`401`** `Wrong PIN, 3 tries left` (or just `Wrong PIN` when it doesn't count, like no PIN set or not a user
  who can sign in). **`423`** the PIN is locked after five wrong tries.
- **`403`** not a registered station, or not approved yet.

At a station with kiosk mode on, the session also ends once no request has reached it for the station's idle
timeout plus 10 minutes. See [Kiosk sessions on the server](../server/pins-and-stations.md#kiosk-sessions-on-the-server).

### `POST /api/auth/kiosk/leave`

**Auth:** admin

An admin left kiosk mode on a station and is carrying on with the app there. Takes the idle limit off their PIN
session, from the token's `sid`, so it doesn't end when the kiosk would have locked. The 12 hours still stand.

- **`202`** done, with an audit entry `Left kiosk mode at <station>`.
- **`404`** the calling session isn't a PIN session.

### `POST /api/auth/station/status`

**Auth:** none

The station's own record, so a device can see whether it's been approved and its kiosk settings.

- **Body:** `StationCredentials`.
- **`200`** `Station`. **`404`** unknown station or wrong secret; the client then forgets its credentials.

### `POST /api/auth/station/staff`

**Auth:** none

Who can PIN in, for the station's sign in screen: active, set up users with a PIN that isn't locked. Sorted by
name.

- **Body:** `StationCredentials`.
- **`200`** a list of `StaffMember { identifier, username, name }`.
- **`403`** not a registered station, or not approved yet.

## See also

- [Authentication and sessions](../server/auth.md)
- [Users and accounts](../server/users-and-accounts.md)
- [Users API](user.md), for `/api/user/setup` and `/api/user/me`
