# Tickets API

`/api/data/ticket`, from [`TicketRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/TicketRoute.kt).
Anyone logged in can look; Normal access changes. A ticket's customer and technician are links, set with their own
routes rather than fields on the ticket. Devices coming in at the counter go through
[`POST /api/counter/check-in`](counter.md#post-apicountercheck-in) instead of `/add`, which saves the links,
deposit and signature with it. A ticket's notes are on the [Notes](notes.md#tickets) page. `deviceIdentifier` (the
customer's device it's on) and `groupIdentifier` (shared by tickets checked in together) are set by check in;
`/add` ignores them and `/update` leaves them alone. `checklist` is what was checked at check in
(`ChecklistItem { name, result }`, `result` being `WORKS`, `FAULTY` or `NOT_TESTED`), saved by `/update` too.
`passcodeSet` says whether the device's passcode is on file; it's the server's to set, through the routes below.
`warrantyUntil` is when the repair's warranty runs out, set at pickup, and `warrantyOf` the earlier ticket this is a
warranty return of, set at check in; neither comes from `/add` or `/update`. Work logged on a warranty return
without a price is free. `abandonedAt` is when the hourly sweep treated it as abandoned under the shop's storage
terms; `/update` clears it when the status is changed, and `/add` ignores it. `locationIdentifier` is the
[location](location.md) it's at: the one the request works at when it's added, changed only by
[moving it](#post-apidataticketmoveidentifier). Its `number` has that location's code as `prefix` (`DT-26-00000042`).

## Reading

### `GET /api/data/ticket/get/{identifier}`

**Auth:** restricted

- **`200`** `Ticket`. **`404`** no such ticket.

### `GET /api/data/ticket/fetch`

**Auth:** restricted

A page of the tickets at the [location](location.md) the request works at, newest first; every location's for an
admin working at `all`.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Ticket`.

### `GET /api/data/ticket/fetch/mine`

**Auth:** restricted

A page of the tickets assigned to the caller, who comes from the token, so nobody can list someone else's.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Ticket`.

Like `/fetch`, only the ones at the location the request works at.


### `GET /api/data/ticket/search`

**Auth:** restricted

Tickets matching a number (`26-42` exactly, `42` in any year), part of the id, the device's make, model or serial,
or the problem. Newest number first. A [status page](status.md) link, like the claim slip's QR scanned, finds just
its ticket (the counter search uses the same lookup).

- **Query:** `query`, `limit` (default 20, at most 200).
- **`200`** a list of `TmSearchResult`, the text being the number and device.

### `GET /api/data/ticket/number/{number}`

**Auth:** restricted

Looks a ticket up by the number on its claim slip, like `26-00000042`.

- **`200`** `Ticket`. **`404`** no ticket with that number, or not a ticket number.

### `GET /api/data/ticket/get/customer/{identifier}`

**Auth:** restricted

- **`200`** `EntityReference { identifier, text }` for the ticket's customer, `text` being their name.
  **`404`** no such ticket, or it has no customer.

### `GET /api/data/ticket/get/user/{identifier}`

**Auth:** restricted

- **`200`** `EntityReference` for the ticket's technician. **`404`** no such ticket, or nobody is assigned.

### `GET /api/data/ticket/get/history/{identifier}`

**Auth:** restricted

Every status the ticket has had, oldest first, with who changed it.

- **`200`** a list of `TicketStatusChange { status, time, changedBy, custom }`, where `custom` is the shop's own
  status it was set to, if any. **`404`** if it couldn't be read.

### `GET /api/data/ticket/signature/{identifier}`

**Auth:** restricted

The customer's signature from check-in.

- **`200`** `TicketSignature { strokes, aspectRatio, signedName, terms, signedAt, takenBy }`. Strokes are scaled
  0 to 1; `terms` is the text they agreed to, as it was then. **`404`** no such ticket, or it wasn't signed.

## Changing

### `POST /api/data/ticket/add`

**Auth:** normal

Adds a ticket and gives it the next number.

- **Body:** `Ticket`, with an id the client made. Its `number` is replaced by the next one, and its `statusToken`
  by a new random one.
- **`201`** the saved `Ticket`, with its number.
- **`400`** `New tickets can't start picked up`, or a `status.custom` that isn't one of the shop's statuses or is
  retired. **`500`** not saved.

### `POST /api/data/ticket/update`

**Auth:** normal

Replaces the ticket's device, status, priority, problem, condition, accessories, estimate and promised time. The
number, status token and pickup time stay the server's. A status change, including a move between two of the shop's own statuses
that count as the same built-in one, is added to the history.

- **Body:** `Ticket`. A `status.custom` (one of the shop profile's `statuses`) sets `status.status` to the built-in
  status it counts as, whatever was sent.
- **`202`** saved. **`404`** no such ticket.
- **`400`** a `status.custom` that isn't one of the shop's, or is retired, unless the ticket is already in it.
- **`409`** setting `PICKED_UP` (`Pick tickets up at the counter, so the invoice gets paid`), or changing a picked
  up ticket's status. Other changes to a picked up ticket are fine.

### `DELETE /api/data/ticket/remove/{identifier}`

**Auth:** normal

Deletes the ticket with its notes, work log, signature, history and technician link. Note photos are cleared up by
the next sweep. Its invoice goes too when it's open with nothing paid; one with money taken against it, or a void
one, is kept with the ticket's number (`ticketLabel`, `ticketRemovedAt`) and the audit entry says so. An admin can
delete it later with [`DELETE /api/invoice/{identifier}`](invoice.md#delete-apiinvoiceidentifier).

- **`202`** deleted. **`404`** no such ticket.

### `POST /api/data/ticket/set/customer/{identifier}`

**Auth:** normal

Links the ticket to a customer, in place of any it had.

- **Body:** the customer's id. **`202`** linked. **`404`** no such ticket or customer.

### `POST /api/data/ticket/set/user/{identifier}`

**Auth:** normal

Assigns the ticket to a technician, in place of whoever had it. Same rule as check-in: only active users with
Normal access or higher take tickets.

- **Body:** the user's id. **`202`** assigned.
- **`400`** `That technician can't take tickets`: no such user, or one who's Restricted, deactivated or not set up.
- **`404`** no such ticket.

### `POST /api/data/ticket/status-token/{identifier}`

**Auth:** normal

Gives the ticket a new `statusToken`, so the [status page](status.md) link handed out before stops working. Audit
logged.

- **`200`** the `Ticket` with its new token. **`404`** no such ticket.

### `POST /api/data/ticket/move/{identifier}`

**Auth:** normal

Sends the ticket to another location, like a device taken across town to a specialist. It keeps its number; its
invoice goes with it while it's open, so the parts come out of the new location's stock at pickup. Audit logged.

- **Body:** `MoveTicketRequest { locationIdentifier }`.
- **`200`** the `Ticket` at its new location (unchanged when it's there already). **`400`** not an active location.
  **`404`** no such ticket. **`409`** it was picked up.

## Work log

What was done on a ticket: time and parts. Each entry puts a line with the same identifier on the ticket's invoice,
see [Counter: work log](../server/counter.md#work-log).

### `GET /api/data/ticket/get/work/{identifier}`

**Auth:** restricted

- **`200`** a list of `WorkEntry { identifier, type, description, quantity, unitPriceCents, inventoryIdentifier,
  userIdentifier, userName, workedAt }`, oldest first. **`404`** no such ticket.

### `POST /api/data/ticket/add/work/{identifier}`

**Auth:** normal

Logs work and adds its line to the ticket's invoice, making the invoice if there isn't one. Both are saved together.

- **Body:** `WorkRequest { type, description, quantity, unitPriceCents, inventoryIdentifier }`. `type` is `LABOR`
  (`quantity` in hours) or `PART` (`quantity` a whole number, `inventoryIdentifier` needed). A null
  `unitPriceCents` uses the shop's `laborRateCents`, or the item's price. A part's blank description is the item's
  name. Labor lines aren't taxed and parts are; that can be changed on the invoice.
- **`201`** the saved `WorkEntry`, with who logged it and when. **`400`** a part without an item, an item that
  doesn't exist, a blank labor description, a quantity of zero or less or with more than two decimals, or a negative
  price. **`404`** no such ticket. **`409`** the ticket is picked up, or its invoice is paid or void.

### `POST /api/data/ticket/add/work/all/{identifier}`

**Auth:** normal

Logs several entries at once, like the time and the parts for one repair, each with its invoice line. All of them are
saved in one transaction, or none.

- **Body:** a list of `WorkRequest`, as for [`add/work`](#post-apidataticketaddworkidentifier).
- **`201`** the saved `WorkEntry`s, in the order sent. **`400`** an empty list, or any entry `add/work` would refuse.
  **`404`** no such ticket. **`409`** the ticket is picked up, or its invoice is paid or void.

### `DELETE /api/data/ticket/remove/work/{identifier}`

**Auth:** normal

Takes the work entry with this identifier off, and its line off the invoice if it's still there.

- **`202`** removed. **`404`** no such entry. **`409`** the ticket is picked up, or its invoice is paid or void.

## Passcode

The device's passcode or pattern, given at check in or later. It's sealed with a key from the server's secret
(`PasscodeVault`, AES-GCM) in `ticket_passcodes`, never on the ticket, the status page, the claim slip or the PDF,
and it's deleted when the ticket is picked up. See [Running a server](../operations.md#restore) for moving it
between servers.

### `GET /api/data/ticket/passcode/{identifier}`

**Auth:** normal

Shows it. Every look is audit logged (`Passcode for 26-... viewed`), so Restricted users can't.

- **`200`** `PasscodeBody { passcode }`. **`404`** no such ticket, or no passcode on file. **`409`** it was sealed
  under another server secret and can't be read.

### `POST /api/data/ticket/passcode/{identifier}`

**Auth:** normal

Sets it, in place of any it had. Audited, without the passcode.

- **Body:** `PasscodeBody { passcode }`, up to 64 characters, trimmed.
- **`202`** saved. **`400`** blank or too long. **`404`** no such ticket. **`409`** the ticket was picked up.

### `DELETE /api/data/ticket/passcode/{identifier}`

**Auth:** normal

Clears it. Audited.

- **`202`** cleared, or there was none. **`404`** no such ticket.

## See also

- [Notes API](notes.md#tickets)
- [Counter API](counter.md)
- [Ticket and invoice numbers](../server/numbering.md)
- [Counter: check-in, invoices and pickup](../server/counter.md#picked_up-is-the-servers)
