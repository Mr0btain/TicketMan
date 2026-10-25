# Customers API

`/api/data/customer`, from [`CustomerRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/CustomerRoute.kt).
Anyone logged in can look; Normal access changes. The client's `CustomerRemoteRepository` calls these, through the
shared paths in `RemoteRepository` (see [Entity layers](../architecture/entity-layers.md)). A customer's notes are on
the [Notes](notes.md#customers) page. `notifyEmail` and `notifySms` say whether the customer agreed to automatic
messages about their repairs on each channel, off unless set ([Messages API](notify.md)). `creditCents` is their
store credit, which only refunds, spending it and an admin's adjustment change; saving the customer leaves it alone.

## Reading

### `GET /api/data/customer/get/{identifier}`

**Auth:** restricted

- **`200`** `Customer`. **`404`** no such customer.

### `GET /api/data/customer/fetch`

**Auth:** restricted

A page of customers, by name.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Customer`.

### `GET /api/data/customer/search`

**Auth:** restricted

Customers matching any part of their name ("jane doe" works), the start of their email or id, or their phone
number ignoring formatting.

- **Query:** `query`, `limit` (default 20, at most 200).
- **`200`** a list of `SearchResult`, the text being the name and phone.

### `GET /api/data/customer/get/tickets/{identifier}`

**Auth:** restricted

The customer's tickets, newest first.

- **`200`** a list of `Ticket`. **`404`** no such customer.

### `GET /api/data/customer/credit/{identifier}`

**Auth:** restricted

The customer's store credit history, newest first, up to 200.

- **`200`** a list of `CreditEntry { identifier, amountCents, balanceAfterCents, reason, invoiceIdentifier, at, by }`,
  `amountCents` negative when spent. **`404`** no such customer.

### `GET /api/data/customer/devices/{identifier}`

**Auth:** restricted

The customer's devices, oldest first. They're made at check in, or kept on file with the `POST` below.

- **`200`** a list of `CustomerDevice { identifier, customerIdentifier, device, color, notes, createdAt }`, `device`
  being a `Device { manufacturer, model, serial, kind }`. **`404`** no such customer.

### `GET /api/data/customer/device/{identifier}/tickets`

**Auth:** restricted

The device's tickets, newest first: its repairs over time.

- **`200`** a list of `Ticket`. **`404`** no such device.

### `GET /api/data/customer/get/invoices/{identifier}`

**Auth:** restricted

The customer's invoices, their tickets' and their counter sales, newest first, up to 200.

- **`200`** a list of `Invoice`. **`404`** no such customer.

## Changing

### `POST /api/data/customer/credit/{identifier}`

**Auth:** admin

Gives store credit, or takes it off, by hand. Written to the credit history and the audit log.

- **Body:** `BalanceAdjustRequest { amountCents, reason }`, `amountCents` negative to take off.
- **`200`** the `Customer` with the new `creditCents`. **`400`** zero, or no reason. **`404`** no such customer.
  **`409`** taking off more than they have (`Sam only has $20.00 of store credit`).

### `POST /api/data/customer/devices/{identifier}`

**Auth:** normal

Keeps a device on file for the customer in the path, whatever `customerIdentifier` the body says.

- **Body:** `CustomerDevice`, with an id the client made. **`201`** the `CustomerDevice`. **`400`** no make or
  model. **`404`** no such customer. **`409`** that id is taken.

### `POST /api/data/customer/device/update`

**Auth:** normal

Saves a device's details, never who owns it. Tickets keep the copy of the details they were checked in with.

- **Body:** `CustomerDevice`. **`200`** the `CustomerDevice`. **`400`** no make or model. **`404`** no such device.

### `DELETE /api/data/customer/device/{identifier}`

**Auth:** normal

Deletes the device. Its tickets stay, with their copy of its details.

- **`202`** deleted. **`404`** no such device.

### `POST /api/data/customer/add`

**Auth:** normal

- **Body:** `Customer`, with an id the client made. **`201`** the `Customer` as saved. **`500`** not saved,
  including when that id is already taken.

### `POST /api/data/customer/update`

**Auth:** normal

Replaces the customer's details.

- **Body:** `Customer`. **`202`** saved. **`404`** no such customer.

### `DELETE /api/data/customer/remove/{identifier}`

**Auth:** normal

Deletes the customer, their notes and their devices; the note photos are cleared up by the next sweep. Their tickets
stay, with no customer.

- **`202`** deleted. **`404`** no such customer. **`409`** they still have store credit, which would go with them.

### `POST /api/data/customer/add/ticket/{identifier}`

**Auth:** normal

Links a ticket to this customer, in place of any customer it had.

- **Body:** the ticket's id. **`202`** linked. **`404`** no such customer or ticket.

The same link can be made from the ticket's side with
[`POST /api/data/ticket/set/customer/{identifier}`](ticket.md#post-apidataticketsetcustomeridentifier).

## See also

- [Notes API](notes.md#customers)
- [Records screen](../ui/screens/records.md)
- [Domain models](../shared/domain-models.md)
