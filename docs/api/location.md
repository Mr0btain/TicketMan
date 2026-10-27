# Locations API

`/api/location`, from [`LocationRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/LocationRoute.kt).
The shop's locations. One server runs every shop: each location keeps its own tickets, invoices, stock counts,
stations, appointments, purchase orders and staff, and numbers its own tickets; customers, the catalog of items and
the settings are the company's, so a customer's tickets show wherever they're looked up. Running a single location
works as before: nothing about locations shows until there's a second one.

**The main location.** The server makes one on start when it has none (`LocationDatabase.ensureMain`), and
everything from before locations belongs to it: the `stock-per-location` fix moves each item's count to it once,
and every start gives rows without a location the main one. Its code is blank, so its numbers stay `26-00000042`.

**Where a request works.** Every client request carries `X-TicketMan-Location` (`LOCATION_HEADER`): an active
location's id, or `all` (`ALL_LOCATIONS`) from an admin looking at every one. Without it, or with a retired or
unknown one, or `all` from someone who isn't an admin, the server uses the caller's home location (`User.homeLocation`),
then the main one (`routing/Locations.kt`: `locationScope()`, and `workingLocation()` for writes, which is never
`all`). A row with no location counts as the main location's.

| | Kept to where the request works | Everywhere |
|-|----------------------------------|------------|
| Tickets | [`/fetch`](ticket.md#get-apidataticketfetch), `/fetch/mine`, the [counter queue](counter.md#get-apicounterqueue) | Search, by number, a customer's tickets |
| New work | Tickets, check ins and their invoices, sales, appointments, purchase orders, stations and users get its location | |
| Stock | Each item's `stock` and `reorderPoint` are that location's (added up for `all`); `stockAt` has every location's | The catalog itself |
| Invoices | [`/fetch`](invoice.md#get-apiinvoicefetch), exports, [reports](report.md), the admin overview, end of day and low stock | |
| Staff | The [users list](user.md#get-apiuserfetch), by home location | Search, PIN lists |
| Card readers | The [counter's options](payment.md#get-apipaymentoptions): readers for that location and for every location | |
| Shop details | [`/api/shop/here`](shop.md#get-apishophere): its address, contacts and tax rates | `/api/shop` stays the company's |

**Numbers.** Tickets, invoices and purchase orders count per location's code and year, unique on
(prefix, year, sequence): `DT-26-00000042`, `INV-DT-26-00000042`, `PO-DT-26-00000042` (`TicketNumber.prefix` and
the like). Changing a code changes numbers handed out from then on. A ticket keeps its number when it moves.

**Tax and stock.** An invoice is taxed at its location's rates (`ShopProfile.at`, kept on the invoice like before)
and its parts come out of that location's stock at pickup or sale; refunds and voids put them back there, and
receiving an order adds to the order's location. A location's own rates are `taxRate` (null for the shop's) and
`taxClassRates`, by the shop's tax class ids, so items keep their classes everywhere.

### `GET /api/location`

**Auth:** restricted

- **`200`** every `Location`, retired ones too, main first.

### `POST /api/location/add`

**Auth:** admin

- **Body:** `Location { identifier, code, name, street, city, state, zip, phone, email, taxRate, taxClassRates }`.
  The code is saved in capitals; `main` is ignored.
- **`201`** the location as saved. Audit logged.
- **`400`** what `Location.problem` says: no name, no code (only the main one goes without), a code that isn't 1 to
  4 capitals or digits or is `INV` or `PO`, a code or name another location has, or a tax rate that isn't a percent.
- **`409`** one with that id exists.

### `POST /api/location/update`

**Auth:** admin

Changes everything but whether it's the main one. Setting `retired` takes it out of use: it's not offered for new
work, requests saying they work there fall back to the caller's home, and its records stay.

- **Body:** `Location`.
- **`200`** the location as saved. Audit logged, saying when it was retired or its code changed.
- **`400`** as for adding, or retiring the main location. **`404`** no such location.

## See also

- [Running a server: more than one location](../operations.md#more-than-one-location)
- [Tickets API: moving a ticket](ticket.md#post-apidataticketmoveidentifier)
- [Admin API: a station's location](admin.md#post-apiadminstationsidentifierlocation)
