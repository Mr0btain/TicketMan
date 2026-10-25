# Gift cards API

`/api/giftcard`, from [`GiftCardRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/GiftCardRoute.kt).
Gift cards are sold as a `GIFT_CARD` line on a sale or a pickup and loaded when that invoice is paid; a blank code
gets a new one like `GC-7KQ2-M9XD-4HTP-WR8B`, and an existing code is topped up. They're spent as `GIFT_CARD`
payments with the code in `reference`, and a refund can go onto one. How that fits with invoices is in
[Counter: store credit and gift cards](../server/counter.md#store-credit-and-gift-cards). Codes are matched however
they're typed: spaces are dropped and letters made upper case (`GiftCardCode.normalize`).

## Reading

### `GET /api/giftcard/code/{code}`

**Auth:** restricted

A card's balance, for the counter to check before taking it.

- **`200`** `GiftCard { identifier, code, balanceCents, issuedCents, createdAt, invoiceIdentifier, customerIdentifier }`.
  **`404`** no such card.

### `GET /api/giftcard/fetch`

**Auth:** admin

Every card, newest first.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `GiftCard`.

## Changing

### `POST /api/giftcard/code/{code}/adjust`

**Auth:** admin

Corrects a card's balance by hand, like a promotion or a mistake. Audited with the reason.

- **Body:** `BalanceAdjustRequest { amountCents, reason }`, negative to take off.
- **`200`** the `GiftCard` as it is now. **`400`** zero, or no reason. **`404`** no such card. **`409`** taking off
  more than is on it.

## See also

- [Invoices API](invoice.md)
- [Customers API](customer.md), for store credit
