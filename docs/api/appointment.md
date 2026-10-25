# Appointments API

`/api/appointment`, from [`AppointmentRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/AppointmentRoute.kt).
The calendar: times customers are coming in with a device. Anyone logged in can look; Normal access books and
changes. Rules are in `AppointmentService`, rows in `appointments` (`AppointmentDatabase`). An appointment becomes a
ticket by checking it in at the counter with its id
([`POST /api/counter/check-in`](counter.md#post-apicountercheck-in)'s `appointmentIdentifier`), which marks it
`ARRIVED` with the ticket in the same transaction.

`Appointment { identifier, startsAt, minutes, customerIdentifier, customerName, phone, email, device, problem, notes,
technicianIdentifier, technicianName, status, ticketIdentifier, reminderSentAt, createdBy, createdAt }`. `startsAt`
is epoch millis; `minutes` 5 to 480 (30 by default). Someone not on file yet is just `customerName` with a phone or
email; with `customerIdentifier` the name comes from their record. `technicianName`, `createdBy`, `createdAt`,
`status`, `ticketIdentifier` and `reminderSentAt` are the server's. `status` is `BOOKED`, `ARRIVED` (checked in),
`NO_SHOW` or `CANCELLED`.

A day before (`AppointmentRules.REMINDER_HOURS`, 24) the hourly sweep sends the customer the
[`APPOINTMENT_REMINDER`](notify.md) message, once, when the shop turned it on and the appointment has a customer who
agreed to messages. Moving an appointment to another time sends it again.

## Reading

### `GET /api/appointment/range`

**Auth:** restricted

Appointments starting from one day to another, both included, in the shop's time zone, earliest first. Every
status, so the calendar can show what was cancelled.

- **Query:** `from` and `to`, days like `2026-10-26`. **`400`** either missing or not a day, `to` before `from`,
  or more than 62 days.
- **`200`** a list of `Appointment`.

### `GET /api/appointment/get/{identifier}`

**Auth:** restricted

- **`200`** `Appointment`. **`404`** no such appointment.

## Changing

### `POST /api/appointment/add`

**Auth:** normal

Books one. Audit logged.

- **Body:** `Appointment`, with an id the client made. It starts `BOOKED` whatever was sent.
- **`201`** the saved `Appointment`.
- **`400`** no time, a length out of range, no customer and no name, a customer that doesn't exist, a technician
  without Normal access, or text that's too long. **`409`** an appointment with that id already exists.

### `POST /api/appointment/update`

**Auth:** normal

Saves the time, length, customer, contact details, device, problem, notes and technician of a booked one. The
status stays as it is.

- **Body:** `Appointment`.
- **`200`** the saved `Appointment`. **`400`** as for `/add`. **`404`** no such appointment. **`409`** it isn't
  booked any more (checked in, a no show or cancelled).

### `POST /api/appointment/status/{identifier}`

**Auth:** normal

Cancels it, marks it a no show, or books it again. Audit logged.

- **Body:** `AppointmentStatusRequest { status }`.
- **`200`** the `Appointment`. **`400`** `ARRIVED`, which only check in sets. **`404`** no such appointment.
  **`409`** it was checked in already.

### `DELETE /api/appointment/remove/{identifier}`

**Auth:** normal

Deletes it. A ticket made from it stays. Audit logged.

- **`202`** deleted. **`404`** no such appointment.

## See also

- [Appointments screen](../ui/screens/appointments.md)
- [Counter API: check-in](counter.md#post-apicountercheck-in)
- [Messages API](notify.md)
