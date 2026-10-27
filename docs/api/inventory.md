# Inventory API

`/api/data/inventory`, from [`InventoryRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/InventoryRoute.kt).
Parts and anything else the shop stocks. Anyone logged in can look; Normal access changes. Stock is guarded more
than the other fields: `/update` leaves it alone, it's changed by hand only through `/set/stock`, and otherwise only
by pickups and voids (see [Stock counts](../server/counter.md#stock-counts)). An item's notes are on the
[Notes](notes.md#inventory) page.

A `stock` of `Int.MAX_VALUE` (2147483647) means the item's stock isn't tracked. `taxClass` is the shop's
[tax class](shop.md#post-apishopupdate) it's sold at, null for the main rate; parts added from it start with it.
`sku` (the shop's own code) and `barcode` (what a scanner reads) are saved trimmed and are each unique when set,
ignoring case. `category` groups items. `reorderPoint` is where the item counts as low stock, null for the admin
screen's threshold ([low stock](admin.md#get-apiadmininventorylow-stock)). `supplierIdentifier` is who it's
usually [ordered](purchase.md) from.

**Stock per location.** The catalog is the company's; each [location](location.md) counts its own stock. Every read
gives `stock` and `reorderPoint` for the location the request works at (`stock` added up and no reorder point for an
admin working at `all`), and `stockAt` with every location's `LocationStock { locationIdentifier, stock,
reorderPoint }`. A location with no row has none. Whether an item is tracked is the same everywhere.

## Reading

### `GET /api/data/inventory/get/{identifier}`

**Auth:** restricted

- **`200`** `Inventory`. **`404`** no such item.

### `GET /api/data/inventory/fetch`

**Auth:** restricted

A page of items, by name.

- **Query:** `offset` (default 0), `limit` (default 50, at most 200). **`400`** if either isn't a whole number.
- **`200`** a list of `Inventory`.

### `GET /api/data/inventory/search`

**Auth:** restricted

Items whose name, description or category contains the query, or whose SKU, barcode or id starts with it.

- **Query:** `query`, `limit` (default 20, at most 200).
- **`200`** a list of `SearchResult`.

### `GET /api/data/inventory/code/{code}`

**Auth:** restricted

The item whose barcode, or else SKU, is exactly `code`, ignoring case. What the counter calls when a scanner reads
a label.

- **`200`** `Inventory`. **`404`** no item has that code.

### `GET /api/data/inventory/categories`

**Auth:** restricted

Every category in use, by name, for picking one.

- **`200`** a list of strings.

### `GET /api/data/inventory/get/images/{identifier}`

**Auth:** restricted

The item's own photos (not its notes' photos).

- **`200`** a list of `StoredImage`. **`404`** no such item.

## Changing

### `POST /api/data/inventory/add`

**Auth:** normal

- **Body:** `Inventory`, with an id the client made. Its `stock` and `reorderPoint` are the count where the request
  works; the other locations start with none. **`201`** the `Inventory` as saved.
- **`400`** `Stock can't be below zero`, a SKU or barcode over 64 characters, a barcode with a space, a category
  over 60 characters, or a negative reorder point. **`409`** another item has that SKU or barcode (`SKU SCR-13 is
  already on iPhone 13 screen`). **`500`** not saved.

### `POST /api/data/inventory/update`

**Auth:** normal

Replaces everything but the stock count, which is kept as it is on the server whatever is sent. The reorder point
is the one where the request works.

- **Body:** `Inventory`. **`202`** saved. **`400`** and **`409`** as for `/add`. **`404`** no such item.

### `POST /api/data/inventory/set/stock/{identifier}`

**Auth:** normal

Sets the stock count by hand, only if it's still what the client last saw, so a pickup in between isn't lost.

- **Body:** `StockChangeRequest { from, to, locationIdentifier }`: the count at that location, where the request works
  when it's null. Use `Int.MAX_VALUE` for "not tracked", either way, which stops or starts counting it at every
  location.
- **`202`** saved. **`400`** `to` below zero, or not an active location. **`404`** no such item.
- **`409`** the count isn't `from` any more: `Stock changed to 3 since this was opened, check the count and try
  again`.

### `POST /api/data/inventory/transfer/{identifier}`

**Auth:** normal

Moves parts from one location to another in one transaction: taken from the first only while there are enough there
(in the same statement, like a pickup), then added at the second. Each move is kept in `stock_transfers`.

- **Body:** `StockTransferRequest { fromLocation, toLocation, quantity, note }`, two different active locations,
  1 to 100000 parts, a note up to 500 characters.
- **`201`** the `StockTransfer { itemIdentifier, itemName, fromLocation, toLocation, quantity, note, movedBy, movedAt }`.
  Audit logged.
- **`400`** what `StockTransferRequest.problem` says, a location that isn't an active one, or an item whose stock isn't
  counted. **`404`** no such item. **`409`** not enough there: `Only 2 at Main Street`.

### `GET /api/data/inventory/transfers/{identifier}`

**Auth:** restricted

- **`200`** the item's last 50 moves, newest first.

### `POST /api/data/inventory/set/images/{identifier}`

**Auth:** normal

Sets the item's photos as a whole list. Photos just uploaded (`PENDING`) are linked; ones the item had that aren't
in the list are marked deleted. Ids that are neither are ignored. Descriptions and tags are taken from the list.

- **Body:** a list of `StoredImage`. **`202`** saved. **`404`** no such item.

### `DELETE /api/data/inventory/remove/{identifier}`

**Auth:** normal

Deletes the item, its notes and its photos (marked deleted, cleared by the next sweep). Invoice lines that used it
keep their copy of its name and price.

- **`202`** deleted. **`404`** no such item.

## See also

- [Notes API](notes.md#inventory)
- [Photos API](image.md)
- [Counter: check-in, invoices and pickup](../server/counter.md#stock-counts)
