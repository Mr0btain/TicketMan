# Staff schedule API

`/api/schedule`, from [`ScheduleRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/ScheduleRoute.kt).
Who works when: shifts, time off, training and anything else, drawn on the calendar's Day, Week, Month and Staff
views beside the [appointments](appointment.md). Anyone logged in reads it; admins write it. `ScheduleService` runs
it, with days in the shop's time zone (the server's `TZ`).

**Entries.** A `ScheduleEntry { identifier, userIdentifier, kind, startsAt, endsAt, allDay, note, seriesIdentifier,
locationIdentifier }`, times in epoch millis with `endsAt` exclusive. `kind` is `SHIFT`, `TIME_OFF`, `TRAINING` or
`OTHER` (`ScheduleKind`, each with its color as a `StatusTone`); `TIME_OFF` is whole days. A whole day entry runs
midnight to midnight, however it's sent: from the start of its first day to the start of the day after its last.
A timed one is up to 24 hours and may run past midnight; its hours count on the day it starts
(`ScheduleRules.hoursByDay`). `userName` is filled in from the user. `locationIdentifier` is where it is, the
[location](location.md) the admin works at when it's not given; reads keep to the location the request works at.
`timeOffHours` is what a time off entry takes from the person's [allowance](timeoff.md) (0 for none), and
`requestIdentifier` the request approving it made.

**Weekly repeats.** Adding with a `ScheduleRepeat { weekdays, until }` (ISO weekdays, 1 Monday to 7 Sunday, and the
last day) writes a copy on each chosen weekday from the entry's first day to `until`, at the same local time of day
for the same length, all sharing the first one's id as `seriesIdentifier` (`ScheduleRules.expand`, up to 370).
Only timed entries repeat. Changes and removals take a `scope`: `ONE`, `FOLLOWING` (this one and later ones in its
series) or `SERIES` (all of it); the others take the changed one's person, kind, note and location, move by as much
as it moved, and take its length.

### `GET /api/schedule/range`

**Auth:** restricted

- **Query:** `from` and `to`, days like `2026-10-01`, both included, up to 93 days; `user` for one person's.
- **`200`** every `ScheduleEntry` touching those days, earliest first.
- **`400`** days that aren't days, `to` before `from`, too long a range, or a `user` that isn't an id.

### `GET /api/schedule/get/{identifier}`

**Auth:** restricted

- **`200`** the `ScheduleEntry`. **`404`** no such entry.

### `POST /api/schedule/add`

**Auth:** admin

- **Body:** `ScheduleRequest { entry, repeat }`.
- **`201`** every entry it made, one without a repeat. Audit logged.
- **`400`** what `ScheduleEntry.problem` says (no start, ending before it starts, a shift over 24 hours, time off
  over 366 days, a note over 500 characters), someone who isn't on the staff or is deactivated, time off with a
  repeat, or a repeat with no weekdays, a last day before the first, or too many entries.
- **`409`** an entry with that id exists.

### `POST /api/schedule/update`

**Auth:** admin

- **Query:** `scope`, `ONE` when it isn't given.
- **Body:** `ScheduleEntry`.
- **`200`** every entry changed. Audit logged.
- **`400`** as for adding, or a scope that isn't one. **`404`** no such entry.

### `DELETE /api/schedule/remove/{identifier}`

**Auth:** admin

- **Query:** `scope`, `ONE` when it isn't given.
- **`202`** taken off, with the rest of the scope. Audit logged. **`400`** a scope that isn't one. **`404`** no such
  entry.

## See also

- [Appointments API](appointment.md)
- [Time clock API](timeclock.md), for hours actually worked
