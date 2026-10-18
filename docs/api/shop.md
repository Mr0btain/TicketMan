# Shop profile API

`/api/shop`, from [`ShopRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/ShopRoute.kt).
The shop's own details: what goes on invoices and slips, the tax rate, the repair terms customers sign, how long
a repair counts as a repeat, and the default paper size. One profile per server, edited on the admin screen's
Server tab.

### `GET /api/shop`

**Auth:** restricted

Everyone reads it, to print invoices and slips and show the terms at check-in.

- **`200`** `ShopProfile`. A server that's never saved one sends the defaults.

### `POST /api/shop/update`

**Auth:** admin

Replaces the profile. A new tax rate applies to invoices made after it, and to open invoices the next time their
lines are saved. Paid and void invoices keep the rate they had.

- **Body:** `ShopProfile`.
- **`202`** saved. **`400`** `repeatWindowDays` outside 1 to 3650.

## See also

- [Counter: check-in, invoices and pickup](../server/counter.md)
- [Printing](../client/printing.md)
