# Money

Every amount in TicketMan is a `Long` count of cents: prices, costs, estimates, charges, payments, totals. Doubles
never hold money, so sums can't drift. Two shared objects do the converting and the maths, and both the client and
the server use them, so a total shown on the counter is worked out the same way as the one saved.

## `Money`

[`Money.kt`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/data/money/Money.kt):

| Function | Does |
|----------|------|
| `format(cents, symbol)` | `1234` → `"$12.34"`, `-1234` → `"-$12.34"`, with `symbol` (default `$`) in place of the `$` |
| `parse(text)` | `"12.5"`, `"$12.50"`, `"$12.3456"` → cents, rounded. Ignores everything except digits, one dot and a leading minus. `null` if there's no number |
| `fromDollars(d)`, `toDollars(c)` | For the few fields that edit dollars |
| `percentToBasisPoints("7.5")` | → `750`. Accepts a trailing `%`. `null` unless 0 to 100 |
| `applyRate(cents, basisPoints)` | Cents times a rate, rounded half up to the cent |

Tax rates are **basis points** (hundredths of a percent) as an `Int`: 7.5% is `750`. The shop profile stores the
rate as the text the admin typed (`"7.5"`), and the server converts it with `percentToBasisPoints` whenever it
needs it.

On screen, money is typed into the kit's `TmMoneyField`, which edits cents directly, and shown with the kit's money
text, in the `numeric*` (monospace) text styles.

### Currency

The shop profile's `currencySymbol` (default `$`, 1 to 4 characters with no digits, checked by `/api/shop/update`)
goes before every amount. On the client, `App` provides it as the kit's `LocalCurrencySymbol` from
`CounterHandler.shop`, which loads at login and updates when an admin saves the profile. `TmMoneyText`,
`TmMoneyField` and the kit's `formatMoney(cents)` (for amounts inside a sentence) read it; screens use those rather
than calling `Money.format` themselves. Printouts pass `shop.currencySymbol`, and the server uses it in its pickup
messages and audit entries.

## `InvoiceMath`

[`InvoiceMath.totals(charges, discountCents, taxRateBasisPoints)`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/data/invoice/InvoiceMath.kt)
returns a `Totals(subtotalCents, discountCents, taxCents, totalCents)`:

1. **Each line** is `quantity × unitPriceCents`, rounded to the cent (`Charge.totalCents`). Quantity is a `Double`
   with up to two decimals, so it can be 1.5 hours of labor.
2. **Subtotal** is the sum of every line.
3. **Discount** is capped between 0 and the subtotal.
4. **Tax** is charged only on taxable lines, and the discount comes off those first:
   `tax = applyRate(max(taxable − discount, 0), rate)`.
5. **Total** is `subtotal − discount + tax`.

For example, with 8.25% tax on parts only: a $120.00 part (taxable) plus half an hour of labor at $60.00 (not
taxable) is a subtotal of $150.00, tax of $9.90 on the part, and a total of $159.90.

Taking the discount off the taxed lines first means a discount always lowers the tax, which is what customers
expect.

### Who calls it

- **Server**: `InvoiceDatabase.setCharges()` runs it every time an invoice's charges or discount change, and saves
  the totals on the invoice. The server's numbers are the real ones.
- **Client**: the pickup screen runs it on the charges being edited to preview the totals before they're saved.

## Payments and balance

`Invoice.paidCents` sums the payments, and `balanceCents = totalCents − paidCents`. A negative balance means change
is due. At pickup the payments have to cover the balance; the server refuses a short one. See the
[end to end script](../../scripts/counter_e2e.py) for a worked pickup, including change.

## Gotchas

- **`Money.parse` goes through a `Double`.** That's fine for anything a person types, but don't use it to move
  amounts around in code. Keep them as `Long` cents.
- **Older inventory prices were text.** `InventoryDatabase.migrateLegacyPrices()` converts them to cents with
  `Money.parse` on start.
- **An invoice keeps the tax rate it was made with.** The rate is read from the shop profile once, when the invoice
  is made (at check-in, or the first time it's opened), and stored on it. Changing the shop's tax rate only affects
  invoices made after that. See [decisions](../decisions.md#invoices-keep-the-tax-rate-they-were-made-with).
- **The symbol always goes first**, with a dot before the cents. Shops that write `12,34 €` get `€12.34`.

## See also

- [Domain models](domain-models.md): `Invoice`, `Charge`, `Payment`
