# Shop profile API

`/api/shop`, from [`ShopRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/ShopRoute.kt).
The shop's own details: what goes on invoices and slips, the tax rate, the currency symbol, the repair terms
customers sign, how long a repair counts as a repeat, the default paper size, the public address customers reach
the server at, and the shop's own ticket statuses. One profile per server, edited on the admin screen's Server tab.

### `GET /api/shop`

**Auth:** restricted

Everyone reads it, to print invoices and slips and show the terms at check-in.

- **`200`** `ShopProfile`. A server that's never saved one sends the defaults.

### `POST /api/shop/update`

**Auth:** admin

Replaces the profile. A new tax rate applies to invoices made after it, and to open invoices the next time their
lines are saved. Paid and void invoices keep the rate they had.

- **Body:** `ShopProfile`. `currencySymbol` is saved trimmed. `laborRateCents` is per hour, the price time logged on
  a ticket's work log starts at. `publicUrl` is saved trimmed, without a trailing `/`.
- **`statuses`** are the shop's own ticket statuses (`CustomStatus { identifier, name, actsAs, tone, showCustomer,
  retired }`). Each counts as a built-in status (`actsAs`), which decides where its tickets show; customers see its
  name only when `showCustomer` is on. A status left out of the list is kept with `retired` set, since tickets and
  history may still point at it. `CustomStatusRules` in shared checks them on both sides.
- **`estimateSentStatus`**, **`estimateApprovedStatus`** and **`estimateDeclinedStatus`** are where sending,
  approving and declining an [estimate](estimate.md) move the ticket, each a `StatusChoice` or null to leave it.
- **`202`** saved. **`400`** `repeatWindowDays` outside 1 to 3650, `currencySymbol` blank, longer than 4
  characters or holding a digit, a negative `laborRateCents`, a `publicUrl` that isn't an `http(s)://` address, or
  statuses that break the rules: a blank or over 40 character name, two in use with the same name or a built-in
  one's, one acting as `PICKED_UP`, more than 40, or a saved one whose `actsAs` changed; or an estimate status staff
  couldn't pick (picked up, retired or unknown).

## See also

- [Counter: check-in, invoices and pickup](../server/counter.md)
- [Printing](../client/printing.md)
