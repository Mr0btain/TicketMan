# Time off API

`/api/timeoff`, from [`TimeOffRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/TimeOffRoute.kt).
Staff ask for time off against a yearly allowance in hours that admins set; admins approve, which puts it on the
[staff schedule](schedule.md), or decline. `TimeOffService` runs it, with days in the shop's time zone.

**Allowances and balances.** Each person's allowance is hours a year (`time_off_allowances`, none until set). It
starts over on 1 January with nothing carried over. A `TimeOffBalance { allowanceHours, takenHours, pendingHours }`
for a year counts the time off entries on the schedule starting in it (their `timeOffHours`, so time off an admin
writes straight onto the schedule counts too) and the requests still waiting; `leftHours` is the allowance less both.
A request counts in the year its first day is in.

**Requests.** A `TimeOffRequest { startsAt, endsAt, hours, note }` is whole days, saved midnight to midnight like a
time off entry, and `hours` is what it takes (the app suggests 8 a weekday, fewer for part of a day). It's
`PENDING` until answered, once: `APPROVED` (with the schedule entry it made as `entryIdentifier`), `DECLINED` (with a
`reason`) or `WITHDRAWN` by whoever asked. Approving writes a `TIME_OFF` entry for the same days with the request's
hours as `timeOffHours` and its id as `requestIdentifier`, at the person's home location, in the same transaction.

### `GET /api/timeoff/me`

**Auth:** restricted

- **Query:** `year`, this year when it isn't given.
- **`200`** `MyTimeOff { balance, requests }`, their newest 50 requests. **`400`** a year that isn't one.

### `POST /api/timeoff/request`

**Auth:** restricted

- **Body:** `TimeOffRequest`; who it's for is the caller.
- **`201`** the request, waiting. Audit logged.
- **`400`** what `TimeOffRequest.problem` says: no first day, a last day before it, over 60 days, no hours, more hours
  than the days have, a note over 500 characters.
- **`409`** no allowance set for the year, or more hours than are left (`Only 40h left for 2027, with 8h waiting`).

### `POST /api/timeoff/withdraw/{identifier}`

**Auth:** restricted

- **`200`** the request, withdrawn. **`404`** not the caller's. **`409`** it was already answered.

### `GET /api/timeoff/requests`

**Auth:** admin

- **Query:** `status`, like `PENDING`, for only those.
- **`200`** requests, newest first, up to 200. **`400`** a status that isn't one.

### `POST /api/timeoff/approve/{identifier}`

**Auth:** admin

- **Body:** `TimeOffDecision` (its reason isn't used).
- **`200`** the request, approved, its time off on the schedule. Audit logged. **`404`** no such request.
- **`409`** it was already answered, or answered meanwhile.

### `POST /api/timeoff/decline/{identifier}`

**Auth:** admin

- **Body:** `TimeOffDecision { reason }`, which they see with the request; blank for none.
- **`200`** the request, declined. Audit logged. **`400`** a reason over 500 characters. **`404`** no such request.
  **`409`** it was already answered.

### `GET /api/timeoff/balances`

**Auth:** admin

- **Query:** `year`, this year when it isn't given.
- **`200`** every active user's `TimeOffBalance`, by name.

### `POST /api/timeoff/allowance/{identifier}`

**Auth:** admin

- **Body:** `TimeOffAllowance { hours }`, 0 to 2000.
- **`200`** their balance for this year. Audit logged. **`400`** out of range. **`404`** no such user.
