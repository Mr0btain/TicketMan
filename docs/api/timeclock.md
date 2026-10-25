# Time clock API

`/api/timeclock`, from [`TimeClockRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/TimeClockRoute.kt).
Staff clock in and out with their PIN at a station, or from their own device; admins read timesheets and fix times.
Rules are in `TimeClockService`, rows in `time_entries` (`TimeClockDatabase`), each a
`TimeEntry { identifier, userIdentifier, userName, clockedInAt, clockedOutAt, station, note, editedBy }` (times in
epoch millis, `clockedOutAt` null while on the clock, `station` blank from someone's own device, `editedBy` the admin
who last changed it). Clocking toggles: on the clock means out, off it means in. Clocks are taken one at a time
across the server, so a double tap can't open two. Every clock in, out and change is audit logged.

## Clocking

### `POST /api/timeclock/punch`

**Auth:** none

At a station, by PIN, with no login, so a locked [kiosk](../ui/screens/kiosk.md) can do it. The station and PIN are
checked exactly like [PIN login](auth.md#post-apiauthpin) (`UserService.checkStationPin`): a wrong PIN counts towards
the lock and is audit logged.

- **Body:** `PinLoginRequest { station, userIdentifier, pin }`.
- **`200`** `PunchResult { entry, clockedIn }`, the stretch started or ended. `station` is the station's name.
- **`401`** wrong PIN (`Wrong PIN, 3 tries left`), no PIN, or not someone who can sign in. **`403`** not a station,
  or one waiting for approval. **`423`** the PIN is locked.

### `POST /api/timeclock/me/punch`

**Auth:** restricted

The caller, from their own device (the user menu or Settings).

- **`200`** `PunchResult`, with a blank `station`.

### `GET /api/timeclock/me`

**Auth:** restricted

- **`200`** `ClockState { entry }`, the caller's open stretch, or `entry` null when they're off the clock.

### `GET /api/timeclock/me/timesheet`

**Auth:** restricted

The caller's own time, as [`/timesheet`](#get-apitimeclocktimesheet).

- **Query:** `from`, `to`. **`400`** as for `/timesheet`.
- **`200`** `Timesheet` with only them.

### `GET /api/timeclock/now`

**Auth:** restricted

Who's on the clock now, earliest first.

- **`200`** a list of `TimeEntry`.

## Timesheets

### `GET /api/timeclock/timesheet`

**Auth:** admin

Time that started from one day to another, both included, in the shop's time zone.

- **Query:** `from` and `to`, days like `2026-10-26`; `user`, only theirs. **`400`** a missing or bad day, `to`
  before `from`, more than 92 days, or a `user` that isn't an id.
- **`200`** `Timesheet { from, to, people, generatedAt }`, `people` being a `TimesheetPerson { userIdentifier, name,
  hours, entries }` for each person with time, by name. Open stretches count up to `generatedAt`.

### `POST /api/timeclock/entry/add`

**Auth:** admin

Adds time by hand, like a forgotten clock in. `editedBy` is the admin.

- **Body:** `TimeEntry`, with an id the client made. `clockedOutAt` null leaves them on the clock.
- **`201`** the saved `TimeEntry`.
- **`400`** no such user, no clock in, out before in, longer than 24 hours, in the future, a note over 500
  characters, overlapping their other time, or an open one when they're on the clock already. **`409`** an entry
  with that id exists.

### `POST /api/timeclock/entry/update`

**Auth:** admin

Changes the times and note. Whose time it is stays.

- **Body:** `TimeEntry`.
- **`200`** the saved `TimeEntry`. **`400`** as for `/entry/add`. **`404`** no such entry.

### `DELETE /api/timeclock/entry/remove/{identifier}`

**Auth:** admin

- **`202`** deleted. **`404`** no such entry.

## See also

- [PINs and stations](../server/pins-and-stations.md)
- [Kiosk](../ui/screens/kiosk.md#clocking-in-and-out)
- [Admin screen: Timesheets](../ui/screens/admin.md#tabs)
