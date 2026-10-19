# Domain models

The `@Serializable` classes in `shared/.../data/` are the records TicketMan works with. The same classes are
stored by the server, sent as JSON and shown by the client, so this is the one place their shape is defined.

## The base types

| Type | What it is |
|------|------------|
| `MainItem` | Abstract base for anything with its own identity: `Customer`, `Ticket`, `Inventory`, `User`, `Note`, `StoredImage`, `Invoice`. Has an `identifier: Uuid` and a `timestamp` read from it |
| `SubItem` | Marker interface for values that live inside a main item (`Device`, `Address`, `TicketStatus`, `Charge`, ...) |
| `IdentifiedItem` | An id plus display text. `SearchResult` implements it |
| `EntityReference` | An id plus a name, for showing a link to another record without loading it (a ticket's customer, its tech) |

### Identifiers and creation time

Main items get their id from `Uuid.generateV7()`. A version 7 UUID starts with the time it was made in
milliseconds, so `Uuid.createdAt()` in
[`TimeHelpers.kt`](../../shared/src/commonMain/kotlin/org/mr0btain/ticketman/data/time/TimeHelpers.kt) reads it
back and `MainItem.timestamp` uses that. No record stores a separate "created" field.

The client makes the id when it creates a record, before the server has seen it. That means a new record can have
notes and photos attached to it before its first save, and a save retried after a network error is refused
as a duplicate id rather than making a second record.

## The records

### Customer

`name`, `address` (`Address`: street, city, state, zip), `email`, `phone`. Tickets are linked to customers through a
join table on the server, not a field here.

### Ticket

| Field | Notes |
|-------|-------|
| `device` | `Device`: manufacturer, model, serial |
| `status` | `TicketStatus`: a `TicketStatusType` and the time it was set |
| `priority` | `TicketPriority`: `LOW`, `NORMAL`, `HIGH` or `URGENT`, and the time it was set |
| `number` | `TicketNumber`, given out by the server when the ticket is added |
| `problem`, `condition`, `accessories` | What the customer reported, damage noted at check-in, what was left with it |
| `estimateCents` | Quoted price |
| `promisedAt` | When the customer was told it'd be ready, epoch millis |
| `pickedUpAt` | Set by the server at pickup |

Statuses:

| `TicketStatusType` | Meaning | Shown as |
|--------------------|---------|----------|
| `IN_PROGRESS` | Being worked on | In progress |
| `ON_HOLD` | Waiting on parts, the customer, ... | On hold |
| `DONE` | Repair finished, waiting for the customer | Ready for pickup |
| `PICKED_UP` | Closed. Only set by a pickup, never picked by hand | Picked up |

`isOpen` is true for the first two. `TicketStatusType.selectable` is the list a person can choose from. The wording
on screen comes from the `TicketStatusType.label` extension in the UI kit's `TmBadge.kt`, not from `shared`.

`TicketNumber` prints as `26-00000042` (two digit year, eight digit count) and `parse()` reads that or the short
form `26-42`. The count starts over each year.

`TicketSignature` is the customer's signature from check-in, kept as pen strokes scaled to 0..1 rather than an
image so it prints sharp at any size. It copies in the repair terms they agreed to, so changing the shop's terms
later doesn't change what an old ticket says was signed. The client sends a `SignatureInput` (strokes, pad aspect
ratio, typed name) and `SignatureInput.problem()` checks it; the server fills in the terms, time and who took it.

### Inventory

`name`, `description`, `stock`, `costCents` (what the shop pays) and `priceCents` (what it charges).

`stock` defaults to `Int.MAX_VALUE`, which means **stock isn't tracked** for that item: pickup doesn't take from it,
and the counter shows "Stock not tracked". In the inventory dialog it's the "Track stock" checkbox, and lists and
details show "Not tracked" rather than the number. The server never lets a tracked count go below zero, and a plain
inventory update doesn't change it; see [Counter](../server/counter.md#stock-counts).

### User

`username`, `name`, `email`, `phone`, `accessLevel`, `status`, `needsSetup`, `hasPin`, `pinLocked`, and `password`.

