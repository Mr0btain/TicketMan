# Payments API

`/api/payment`, from [`PaymentRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/PaymentRoute.kt).
Cards taken through the shop's own Stripe or Square account: an amount sent to a card reader at the counter, or a
payment link (a checkout page) the customer pays on, from the counter or the [status page](status.md#get-statustokenpay).
`PaymentService` (`components/payments/`) runs it; the setup is in
[Running a server](../operations.md#card-payments).

**Processors.** Each one is a `PaymentProcessor` (`StripeProcessor`, `SquareProcessor`, both straight REST over
the JDK `HttpClient`), made from the saved settings by `PaymentProcessors.of`, which tests swap like the message
senders:

| | Stripe | Square |
|-|--------|--------|
| Reader | A `card_present` PaymentIntent, sent with `terminal/readers/{id}/process_payment_intent` | A terminal checkout for the device |
| Link | A Checkout Session | A quick pay payment link (reference `<link id>/<order id>`) |
| Paid when | The intent `succeeded`, the session `paid` | The checkout `COMPLETED`, the order has a tender whose payment `COMPLETED` |
| Refund | Against the PaymentIntent | Against the payment |
| Webhook | `Stripe-Signature`, HMAC of `t.body` with the signing secret, 5 minutes old at most | `x-square-hmacsha256-signature`, HMAC of the webhook address and body with the signature key |

`SIMULATED` pretends, for a server in development mode only (the tests and `scripts/counter_e2e.py`): a reader is
paid 2 seconds after it's sent unless the amount ends in .02, which is declined, and a link is paid as soon as it's
asked about and sends the customer straight back.

**Charges.** Each try is a `CardCharge` (`card_charges` table): `PENDING`, then `SUCCEEDED`, `FAILED`,
`CANCELLED`, or `REFUNDED`. The server only marks one paid from what the processor says when asked: the counter
asks while it waits (each `GET` of a charge asks again), the sweep every 10 seconds asks new ones every 10 seconds
(links every minute), ones over 10 minutes old every 5, and calls off a reader nobody came back to after 30 minutes
and a link after 7 days. A webhook only says which charges to ask about, so a forged one can't mark anything paid,
and webhooks are optional.

A paid charge with an invoice becomes a `CARD` payment on it at once (`InvoiceService.cardCharged`: the charge's
status moves on in the same transaction, once), with `processor`, `processorReference` (the processor's id for the
money) and `chargeIdentifier`. Money taken is always recorded, on an invoice paid or voided meanwhile too, where an
admin refunds what's over. A charge made with no invoice, for a sale or a check in deposit that isn't saved yet,
waits to be claimed: the sale's or deposit's `PaymentRequest` names it in `chargeIdentifier` (method `CARD`, the
charge's amount), checked before the transaction and claimed inside it. One never claimed, like a sale that wasn't
finished, is listed with `unclaimed` and refunded with [`/charge/{identifier}/refund`](#post-apipaymentchargeidentifierrefund).

**Refunds.** A card payment taken through the processor goes back onto its card: an [invoice refund](invoice.md#post-apiinvoiceidentifierrefund)
naming it in `paymentIdentifier`, or taking it off an open invoice ([`DELETE` a payment](invoice.md#delete-apiinvoiceidentifierpaymentspaymentidentifier)),
which refunds what's left of it first. Either way the processor is asked before anything is saved, so a refused
refund changes nothing. Each refund carries an idempotency key made from its own id, so trying again doesn't refund
twice.

**Secrets.** The Stripe secret key and webhook secret, and the Square access token and webhook signature key, are
kept in the database (`payment_settings`, so they're in backups) and never sent to a client: reads have them `null`
with `stripeKeySet`, `stripeWebhookSecretSet`, `squareTokenSet` and `squareWebhookKeySet` saying whether one is
saved. Saving with one `null` keeps the saved one; any string replaces it, `""` clears it.

## Settings

### `GET /api/payment/settings`

**Auth:** admin

- **`200`** `PaymentSettings`, without the keys.

### `POST /api/payment/settings`

**Auth:** admin

- **Body:** `PaymentSettings`: `enabled`, `provider` (`STRIPE`, `SQUARE` or `SIMULATED`), `currency` (ISO code like
  `USD`, saved upper case), `stripe`, `square` (`environment` `PRODUCTION` or `SANDBOX`, `locationId`), `readers`
  (`CardReader { identifier, name }`, the processor's id: a Stripe `tmr_...` or a Square device id) and
  `onlinePayments` (customers pay from the status page).
- **`200`** the settings as saved, without the keys. Audit logged.
- **`400`** what `PaymentSettings.problem()` says, with the saved keys counted: a currency that isn't 3 letters,
  turned on without a Stripe secret key or a Square token and location, a reader without an id or name or twice,
  more than 50; or `SIMULATED` on a server not in development mode.

### `GET /api/payment/readers`

**Auth:** admin

The readers on the processor account, with the saved keys, whether or not payments are on yet: Stripe's terminal
readers, Square's paired device codes.

- **`200`** `List<CardReader>`.
- **`409`** no keys saved, or the processor's reason, like `Stripe said 401: Invalid API Key provided`.

### `GET /api/payment/options`

**Auth:** restricted

What the counter offers, no keys.

- **`200`** `PaymentOptions { enabled, provider, readers, links, onlinePayments }`. All off while payments are off
  or not set up; `links` and `onlinePayments` also need the shop's [public address](shop.md#post-apishopupdate),
  since the checkout comes back to the status page.

## Charges

### `POST /api/payment/charge/reader`

**Auth:** normal

Sends an amount to a reader. Watch it with [`GET /charge/{identifier}`](#get-apipaymentchargeidentifier).

- **Body:** `ReaderChargeRequest { readerIdentifier, amountCents, invoiceIdentifier }`. With an invoice, the payment
  lands on it once paid; without, a sale or deposit claims it.
- **`201`** the `CardCharge`, `PENDING`. Audit logged.
- **`400`** a reader that isn't one of the shop's, an amount of zero or less, or more than the invoice's balance.
- **`404`** no such invoice.
- **`409`** payments are off, the invoice isn't open, the reader is still waiting for another card (asked about
  first), or the processor refused, with its reason (the charge is kept as `FAILED`).

### `POST /api/payment/charge/link`

**Auth:** normal

A checkout page for an open invoice, coming back to its ticket's status page. A waiting link for the same amount is
handed out again; ones for other amounts are closed first, so a customer can't pay twice from two links.

- **Body:** `PaymentLinkRequest { invoiceIdentifier, amountCents }`, `null` for the whole balance.
- **`201`** the `CardCharge` with its `url`. Audit logged.
- **`400`** more than the balance.
- **`404`** no such invoice.
- **`409`** payments are off, no public address, nothing owed, the invoice isn't open, or the processor refused.

### `GET /api/payment/charge/{identifier}`

**Auth:** restricted

The charge, asked about at the processor first while it's waiting, which is how the counter follows a reader.

- **`200`** `CardCharge`. `SUCCEEDED` has `paymentReference`, and `paymentIdentifier` once it's on an invoice;
  `FAILED` has the processor's `error`, like `Your card was declined.`
- **`404`** no such charge.

### `POST /api/payment/charge/{identifier}/cancel`

**Auth:** normal

Stops a reader waiting for the card, or closes a link, then asks once more, since the card may just have gone
through.

- **`200`** the charge, `CANCELLED` (or `FAILED`, when the processor says so). Audit logged.
- **`404`** no such charge.
- **`409`** it isn't waiting, the card went through before it could be cancelled (it's kept, on its invoice), or
  the processor didn't cancel it.

### `POST /api/payment/charge/{identifier}/refund`

**Auth:** admin

Gives back a charge that was paid but never put on an invoice.

- **Body:** `ChargeRefundRequest { reason }`.
- **`200`** the charge, `REFUNDED`. Audit logged as a refund.
- **`400`** no reason.
- **`404`** no such charge.
- **`409`** it's on an invoice (refund it [there](invoice.md#post-apiinvoiceidentifierrefund)), it wasn't paid, its
  processor isn't set up any more, or the processor refused.

### `GET /api/payment/charges`

**Auth:** admin

- **Query:** `offset`, `limit`.
- **`200`** `List<CardCharge>`, newest first.
- **`500`** the read failed.

### `GET /api/payment/charges/invoice/{identifier}`

**Auth:** restricted

- **`200`** the invoice's `List<CardCharge>`, newest first.
- **`500`** the read failed.
