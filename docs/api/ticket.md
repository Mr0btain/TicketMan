# Tickets API

`/api/data/ticket`, from [`TicketRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/TicketRoute.kt).
Anyone logged in can look; Normal access changes. A ticket's customer and technician are links, set with their own
routes rather than fields on the ticket. Devices coming in at the counter go through
[`POST /api/counter/check-in`](counter.md#post-apicountercheck-in) instead of `/add`, which saves the links,
deposit and signature with it. A ticket's notes are on the [Notes](notes.md#tickets) page.

## Reading

### `GET /api/data/ticket/get/{identifier}`

**Auth:** restricted

- **`200`** `Ticket`. **`404`** no such ticket.

### `GET /api/data/ticket/fetch`

**Auth:** restricted

A page of every ticket, newest first.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Ticket`.

### `GET /api/data/ticket/fetch/mine`

**Auth:** restricted

A page of the tickets assigned to the caller, who comes from the token, so nobody can list someone else's.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Ticket`.

### `GET /api/data/ticket/search`

**Auth:** restricted

Tickets matching a number (`26-42` exactly, `42` in any year), part of the id, the device's make, model or serial,
or the problem. Newest number first.

- **Query:** `query`, `limit` (default 20, at most 200).
- **`200`** a list of `SearchResult`, the text being the number and device.

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

- **`200`** a list of `TicketStatusChange { status, time, changedBy }`. **`404`** if it couldn't be read.

### `GET /api/data/ticket/signature/{identifier}`

**Auth:** restricted

The customer's signature from check-in.

- **`200`** `TicketSignature { strokes, aspectRatio, signedName, terms, signedAt, takenBy }`. Strokes are scaled
  0 to 1; `terms` is the text they agreed to, as it was then. **`404`** no such ticket, or it wasn't signed.

## Changing

### `POST /api/data/ticket/add`

**Auth:** normal

Adds a ticket and gives it the next number.

- **Body:** `Ticket`, with an id the client made. Its `number` is replaced by the next one.
- **`201`** the saved `Ticket`, with its number.
- **`400`** `New tickets can't start picked up`. **`500`** not saved.

### `POST /api/data/ticket/update`

**Auth:** normal

Replaces the ticket's device, status, priority, problem, condition, accessories, estimate and promised time. The
number and pickup time stay the server's. A status change is added to the history.

- **Body:** `Ticket`.
- **`202`** saved. **`404`** no such ticket.
- **`409`** setting `PICKED_UP` (`Pick tickets up at the counter, so the invoice gets paid`), or changing a picked
  up ticket's status. Other changes to a picked up ticket are fine.

### `DELETE /api/data/ticket/remove/{identifier}`

**Auth:** normal

Deletes the ticket with its notes, invoice, signature, history and technician link. Note photos are cleared up by
the next sweep.

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

## See also

- [Notes API](notes.md#tickets)
- [Counter API](counter.md)
- [Ticket and invoice numbers](../server/numbering.md)
- [Counter: check-in, invoices and pickup](../server/counter.md#picked_up-is-the-servers)
