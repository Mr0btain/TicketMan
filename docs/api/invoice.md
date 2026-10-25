# Invoices API

`/api/invoice`, from [`InvoiceRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/InvoiceRoute.kt).
One invoice per ticket, plus counter sales, which have no ticket (`ticketIdentifier` null; made by
[`POST /api/counter/sale`](counter.md#post-apicountersale)). Anyone logged in can look; Normal access changes an open invoice; only admins void, and
delete one kept after its ticket was deleted. An invoice with money taken against it, or a void one, outlives its
ticket: `ticketLabel` keeps the ticket's number and `ticketRemovedAt` says when it went.
Totals are always worked out by the server with `InvoiceMath`, the tax rate the invoice was made with and its
`taxRates` (the shop's [tax classes](shop.md#post-apishopupdate) then), so a client never sends them. A line's
`taxClass` picks one of those; a class the shop doesn't have is a `400`. Paying an invoice off and closing the ticket is [`POST /api/counter/pickup`](counter.md#post-apicounterpickup).
See [Counter: check-in, invoices and pickup](../server/counter.md#invoices) for the rules.

Opening a ticket's invoice (`POST /api/invoice/ticket/{id}`) and picking it up bring its storage fee up to date: one
untaxed `OTHER` line, `Storage, 2 days`, whose id is made from the ticket's (`Storage.lineIdentifier`), a day's fee
for each day the ticket has been ready past the shop's free days. Taking that line off in an update waives it for
good (`storageWaived`).

All the changing routes run under `InvoiceService`'s lock, one change at a time.

## Reading

### `GET /api/invoice/{identifier}`

**Auth:** restricted

- **`200`** `Invoice`. **`404`** no such invoice.

### `GET /api/invoice/ticket/{ticketIdentifier}`

**Auth:** restricted

The ticket's invoice, without making one.

- **`200`** `Invoice`. **`404`** the ticket has no invoice.

### `GET /api/invoice/fetch`

**Auth:** restricted

A page of every invoice, newest first.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Invoice`.

### `GET /api/invoice/{identifier}/pdf`

**Auth:** restricted

The invoice as a PDF on a letter page, laid out like the printed one (`PrintTemplates.invoice`), to email or file.
Streamed, with `Content-Disposition: attachment; filename=INV-26-00000042.pdf`.

- **`200`** `application/pdf`. **`404`** no such invoice. One whose ticket was deleted shows the kept ticket number
  and no device.

### `GET /api/invoice/export`

**Auth:** admin

Invoices made between two days, in the shop's time zone, as CSV for the books. Audited (`BACKUP` on `invoices`, with
the count and days).

- **Query:** `from` and `to`, days like `2026-10-01`, both included. **`400`** either missing, not a day, or `to`
  before `from`.
- **`200`** `text/csv`, one row per invoice, oldest number first: `Invoice, Date, Status, Paid on, Ticket,
  Customer, Subtotal, Discount, Tax, Total, Paid, Balance, Refunded`. A sale's Ticket is `Sale`. Amounts are plain numbers (`159.90`) so a spreadsheet adds
  them up. Cells are quoted as spreadsheets expect, and typed text starting with `=`, `+`, `-` or `@` gets a leading
  `'` so it isn't run as a formula.

## Changing

### `POST /api/invoice/ticket/{ticketIdentifier}`

**Auth:** normal

Gets the ticket's invoice, making it (numbered, with the shop's current tax rate) if there isn't one.

- **`200`** `Invoice`. **`404`** no such ticket.

### `POST /api/invoice/{identifier}/update`

**Auth:** normal

Applies what the caller changed since it opened the invoice to the lines it has now, so lines added meanwhile
(like work logged on the ticket) stay. Changed lines keep their place, new ones go on the end. The server works out
the totals again, with the tax rate the invoice was made with.

