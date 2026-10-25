# Messages API

`/api/notify`, from [`NotifyRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/NotifyRoute.kt).
Messages to customers by email (the shop's own SMTP server) and text (Twilio), sent from an outbox in the
background. `NotificationService` (`components/notify/`) decides what's sent when; the setup is in
[Running a server](../operations.md#messages-to-customers).

**When messages go.** Automatic ones only for the events the shop turned on (`NotificationSettings.events`) and only
to customers who agreed on that channel (`Customer.notifyEmail`, `notifySms`):

| Event | Sent when |
|-------|-----------|
| `READY_FOR_PICKUP` | A ticket's status changes into one acting as `DONE` from one that wasn't |
| `STATUS_CHANGED` | A ticket's status changes into one of `statusChangeStatuses` (and ready for pickup when that one's off) |
| `ESTIMATE_READY` | An estimate is sent with `notify` on ([Estimates API](estimate.md)) |
| `PICKUP_REMINDER` | The hourly sweep finds a ticket ready `reminderAfterDays`, then every `reminderEveryDays`, up to `reminderLimit` |
| `STORAGE_FEE` | The hourly sweep finds a waiting ticket a day before its [storage fee](shop.md#post-apishopupdate) starts, once since it was ready |
| `ABANDONED` | The hourly sweep treats a ticket left past the shop's abandon limit as abandoned |
| `STATUS_LINK` | Staff send it from the ticket card, whatever the customer agreed to |
| `TEST` | An admin tries the settings, not kept in the outbox |

Only status changes made through [`/api/data/ticket/update`](ticket.md#post-apidataticketupdate) send messages;
estimates moving a ticket don't, since their own message says it.

**Wording.** Each event has a `MessageTemplate { subject, body }` (subject for email only), the shop's own in
`templates` or the defaults in shared `MessageTemplates`. `{name}` (first name), `{ticket}`, `{device}`,
`{status}` (as customers see it), `{link}` (the [status page](status.md), blank without a public address),
`{estimate}`, `{balance}`, `{storage}` (the daily storage fee), `{shop}` and `{shop_phone}` are filled in when the message is queued.

**The outbox.** Each message is an `OutboxMessage` row (`outbox` table), one per channel. A loop every 10 seconds
sends what's due. A send that might work later (`SendResult.Retry`: the server down, a refused login, Twilio 401,
403, 429 or 5xx) is tried again after 1, 5 and 15 minutes, 1, 3 and 6 hours, then `FAILED`. One that won't
(`Failed`: an address the mail server refuses, Twilio 400) fails at once. Turning a channel off cancels what's
waiting on it. Phone numbers go out in international form: one saved without a country code gets
`sms.countryCode` (shared `PhoneNumbers`).

**Secrets.** The SMTP password and Twilio auth token are kept in the database (`notify_settings`, so they're in
backups) and never sent to a client: reads have them `null` with `smtpPasswordSet` and `smsTokenSet` saying whether
one is saved. Saving with one `null` keeps the saved one; any string replaces it, `""` clears it.

## Settings

### `GET /api/notify/settings`

**Auth:** admin

- **`200`** `NotificationSettings`, without the password and token.

### `POST /api/notify/settings`

**Auth:** admin

- **Body:** `NotificationSettings`.
- **`200`** the settings as saved, without the secrets. Audit logged.
- **`400`** what `NotificationSettings.problem()` says: email or texts on without what they need, a bad port, from
  address or from number (`+15551234567`), a country code that isn't 1 to 3 digits, reminder days outside 1 to 365,
  more than 20 reminders, or a template that's empty or too long (2000, subjects 200).

### `POST /api/notify/test`

**Auth:** admin

Sends a test now with the settings sent, so they can be tried before saving. Not kept in the outbox.

- **Body:** `TestMessageRequest { settings, channel, recipient }`. A null password or token uses the saved one.
- **`200`** sent. **`400`** with why: what the mail server or Twilio said, or a recipient that isn't an address or
  number.

## The outbox

### `GET /api/notify/outbox`

**Auth:** admin

Every message, newest first.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200).
- **`200`** a list of `OutboxMessage`.

### `POST /api/notify/outbox/{identifier}/retry`

**Auth:** admin

Tries a failed, cancelled or waiting message again now, with its tries counted from zero.

- **`200`** the `OutboxMessage`. **`404`** no such message. **`409`** it was already sent.

### `POST /api/notify/outbox/{identifier}/cancel`

**Auth:** admin

- **`200`** the `OutboxMessage`, `CANCELLED`. **`404`** no such message. **`409`** it isn't waiting.

## Tickets

### `GET /api/notify/ticket/{identifier}`

**Auth:** restricted

What the ticket's customer was sent, newest first, kept after the ticket is deleted (with `ticketLabel`).

- **`200`** a list of `OutboxMessage`.

### `POST /api/notify/ticket/{identifier}/send`

**Auth:** normal

Sends the ticket's customer a message now, to every channel that's on and that they have a contact for, agreed or
not. The ticket card's **Send status link**.

- **Body:** `TicketMessageRequest { event, channels }`, `STATUS_LINK` and every channel by default.
- **`201`** the messages queued. **`404`** no such ticket.
- **`400`** both channels off, no email or phone to send to, or `TEST`.

## See also

- [Estimates API](estimate.md)
- [Status page](status.md)
