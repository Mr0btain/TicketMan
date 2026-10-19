# Counter API

`/api/counter`, from [`CounterRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/CounterRoute.kt).
The front desk: one search across tickets and customers, the queue, the repeat repair check, and the two
endpoints that change several things at once, check-in and pickup. What check-in and pickup do inside, and every
rule they check, is in [Counter: check-in, invoices and pickup](../server/counter.md).

## Changing

### `POST /api/counter/check-in`

**Auth:** normal

A device coming in. Saves the ticket, its customer and technician links, the deposit and the signature together,
or none of them. The ticket gets its number here.

- **Body:** `CheckInRequest { ticket, customerIdentifier, technicianIdentifier, deposit, signature }`. `deposit`
  is a `PaymentRequest`; `signature` is needed when the shop has repair terms.
- **`201`** `CheckInResponse { ticket, invoice }`, with the numbered ticket and the invoice if a deposit made one.
- **`400`** with the reason: the ticket id isn't a version 7 UUID, no such customer, no make or model, no problem,
  a status other than in progress or on hold, a negative estimate, a technician without Normal access, a deposit
  of zero or less, or a missing or bad signature. **`409`** a ticket with that id already exists.

### `POST /api/counter/pickup`

**Auth:** normal

Hands a ticket back: takes the payments, marks the invoice paid, takes the parts from stock and closes the ticket
as `PICKED_UP`, in one transaction. Makes the invoice if the ticket doesn't have one.

- **Body:** `PickupRequest { ticketIdentifier, payments }`. `payments` are the ones taken now; earlier ones (the
  deposit) are already on the invoice.
- **`200`** the paid `Invoice`.
- **`400`** a payment of zero or less. **`404`** no such ticket.
- **`409`** already picked up, the invoice isn't open, money is still owed (`$12.00 is still owed on INV-...`), or
  there isn't enough of a part in stock.

## Reading

### `GET /api/counter/search`

**Auth:** restricted

Tickets and customers matching a query. Tickets match on number, device or problem, customers on name, phone or
email, and each matching customer's tickets are added to the tickets.

- **Query:** `query`, `limit` (default 20, kept to 1 to 100).
- **`200`** `CounterSearchResponse { tickets, customers }`, tickets as `TicketSummary` (the ticket with its
  customer's name).

### `GET /api/counter/queue`

**Auth:** restricted

What needs attention: open tickets sorted into four lists.

- **Query:** `recent`, how many just checked in tickets (default 10, at most 100).
- **`200`** `CounterQueueResponse { readyForPickup, dueToday, overdue, recentlyCheckedIn }`.

### `POST /api/counter/repeat-check`

**Auth:** restricted

Earlier tickets for the same device and customer within the shop's repeat window (`repeatWindowDays` on the shop
profile), to warn at check-in that it's back.

- **Body:** `RepeatCheckRequest { customerIdentifier, device, excludeTicket }`. `excludeTicket` leaves out the
  ticket being checked in.
- **`200`** a list of `TicketSummary`, empty when it's not a repeat.

## See also

- [Counter: check-in, invoices and pickup](../server/counter.md)
- [Invoices API](invoice.md)
- [Counter screen](../ui/screens/counter.md)