- `password` is only filled in by a client creating a user. The server stores a hash and calls `withoutPassword()`
  before sending a user anywhere. The PIN itself never leaves the server; `hasPin` says whether there is one.
- `AccessLevel` is `RESTRICTED` (0), `NORMAL` (1) or `ADMIN` (2). `NONE` (9) is only for reading old data, where it
  meant deactivated, and never `meets()` anything.
- `UserStatus` is `PENDING` (asked for an account or was invited), `ACTIVE` or `DEACTIVATED`.
- `canAccess(minimum)` is the one check for whether a user may do something: active, setup finished, and a level
  that meets `minimum`. The server's auth providers use it on every request.

`UsernameRules` and `PinRules` are checked on both sides, so the client can say what's wrong before sending:

- usernames are 3 to 32 characters of `a-z 0-9 . _ -`, stored lowercase,
- PINs are 6 digits, not all the same digit and not a straight run like `123456`. Five wrong tries lock it.

### Note

`author`, `message` and `images` (a list of `StoredImage`). Every record type can have notes, and notes are their
own main items so they can be edited and removed by id.

### StoredImage

Details of a photo the server holds: `description`, `tags`, and the original's `width`, `height` and `sizeBytes`.
The bytes live on the server's disk. A photo never changes once uploaded, so replacing one means uploading a new one.

`ImageVariant` lists the sizes the server keeps (`THUMB` 320px, `DISPLAY` 1600px, `ORIGINAL` capped at 2560px) and
`ImageLimits` the upload limits (25 MB, 50 megapixels). Both sides read them from here.

## Counter and money

| Type | What it is |
|------|------------|
| `Invoice` | A ticket's bill: `charges`, `payments`, `discountCents`, tax rate and totals, `status` (`OPEN`, `PAID`, `VOID`), `number` (`INV-26-00000042`) |
| `Charge` | One line: `LABOR` (quantity is hours), `PART` (linked to an inventory item) or `OTHER`, with a unit price and whether it's taxable |
| `Payment` | Money taken: method (`CASH`, `CARD`, `CHECK`, `OTHER`), amount, when and by whom |
| `TicketSummary` | A ticket with its customer and tech as `EntityReference`s, for lists that would otherwise need extra lookups |
| `ShopProfile` | The shop's name, address, contact details, tax rate, invoice footer, repair terms, repeat repair window and default paper size |

There's one invoice per ticket. Totals are worked out by `InvoiceMath`, see [Money](money.md).

## Stations and audit

| Type | What it is |
|------|------------|
| `Station` | A device approved for PIN login: name, device id, `StationStatus` (`PENDING`, `APPROVED`), kiosk settings |
| `StationCredentials` | The station's id and secret, given once at registration and sent with every PIN login |
| `KioskSettings` | Kiosk on or off, and the idle timeout (30 to 3600 seconds) |
| `StaffMember` | Someone who can PIN in at a station |
| `AuditEntry` | Who did what (`AuditAction`) to which record, and when |

## Rules for changing a model

- **Give every new field a default.** Older clients and servers then read JSON without it, and new database
  columns can be filled on existing rows. Both sides' JSON setup ignores unknown keys, so adding fields is safe in
  both directions; removing or renaming one is not.
- **Fields the server owns** (like `Ticket.number`, `Ticket.pickedUpAt`, invoice totals) are still on the model so
  the client can show them, but the server ignores them when a client sends the record back.
- A new field on a main record needs adding on every layer, see [Entity layers](../architecture/entity-layers.md).

## Gotchas

- **Older invoice ids aren't time based.** `Invoice`, `Charge` and `Payment` default to `Uuid.generateV7()` like
  everything else, but rows saved before that have random ids, and a charge keeps whatever id the client sent. Read
  times from `Invoice.createdAt` and `Payment.receivedAt`, not from their ids.
- **`Address.fromString` and `Device.fromString` split on spaces**, so a two word street or model name is cut up.
  Nothing in the app calls them now; don't start.
- **`Customer.cleanName()` and `User.cleanName()`** treat `:` as a name separator, left over from an older way of
  storing names.

## See also

- [Results and errors](results-and-errors.md)
- [Money](money.md)
- [DTOs, caches and platform helpers](dtos-and-platform.md)
