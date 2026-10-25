# Reports API

`/api/report`, from [`ReportRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/ReportRoute.kt).
How the shop did over some days, read by the admin screen's Reports tab, and the commission rates its tech numbers
use. Admins only. The sums are worked out in `ReportDatabase` from what's already kept: invoices, payments, the work
log and the status history. For the day by day numbers see the end of day report,
[`GET /api/admin/report/daily`](admin.md#get-apiadminreportdaily).

## Reading

### `GET /api/report/period`

**Auth:** admin

From one day to another, both included, in the shop's time zone (`TZ`).

- **Query:** `from` and `to`, days like `2026-10-01`. **`400`** either missing or not a day, `to` before `from`, or
  more than 732 days.
- **`200`** `PeriodReport { from, to, sales, days, taxes, takenByMethod, refunds, techs, turnaround }`:
  - `sales` (`SalesSummary`): invoices paid in those days, void ones left out. `invoices`, `totalCents`, `taxCents`,
    `discountCents`, line totals before the discount by type (`laborCents`, `partsCents`, `otherCents`,
    `giftCardCents`), `partsCostCents` and `partsWithoutCost`. Net sales are the total less tax and gift cards sold
    (money held for the customer, not a sale); gross profit is net sales less what the parts cost.
  - Parts cost is each part line's item `costCents` as it was when the invoice was paid, kept on the line then
    (`invoice_charges.cost_cents`). Lines paid before costs were kept, or whose item has no cost, count as free and
    are counted in `partsWithoutCost`.
  - `days`: `DaySales { date, invoices, totalCents }` for each day with a paid invoice, oldest first.
  - `taxes`: a `TaxLine { name, basisPoints, taxableCents, taxCents }` per rate, as each invoice worked it out
    (`InvoiceMath`), added up by name and rate.
  - `takenByMethod`: payments received in those days by method, refunds taken off. Store credit and gift cards spent
    are in it too; the client leaves them out of money taken.
  - `refunds` (`RefundSummary { count, cents, refunds }`): refunds given in those days, the newest 100 listed as
    `RefundLine { invoiceLabel, amountCents, reason, method, receivedAt, receivedBy }`. Refunds aren't taken off the
    profit.
  - `techs`: a `TechSummary { userIdentifier, name, tickets, laborHours, laborCents, partsCents,
    commissionBasisPoints }` per person who logged work that's on those paid invoices, at the price the line was
    billed at (so free warranty work counts its hours, nothing else). Most labor first. Commission is
    `laborCents` times their rate.
  - `turnaround`: tickets picked up in those days, `pickedUp`, and hours from check in to the first time they were
    marked ready (a status counting as done, `readyCount` of them) and to pickup, as averages and medians. Null when
    there are none.

### `GET /api/report/commissions`

**Auth:** admin

- **`200`** a list of `CommissionRate { userIdentifier, basisPoints }`, one per user who has one (1000 is 10%).
  Kept in `commission_rates`, apart from users, so only admins see them.

## Changing

### `POST /api/report/commissions`

**Auth:** admin

Replaces every rate. A user left out, or given 0, has none. Audit logged.

- **Body:** a list of `CommissionRate`.
- **`202`** saved. **`400`** a rate below 0 or over 10000, the same user twice, or a user that doesn't exist.

## See also

- [Admin screen: Reports](../ui/screens/admin.md#tabs)
- [Invoices API](invoice.md)
- [Tickets API: work log](ticket.md#work-log)
