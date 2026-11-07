# Repair menu API

`/api/catalog`, from [`CatalogRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/CatalogRoute.kt).
The shop's repair menu: device models (`CatalogModel { identifier, kind, manufacturer, model, repairs }`), each with
the repairs offered on it at a set price (`RepairOffer { identifier, name, priceCents, inventoryIdentifier,
warrantyDays, taxClass }`). Check-in picks a model from the device typed (shared `RepairCatalog.matches`) and sends
the repairs ticked as [`CheckInRequest.repairs`](counter.md#post-apicountercheck-in), which go on the ticket's
invoice as lines (`RepairCatalog.charge`): the part when the repair uses one, so its stock comes out at pickup and the
profit counts its cost, otherwise labor. The line is a copy, so changing or removing the menu later doesn't touch
invoices. Kept in `repair_catalog` (`CatalogDatabase`), each model's repairs as JSON beside it.

### `GET /api/catalog`

**Auth:** restricted

- **`200`** every `CatalogModel`, by manufacturer then model.
- **`500`** the menu couldn't be read.

### `POST /api/catalog/add`

**Auth:** admin

- **Body:** `CatalogModel`. Names are trimmed.
- **`201`** the model as saved. Audit logged.
- **`400`** what `RepairCatalog.problem` says (no model, a manufacturer and model already on the menu ignoring case,
  a repair with no name or the same name twice in a model, a negative price, a warranty past
  `Warranty.MAX_DAYS`, more than 60 repairs), a tax class the shop doesn't have, or a part that isn't in inventory.
- **`409`** one with that id exists.

### `POST /api/catalog/update`

**Auth:** admin

- **Body:** `CatalogModel`, its repairs replacing the ones it had.
- **`200`** the model as saved. Audit logged.
- **`400`** as for `/add`. **`404`** no such model.

### `DELETE /api/catalog/remove/{identifier}`

**Auth:** admin

- **`202`** removed. Audit logged. **`404`** no such model.
