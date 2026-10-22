# Invoices API

`/api/invoice`, from [`InvoiceRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/InvoiceRoute.kt).
One invoice per ticket. Anyone logged in can look; Normal access changes an open invoice; only admins void.
Totals are always worked out by the server with `InvoiceMath` and the shop's tax rate, so a client never sends
them. Paying an invoice off and closing the ticket is [`POST /api/counter/pickup`](counter.md#post-apicounterpickup).
See [Counter: check-in, invoices and pickup](../server/counter.md#invoices) for the rules.

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

- **`200`** `application/pdf`. **`404`** no such invoice, or its ticket is gone.

### `GET /api/invoice/export`

**Auth:** admin

Invoices made between two days, in the shop's time zone, as CSV for the books. Audited (`BACKUP` on `invoices`, with
the count and days).

- **Query:** `from` and `to`, days like `2026-10-01`, both included. **`400`** either missing, not a day, or `to`
  before `from`.
- **`200`** `text/csv`, one row per invoice, oldest number first: `Invoice, Date, Status, Paid on, Ticket,
  Customer, Subtotal, Discount, Tax, Total, Paid, Balance`. Amounts are plain numbers (`159.90`) so a spreadsheet adds
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

- **Body:** `PaymentRequest { method, amountCents, note }`. `receivedBy` is filled in from the caller.
- **`200`** the updated `Invoice`.
- **`400`** zero or less. **`404`** no such invoice. **`409`** the invoice is paid or void.

### `DELETE /api/invoice/{identifier}/payments/{paymentIdentifier}`

**Auth:** normal

Takes a payment back off, for one entered by mistake.

- **`200`** the updated `Invoice`. **`404`** no such invoice or payment. **`409`** the invoice is paid or void.

### `POST /api/invoice/{identifier}/void`

**Auth:** admin

Cancels an invoice. A paid one puts its parts back in stock. The ticket keeps its status; a picked up ticket stays
picked up.

- **`200`** the void `Invoice`. **`404`** no such invoice. **`409`** already void.

## See also

- [Counter: check-in, invoices and pickup](../server/counter.md)
- [Money](../shared/money.md)
- [Counter API](counter.md)
