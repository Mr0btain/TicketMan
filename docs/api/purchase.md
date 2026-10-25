# Purchasing API

`/api/purchase`, from [`PurchaseRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/PurchaseRoute.kt).
Suppliers and purchase orders. Anyone logged in can look; Normal access orders and receives. How orders move
through their statuses, and what they do to stock and tickets, is in [Purchasing](../server/purchasing.md).

## Suppliers

### `GET /api/purchase/suppliers`

**Auth:** restricted

Every supplier, by name.

- **`200`** a list of `Supplier { identifier, name, contactName, phone, email, website, accountNumber, notes }`.

### `POST /api/purchase/suppliers/add`

**Auth:** normal

- **Body:** `Supplier`, with an id the client made. Text is saved trimmed.
- **`201`** the `Supplier` as saved. **`400`** no name, or a name over 120 characters. **`409`** that id is taken.

### `POST /api/purchase/suppliers/update`

**Auth:** normal

- **Body:** `Supplier`. **`200`** the `Supplier` as saved. **`400`** as for `/add`. **`404`** no such supplier.

### `DELETE /api/purchase/suppliers/{identifier}`

**Auth:** normal

Orders keep the supplier's name; items whose `supplierIdentifier` pointed at it just don't find it any more.

- **`202`** deleted. **`404`** no such supplier.

## Orders

### `GET /api/purchase/orders`

**Auth:** restricted

A page of orders, newest number first.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200), `status`: a comma separated list of
  `PurchaseStatus` names (`DRAFT`, `ORDERED`, `PARTLY_RECEIVED`, `RECEIVED`, `CANCELLED`) to only list those.
  **`400`** a paging value that isn't a whole number, or an unknown status.
- **`200`** a list of `PurchaseOrder { identifier, number, supplierIdentifier, supplierName, status, lines, note,
  createdAt, createdBy, orderedAt, receivedAt, expectedAt }`, each line a `PurchaseLine { identifier,
  inventoryIdentifier, description, quantity, receivedQuantity, unitCostCents, ticketIdentifier }`.

### `GET /api/purchase/orders/{identifier}`

**Auth:** restricted

- **`200`** `PurchaseOrder`. **`404`** no such order.

### `GET /api/purchase/orders/ticket/{ticketIdentifier}`

**Auth:** restricted

Orders with a line for the ticket, newest first, for the ticket card's parts.

- **`200`** a list of `PurchaseOrder`.

### `POST /api/purchase/orders/add`

**Auth:** normal

A new draft, numbered like `PO-26-00000001`. Lines can be added later.

- **Body:** `PurchaseOrderRequest { supplierIdentifier, lines, note, expectedAt }`. A line's blank description is
  the item's name; `receivedQuantity` is ignored.
- **`201`** the `PurchaseOrder`. **`400`** no such supplier, a line for no item or a ticket that doesn't exist, a
  quantity under one, or a negative cost.

### `POST /api/purchase/orders/{identifier}/update`

**Auth:** normal

Replaces a draft's supplier, lines, note and expected date.

- **Body:** `PurchaseOrderRequest`. **`200`** the `PurchaseOrder`. **`400`** as for `/add`. **`404`** no such
  order. **`409`** it was placed already.

### `POST /api/purchase/orders/{identifier}/place`

**Auth:** normal

The draft goes to the supplier and becomes `ORDERED`. Tickets with lines on it move to the shop's
`partsOrderedStatus`.

- **`200`** the `PurchaseOrder`. **`400`** no lines or no supplier. **`404`** no such order. **`409`** not a draft.

### `POST /api/purchase/orders/{identifier}/receive`

**Auth:** normal

Parts that arrived: each line's `receivedQuantity` goes up, the parts go into stock (untracked items stay untracked)
and each item's cost becomes the line's, all in one transaction. The order is `RECEIVED` once nothing is
outstanding, `PARTLY_RECEIVED` before. Tickets whose parts, on every order, have all arrived move to the shop's
`partsArrivedStatus`.

- **Body:** `ReceiveRequest { lines }`, each a `ReceivedLine { lineIdentifier, quantity }`.
- **`200`** the `PurchaseOrder`. **`400`** nothing arriving, a line not on the order, a negative count, or more
  than is still to come (`Only 1 of Screen are still to come on PO-...`). **`404`** no such order. **`409`** the
  order is a draft, received or cancelled.

### `POST /api/purchase/orders/{identifier}/cancel`

**Auth:** normal

Stops waiting on what hasn't come. What arrived stays in stock.

- **`200`** the `PurchaseOrder`. **`404`** no such order. **`409`** already received or cancelled.

### `DELETE /api/purchase/orders/{identifier}`

**Auth:** normal

Deletes a draft.

- **`202`** deleted. **`404`** no such order. **`409`** it was placed; cancel it instead.

### `POST /api/purchase/orders/part`

**Auth:** normal

A part a ticket needs, added to the supplier's newest draft, or a new draft when there's none. The ticket moves when
that order is placed.

- **Body:** `OrderPartRequest { ticketIdentifier, inventoryIdentifier, quantity, supplierIdentifier }`.
  `supplierIdentifier` null means the item's own supplier.
- **`200`** the draft `PurchaseOrder`. **`400`** no such item or supplier, a quantity under one, or no supplier
  given for an item without one. **`404`** no such ticket. **`409`** the ticket was picked up.

## See also

- [Purchasing](../server/purchasing.md)
- [Inventory API](inventory.md)
