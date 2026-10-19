# Users API

`/api/user`, from [`UserRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/UserRoute.kt).
Staff accounts: looking them up, an admin managing them, approving requests and invites, PINs, and a user's own
first login setup. Passwords never leave the server; every `User` sent back has a blank `password`
(`withoutPassword()`). What statuses and access levels mean is in
[Users and accounts](../server/users-and-accounts.md). Staff notes are on the [Notes](notes.md#users) page.

## You

### `GET /api/user/me`

**Auth:** setup

The calling user, from the token. Works mid setup, so the client can tell setup is needed.

- **`200`** `User`. **`404`** the user is gone.

### `POST /api/user/setup`

**Auth:** setup

First login after an approved account request: confirm details, and optionally a new password, and set a PIN.

- **Body:** `AccountSetupRequest { name, email, phone, password, pin }`. A blank `password` keeps the one they
  asked with.
- **`202`** the updated `User`, with `needsSetup` off; their tokens now work everywhere.
- **`400`** no name, a password under 8 characters, or a PIN `PinRules` refuses. **`409`** already set up.

### `POST /api/user/me/pin`

**Auth:** restricted

Changes your own PIN. Needs your password, so someone at an unlocked screen can't.

- **Body:** `SetPinRequest { currentPassword, pin }`.
- **`202`** no body. **`400`** `Password is wrong`, or the PIN isn't allowed.

## Looking users up

### `POST /api/user/get`

**Auth:** normal

- **Body:** a user id. **`200`** `User`. **`404`** no such user.

### `POST /api/user/fetch`

**Auth:** normal

A page of users.

- **Body:** `PagedRequest`. **`200`** a list of `User`. **`500`** if the database read failed.

### `POST /api/user/search`

**Auth:** normal

Users whose name, username or id starts with the query. Used to pick a technician.

- **Body:** `SearchQuery`. **`200`** a list of `SearchResult`.

## Managing users

### `POST /api/user/add`

**Auth:** admin

Adds a user who can log in straight away (`ACTIVE`).

- **Body:** `User` with a plain `password`, which the server hashes.
- **`201`** `UserResponse { identifier, accessLevel }`.
- **`400`** no username or password, a username `UsernameRules` refuses, a password `PasswordRules` refuses (under
  8 characters), or `accessLevel` `NONE`. **`409`** the username is taken.

### `POST /api/user/update`

**Auth:** admin

Changes everything but the password. Making someone anything other than `ACTIVE` ends all their sessions.

- **Body:** `User`. Its `password` is ignored.
- **`202`** saved.
- **`400`** no username, a new username `UsernameRules` refuses (an unchanged one is left alone, even if it's from
  before the rules), `accessLevel` `NONE` (deactivate with `status` instead), or setting someone back to `PENDING`.
  A pending user stays pending until approved, whatever is sent.
- **`409`** the username is taken, you changed your own access or status, or it would leave no active admin.
- **`404`** no such user.

### `POST /api/user/password/{identifier}`

**Auth:** admin

Sets a new password and logs that user out of every device (except this one, if it's your own).

- **Body:** `PasswordResetRequest { password }`.
- **`202`** done. **`400`** a password under 8 characters. **`404`** no such user.

### `DELETE /api/user/remove/{identifier}`

**Auth:** admin

Deletes a user, their sessions and their staff notes. Tickets assigned to them stay, unassigned.

- **`202`** deleted. **`404`** no such user. **`409`** it's you, or the last admin.

## PINs

### `POST /api/user/pin/{identifier}/unlock`

**Auth:** admin

Unlocks a PIN after five wrong tries, keeping the PIN.

- **`202`** unlocked. **`404`** no such user.

### `DELETE /api/user/pin/{identifier}`

**Auth:** admin

Clears the PIN. They set a new one with `POST /api/user/me/pin`.

- **`202`** cleared. **`404`** no such user.

## Requests and invites

### `GET /api/user/pending`

**Auth:** admin

Accounts waiting on someone.

- **`200`** a list of `PendingUserResponse { user, kind, inviteExpiresAt }`, where `kind` says request or invite.

### `POST /api/user/approve/{identifier}`

**Auth:** admin

Approves an account request. They log in with the password they asked with and finish setup.

- **Body:** `ApproveRequest { accessLevel }`.
- **`202`** approved. **`400`** `accessLevel` `NONE`. **`404`** not a pending user. **`409`** it's an invite,
  which activates itself.

### `DELETE /api/user/reject/{identifier}`

**Auth:** admin

Turns down a request, or cancels an invite. The pending user is removed.

- **`202`** done. **`404`** not a pending user.

### `POST /api/user/invite`

**Auth:** admin

Makes a pending user and a setup code for them to log in with in place of a password.

- **Body:** `InviteRequest { username, name, email, phone, accessLevel }`.
- **`201`** `InviteResponse { user, code, expiresAt }`. The code is only sent this once.
- **`400`** a bad username or `accessLevel` `NONE`. **`409`** the username is taken.

### `POST /api/user/invite/{identifier}/renew`

**Auth:** admin

Replaces an invite's code with a new one; the old one stops working.

- **`202`** `InviteResponse`. **`404`** not a pending user. **`409`** it's an account request, not an invite.

## Gotchas

- **Restricted users can't list users.** `/get`, `/fetch` and `/search` need Normal access, unlike the other
  record types, where Restricted can read.

## See also

- [Users and accounts](../server/users-and-accounts.md)
- [Auth API](auth.md)
- [Notes API](notes.md#users)
