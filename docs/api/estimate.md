# Estimates API

`/api/estimate`, from [`EstimateRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/EstimateRoute.kt).
Work the shop proposes on a ticket, for the customer to approve before it's done. `EstimateService` runs it.

An `Estimate { lines, taxRateBasisPoints, note, status, createdAt, createdBy, decision }` holds `Charge` lines like an
invoice's, with the shop's tax rate when it was made; `totals` come from shared `InvoiceMath` with no discount. A
ticket has any number over time, at most one `PENDING`. The customer answers on their
[status page](status.md#post-statustokenestimate), typing their name as the signature, or tells staff, who write it
down here. The answer is kept as an `EstimateDecision { approved, method, amountCents, decidedAt, recordedBy,
signedName, note }`, so what was agreed to never changes.

Approving puts the lines on the ticket's invoice (made then if it has none) with new ids, in the same transaction
as the answer (`InvoiceService.addApprovedLines`, under its `CHANGE_LOCK`), and sets the ticket's `estimateCents` to
the total. Sending, approving and declining each move the ticket to the shop profile's `estimateSentStatus`,
`estimateApprovedStatus` and `estimateDeclinedStatus` when set ([Shop profile API](shop.md)), recorded in its
history; those moves send no status message.

### `GET /api/estimate/ticket/{ticketIdentifier}`

**Auth:** restricted

- **`200`** the ticket's `Estimate`s, newest first. **`404`** no such ticket.

### `POST /api/estimate/ticket/{ticketIdentifier}`

**Auth:** normal

Sends a new estimate, withdrawing one still waiting. With `notify` on, the customer gets the `ESTIMATE_READY`
message when the shop has it on ([Messages API](notify.md)).

- **Body:** `EstimateRequest { lines, note, notify }`.
- **`201`** the `Estimate`, `PENDING`. **`404`** no such ticket. **`409`** the ticket was picked up.
- **`400`** no lines, or lines an invoice wouldn't take (a blank description, a quantity that isn't more than zero
  with up to two decimals, a negative price, a part without an inventory item), or a note over 2000 characters.

### `POST /api/estimate/{identifier}/decide`

**Auth:** normal

What the customer said, by phone, in person or by email, written down by the caller.

- **Body:** `EstimateDecisionRequest { approved, method, note }`. `method` can't be `STATUS_PAGE`.
- **`200`** the `Estimate`, `APPROVED` or `DECLINED`. **`404`** no such estimate.
- **`409`** it was already answered or withdrawn, the ticket was picked up, or the invoice isn't open (void or
  paid); the estimate stays waiting. **`400`** `STATUS_PAGE`, or a line inventory no longer has.

### `POST /api/estimate/{identifier}/withdraw`

**Auth:** normal

- **`200`** the `Estimate`, `WITHDRAWN`. **`404`** no such estimate. **`409`** it isn't waiting.

## See also

- [Status page](status.md)
- [Counter: check-in, invoices and pickup](../server/counter.md)
