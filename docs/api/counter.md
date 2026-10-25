# Counter API

`/api/counter`, from [`CounterRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/CounterRoute.kt).
The front desk: one search across tickets and customers, the queue, the repeat repair check, and the endpoints
that change several things at once, check-in, pickup and sales. What check-in and pickup do inside, and every
rule they check, is in [Counter: check-in, invoices and pickup](../server/counter.md).

## Changing

### `POST /api/counter/check-in`

**Auth:** normal

A device coming in. Saves the ticket, its customer and technician links, the deposit and the signature together,
or none of them. The ticket gets its number here.

- **Body:** `CheckInRequest { ticket, customerIdentifier, technicianIdentifier, deposit, signature, deviceIdentifier,
  more }`. `deposit` is a `PaymentRequest`; `signature` is needed when the shop has repair terms.
  `deviceIdentifier` is one of the customer's [devices](customer.md#get-apidatacustomerdevicesidentifier), when
  one was picked; without it the server finds theirs with the same serial or makes one, and the ticket's
  `deviceIdentifier` points at it either way. `more` is a list of `CheckInItem { ticket, deviceIdentifier }`, other
  devices left at the same visit: each gets its own numbered ticket, all sharing a `groupIdentifier` (the first
  ticket's id), the same technician and signature; the deposit goes on the first. Up to 10 devices. `passcode`
  (and each item's) is the device's [passcode](ticket.md#passcode), sealed and kept apart from the ticket; each
  ticket's `checklist` is the intake checklist as filled in. A ticket with `warrantyOf` is a warranty return of
  that earlier ticket, which has to be the customer's and still under warranty. `appointmentIdentifier` is the
  booked [appointment](appointment.md) this visit is: it's marked `ARRIVED` with the first ticket (and linked to the
  customer when it had none) in the same transaction.
- **`201`** `CheckInResponse { ticket, invoice, linked }`, with the numbered ticket, the invoice if a deposit made
  one, and the other tickets made with it.
- **`400`** with the reason: a ticket id isn't a version 7 UUID or is used twice, a picked device isn't the
  customer's, more than 10 devices, no such customer, no make or model, no problem,
  an appointment that doesn't exist, isn't booked or is another customer's, a status other than in progress or on hold (or one of the shop's own counting as them; an unknown or retired one is refused too), a negative estimate, a warranty return of a ticket that isn't the customer's or whose warranty ran out, a technician without Normal
  access, a deposit
  of zero or less, or a missing or bad signature. **`409`** a ticket with that id already exists, or the appointment was checked in
  at the same moment somewhere else.

### `POST /api/counter/pickup`

**Auth:** normal

Hands a ticket back: takes the payments, marks the invoice paid, takes the parts from stock and closes the ticket
as `PICKED_UP`, in one transaction, starting its warranty (`warrantyUntil`, the longest a labor or part line gives,
the shop's `warrantyDays` for lines that don't say) and forgetting its passcode. Makes the invoice if the ticket
doesn't have one.

- **Body:** `PickupRequest { ticketIdentifier, payments }`. `payments` are the ones taken now; earlier ones (the
  deposit) are already on the invoice.
- **`200`** the paid `Invoice`.
- **`400`** a payment of zero or less. **`404`** no such ticket.
- **`409`** already picked up, the invoice isn't open, money is still owed (`$12.00 is still owed on INV-...`),
  there isn't enough of a part in stock, or store credit or a gift card can't pay what it's asked to.

### `POST /api/counter/sale`

**Auth:** normal

Sells from stock with no ticket: makes the invoice (with no `ticketIdentifier`), takes the payments and the parts
from stock and marks it paid, in one transaction.

- **Body:** `SaleRequest { customerIdentifier, charges, discountCents, payments }`. `customerIdentifier` is
  optional. Lines follow the same rules as an invoice's.
- **`201`** the paid `Invoice`.
- **`400`** no lines, a bad line, a negative discount, a payment of zero or less, or no such customer.
- **`409`** the payments don't cover the total (`$8.00 is still owed`), there isn't enough of a part in stock, or
  store credit or a gift card can't pay what it's asked to (see [adding a payment](invoice.md#post-apiinvoiceidentifierpayments)).

Lines can include `GIFT_CARD` lines, loaded when the sale is paid ([Gift cards API](giftcard.md)); payments can be
store credit (with `customerIdentifier` set) or gift cards.

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

What needs attention: open tickets sorted into lists.

- **Query:** `recent`, how many just checked in tickets (default 10, at most 100).
- **`200`** `CounterQueueResponse { readyForPickup, dueToday, overdue, recentlyCheckedIn, abandoned }`. Tickets the
  hourly sweep treated as abandoned (`abandonedAt`) are in `abandoned`, not `readyForPickup`.

### `POST /api/counter/repeat-check`

**Auth:** restricted

Earlier tickets for the same device and customer within the shop's repeat window (`repeatWindowDays` on the shop
profile), to warn at check-in that it's back.

- **Body:** `RepeatCheckRequest { customerIdentifier, device, excludeTicket, deviceIdentifier }`. `excludeTicket`
  leaves out the ticket being checked in. With `deviceIdentifier` (a device picked at check in) that device's
  tickets match, as well as the serial or make and model. Each ticket's `warrantyUntil` says whether it's still
  covered.
- **`200`** a list of `TicketSummary`, empty when it's not a repeat.

## See also

- [Counter: check-in, invoices and pickup](../server/counter.md)
- [Invoices API](invoice.md)
- [Counter screen](../ui/screens/counter.md)
