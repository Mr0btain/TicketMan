# Status page

`/status`, from [`StatusRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/StatusRoute.kt).
The customer status page: HTML for a phone's browser, not JSON, and no login. It's the one part of the server meant
to be on the internet, see [Running a server](../operations.md#the-customer-status-page). `StatusService` decides
what goes on it and `StatusPage` writes the HTML.

Every ticket gets a random 32 character hex token when it's added (`Ticket.statusToken`, never taken from a
client). The link is the shop profile's `publicUrl` plus `/status/{token}` (shared `StatusLink.url`), printed as the
claim slip's QR once `publicUrl` is set. Staff replace a token with
[`POST /api/data/ticket/status-token/{identifier}`](ticket.md#post-apidataticketstatus-tokenidentifier), which
cuts off the old link.

The page shows the device (make and model, not the serial), the status and a timeline of status changes, the
[estimate](estimate.md) waiting for an answer with its lines and a form to approve or decline it (or what they said
to the last one), the promised date while it's open, notes marked `customerVisible` with their photos, the estimate, the invoice's total,
paid and balance (not a void one, and no lines), a **Pay online** button for the balance when the shop takes
[payments online](payment.md), the customer's first name and the shop's contact details. A status
the shop doesn't share (`CustomStatus.showCustomer` off) reads as the built-in one it acts as, and repeats it makes
are dropped from the timeline. Never internal notes, who changed what, the serial or the customer's contact details.

Pages are sent with `Cache-Control: no-store`, `Referrer-Policy: no-referrer` (the token is in the address),
`X-Robots-Tag: noindex`, `X-Frame-Options: DENY` and a Content-Security-Policy allowing only inline styles, the
page's own images and its own form: there are no scripts. Links are relative, so it works under any path the proxy
gives it. Times are in the server's `TZ`.

Limits are in memory, per address (from `X-Forwarded-For` on [trusted proxies](../configuration.md#trusted_proxies)):
30 unknown links in 15 minutes, 10 wrong lookups in 15 minutes, and 10 wrong lookups per ticket number in 24 hours
from anywhere, so guessing the 4 digits from many addresses runs out too. Only misses count.

### `GET /status`

**Auth:** none

The lookup form: ticket number and the last 4 digits of the customer's phone.

- **`200`** HTML.

### `POST /status`

**Auth:** none

The form's answer.

- **Body:** form fields `number` (`26-00000042` or `26-42`) and `phone` (4 digits).
- **`303`** to `status/{token}` (relative) when the number's ticket has a customer whose phone ends in those
  digits.
- **`404`** the form again, saying it couldn't find it. **`429`** the form again, saying there were too many tries.

### `GET /status/{token}`

**Auth:** none

Waiting payment links on the ticket's invoice are asked about first, so a customer back from paying sees it paid.

- **`200`** the ticket's page. **`404`** a page saying the link doesn't work, with a link to the form, for a token
  that isn't one or was replaced. **`429`** too many unknown links from this address.

### `POST /status/{token}/estimate`

**Auth:** none

The customer approving or declining the waiting estimate. Their typed name is the signature; it's kept as
`signedName` with `method` `STATUS_PAGE`. Unknown links count toward the same limit as the page's.

- **Body:** form fields `decision` (`approve` or `decline`) and `name` (2 to 100 characters).
- **`303`** to `../{token}` (relative), their page, when it's saved.
- **`409`** their page again saying why: no name, nothing waiting any more, or an approval the invoice couldn't
  take. **`400`** a `decision` that's neither. **`404`** an unknown link. **`429`** too many unknown links.

### `GET /status/{token}/photo/{identifier}/{variant}`

**Auth:** none

A photo on one of the ticket's notes marked for the customer, `thumb` or `display` (never `original`), sent with
`Cache-Control: private, max-age=3600`.

- **`200`** the JPEG. **`404`** a wrong link, a photo not on a shared note of this ticket, or another variant.

### `GET /status/{token}/pay`

**Auth:** none

The page's **Pay online** button: a checkout page for the open invoice's balance, through
[`PaymentService.link`](payment.md#post-apipaymentchargelink) (made by `customer`, the same link again while it's
waiting). A plain link rather than a form, so the redirect to the processor isn't held to the page's `form-action`.
Each address gets 10 in 15 minutes, and unknown links count toward the page's limit.

- **`303`** to the processor's checkout, which comes back to the ticket's page.
- **`409`** the page again saying why: paying online is off, nothing is owed, or the processor refused.
  **`404`** an unknown link. **`429`** too many tries.

### `POST /status/payments/{provider}/webhook`

**Auth:** none

The card processor, `stripe` or `square`, saying a payment changed. Here because the status page is the only part
the proxy lets outside addresses reach. The signature is checked with the saved webhook secret (Stripe) or
signature key over the shop's public address of this path (Square); a good one only makes the server ask the
processor about the waiting charges it names. Bodies past 512 KB are cut off.

- **`200`** checked. **`400`** a bad or missing signature, or no key saved. **`404`** not `stripe` or `square`, or
  that processor isn't set up.

## See also

- [Payments API](payment.md)
- [Running a server: the customer status page](../operations.md#the-customer-status-page)
- [Photos](../server/photos.md)