- **Body:** `InvoiceChangeRequest { added, changed, removed, discountCents, discountWas }`, built with shared
  `InvoiceChanges.between(before, beforeDiscount, after, afterDiscount)`. `changed` is a list of
  `ChargeChange { before, after }`, `removed` the lines as they were, and `discountCents` null when it wasn't
  changed. An empty one changes nothing.
- **`200`** the updated `Invoice`, with everyone's lines.
- **`400`** a line without a description, a quantity of zero or less or with more than two decimals, a negative
  price, or a negative discount. **`404`** no such invoice. **`409`** the invoice is paid or void, or someone else
  changed or removed a line this changes or removes, or changed the discount this changes (`Someone else changed
  "Screen" on this invoice`). A line removed that's already gone, or added twice, is fine.

### `POST /api/invoice/{identifier}/payments`

**Auth:** normal

Takes a payment, like a deposit, without closing anything.

- **Body:** `PaymentRequest { method, amountCents, note, reference }`. `receivedBy` is filled in from the caller.
  `STORE_CREDIT` spends the invoice's customer's credit; `GIFT_CARD` spends the card whose code is `reference`.
- **`200`** the updated `Invoice`.
- **`400`** zero or less. **`404`** no such invoice. **`409`** the invoice is paid or void, store credit with no
  customer on the invoice or not enough of it (`Only $10.00 of store credit left`), or no such gift card or not
  enough on it (`Only $5.00 left on gift card GC-...`).

### `DELETE /api/invoice/{identifier}/payments/{paymentIdentifier}`

**Auth:** normal

Takes a payment back off, for one entered by mistake. Store credit or a gift card gets back what it paid.

- **`200`** the updated `Invoice`. **`404`** no such invoice or payment. **`409`** the invoice is paid or void.

### `POST /api/invoice/{identifier}/void`

**Auth:** admin

Cancels an invoice. A paid one puts its parts back in stock, less any a refund already put back, and takes back
the gift cards it loaded. The ticket keeps its status; a picked up ticket stays
picked up.

- **`200`** the void `Invoice`. **`404`** no such invoice. **`409`** already void, or a gift card it sold has been
  spent from (refund instead).

### `POST /api/invoice/{identifier}/refund`

**Auth:** admin

Gives money back on a paid invoice, and puts returned parts back in stock, in one transaction. The refund is saved
as a payment with a negative `amountCents`, the reason as its `note` and the parts as its `returns`; the invoice
stays paid. `paidCents` leaves refunds out, `refundedCents` adds them up and `netPaidCents` is the difference.
Audited as `REFUND`.

- **Body:** `RefundRequest { method, amountCents, reason, returns, reference }`. `amountCents` is positive, or zero
  when only parts come back; `returns` is a list of `ReturnedPart { inventoryIdentifier, quantity }`.
  `STORE_CREDIT` goes to the invoice's customer; `GIFT_CARD` goes onto the card `reference` names, or a new one
  when it's blank, whose code is the refund's `reference`.
- **`200`** the `Invoice` with the refund on it.
- **`400`** no reason, a negative amount, nothing refunded or returned, more than `netPaidCents` (`Only $30.00 was
  paid on INV-...`), store credit on an invoice with no customer, a part the invoice didn't sell, or more of one than it sold less what was already returned
  (`Only 1 of Cable can be returned on INV-...`). **`404`** no such invoice. **`409`** the invoice is open (take the
  payment off instead) or void.

### `DELETE /api/invoice/{identifier}`

**Auth:** admin

Deletes an invoice kept after its ticket was deleted, with its lines and payments, once an admin decides it isn't
needed for the books. Stock its parts took stays taken; void it first to put that back. Audited.

- **`202`** deleted. **`404`** no such invoice. **`409`** its ticket is still there; it's dealt with when the
  ticket is deleted.

## See also

- [Counter: check-in, invoices and pickup](../server/counter.md)
- [Money](../shared/money.md)
- [Counter API](counter.md)
