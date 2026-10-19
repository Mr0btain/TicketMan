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

## Changing

### `POST /api/invoice/ticket/{ticketIdentifier}`

**Auth:** normal

Gets the ticket's invoice, making it (numbered, with the shop's current tax rate) if there isn't one.

- **`200`** `Invoice`. **`404`** no such ticket.

### `POST /api/invoice/{identifier}/update`

**Auth:** normal

Replaces the lines and discount. The server works out the totals again, with the shop's current tax rate.

- **Body:** `InvoiceUpdateRequest { charges, discountCents }`.
- **`200`** the updated `Invoice`.
- **`400`** a line without a description, a quantity of zero or less or with more than two decimals, a negative
  price, or a negative discount. **`404`** no such invoice. **`409`** the invoice is paid or void.

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
