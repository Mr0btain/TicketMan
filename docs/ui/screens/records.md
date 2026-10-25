# Records screen

The Records screen (`Destination.RECORDS`, the app's start screen) is where customers, tickets and inventory are
looked up, added, edited and deleted. Each record type is a tab with the same layout: a toolbar, a list and the
picked record's details. Touch this when changing what a record shows or how it's edited. The data side (pagers,
caches, seeds, saving) is covered in [Records: seeds, caches and pagers](../../client/records.md).

![The Tickets tab: toolbar, list and the picked ticket's details](../../images/records-tickets.png)

## How it works

[`RecordsScreen`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/records/RecordsScreen.kt)
draws `TmTabs` (Customers, Tickets, Inventory) and one panel for the open tab. The open tab is
`MainViewModel.selectedMainTabIndex`, not screen state, so the admin screen can switch it when it links here and the
"Start tab" setting can pick it.

Each panel (`CustomerPanel`, `TicketPanel`, `InventoryPanel`) is a
[`RecordsLayout`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/records/components/RecordsLayout.kt)
with three slots:

| Slot | Built with | What it does |
|------|-----------|--------------|
| `toolbar` | `TmPanelTopBar` | Search on the left (picking a result selects it), delete and add on the right. `extras` holds anything else, like the tickets tab's My tickets / All tickets switch |
| `list` | `RecordsList` | A `TmTableHeader` and a `TmLazyColumn` of `TmTableRow`s, or an empty state. Loads pages as it scrolls through `OffsetPagerLoader` |
| `detail` | `RecordDetail` | The picked record: a header with an Edit button, then `DetailSection`s scrolling under it. An empty state when nothing is picked |

At 760 dp and wider the list and details sit side by side (56 / 44); narrower, the details stack under the list.
Under 560 dp (`NARROW_LIST_WIDTH`) each list drops to its name and the one or two columns that matter, with the
rest as a caption under the name (`RecordNameCell`): customers show their phone and email under the name, tickets
the device under the number beside the status, and inventory its SKU, category or description under the name
beside stock and price.

```mermaid
sequenceDiagram
    participant U as User
    participant S as RecordsScreen
    participant H as TicketRecords
    participant D as TicketDialog
    U->>S: clicks a row
    S->>H: select(id)
    H-->>S: seed loads, row highlighted (pendingId while loading)
    U->>S: Edit
    S->>D: isVisible = true, seed
    U->>D: changes fields
    D->>H: seed.change...() (Save enabled once isDirty)
    U->>D: Save ticket
    D->>H: save()
```

### What each tab shows

| Tab | List columns | Details | Dialog |
|-----|--------------|---------|--------|
| Customers | Name, phone, email, street | Contact, their tickets (click to open), notes | `CustomerDialog`: first and last name, phone, email, address, their tickets and a search to link more, notes |
| Tickets | Number, device, priority, status | Status and priority badges, customer, technician, device, created, estimate, problem, work log (`TicketWorkSection`, with Log work), updates | `TicketDialog`: customer and technician search, New customer, device, problem, status, priority, updates |
| Inventory | Name, SKU and category (or description), stock, cost, price | Description, SKU, barcode, category and **Print label**, price, cost, stock, reorder point, photos, notes | `InventoryDialog`: name, description, SKU, barcode, category, reorder point, stock (or untracked), cost, price, usual supplier, tax class when the shop has more than one rate, photos, notes |

Users are records too (`UserRecords`), but they're managed on the [admin screen](admin.md), not here.

### Dialogs

`RecordsScreen` owns every dialog and whether it's showing; the panels only call back (`onAddCustomer`,
`onEditTicket`, ...). The dialogs edit the handler's **seed** directly: every field change calls a `change...()` on
it, and the dialog's Save stays off until `seed.isDirty`.

- **Add** clears the selection first (`clearSelection()`), so the seed is a blank new record.
- **Cancel** re-selects the same record, which reloads the seed from the cache and throws the edits away. If the
  record has been deleted on another device meanwhile, the reload finds nothing: the handler clears the seed,
  reloads the list and says it may have been deleted (`forgetIfOpen`).
- **Save** calls the handler's `save()` and closes the dialog, for all three record types. A new ticket's number
  shows in the details and the list once the server has given it one.
- **Notes** are added and edited in the kit's `TmEditNoteDialog`, on the seed. They're sent when the record is
  saved, not when the note dialog closes (see [`syncNotes`](../../client/records.md#saving)).
- **Delete** opens `TmConfirmDeleteDialog(isTwoStep = true)`, which asks twice, then calls the handler's
  `remove()` with the selected record. The record stays selected until the server agrees; if it refuses, a
  snackbar says why ("Customer not deleted: ...").

- **New customer** in the ticket dialog opens the kit's `TmCustomerDialog` on top. Saving it adds the customer
  straight away through `CustomerRecords.addCustomer()` (so it stays even if the ticket is then cancelled) and picks
  it as the ticket's customer.
- **Linking tickets** to a customer: the dialog's ticket search adds the picked ticket to `seed.tickets`, after
  `CustomerRecords.ticketToLink()` loads it and the customer it's on now. A ticket on someone else asks first
  ("Move 26-00000042?"), since a ticket has one customer and linking moves it. Save sends
  `POST /api/data/customer/add/ticket/{id}` for each one not linked yet. Saving never unlinks, so only tickets
  picked in this dialog have an X (shown as "Linked when saved"); a ticket leaves a customer by being linked to
  another.

Clicking a photo anywhere on the screen opens one shared `TmImageView` at that photo.

### Work log

The ticket details show its work log, loaded with the ticket by `TicketRecords.work` (`TicketWork`), with the total.
**Log work** opens `WorkDialog`: time (what was done, hours, rate per hour starting at the shop's labor rate) or a
part (picked from an inventory search, how many, price each starting at the item's). Entries are saved straight
away, not with the ticket, since each one changes the invoice; taking one off asks first. Restricted users see the
log without the buttons, and a picked up ticket's can't change.

### Checking for updates

[`CheckForUpdates`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/CheckForUpdates.kt)
runs a check on the interval set in Settings (`SettingsHandler.updateInterval`, 30 seconds by default), while the
window has focus and the screen is on. A window back after longer than the interval checks straight away. The
records screen reloads the open tab's list (`pager.refreshCurrentData()`) and the open record
(`reloadSelected()`), and skips it all while any dialog is open. `reloadSelected()` leaves a new or changed seed
alone, and loads with `quiet = true`, so a server that can't be reached doesn't pop a snackbar every 30 seconds; a
record deleted elsewhere is still dropped. The counter and admin screens use the same composable.

### View only

Restricted users can look but not change anything. `RecordsScreen` reads `session.canEdit` and passes it down:

- `TmPanelTopBar(canChange = false)` drops add and delete and shows a "View only" badge,
- `RecordDetail(canEdit = false)` drops the Edit button,
- clicking a ticket on a customer opens it on the Tickets tab instead of in the edit dialog.

The handlers refuse changes too, and the server checks again. See
[Restricted users](../../client/records.md#restricted-users).

![The same screen for a Restricted user](../../images/records-view-only.png)

## Where it lives

| What | Where |
|------|-------|
| The screen, dialogs and wiring | `ui/screens/records/RecordsScreen.kt` |
| Shared layout, list and detail | `ui/screens/records/components/RecordsLayout.kt` |
| Per type: panel, list pane, info pane, dialog, handler | `ui/screens/records/components/<customer|ticket|inventory>/` |
| Notes syncing | `ui/screens/records/components/RecordNotes.kt` |
| Render test | `ScreenRenderTest.records()`, writing `records-*.png` |

## Adding a field to a record

1. Add it to the model and the server layers (see [Entity layers](../../architecture/entity-layers.md)).
2. Add it to the seed's `fields()` and give the seed a `change...()` for it.
3. Show it in the info pane with a `TmKeyValueRow` inside the right `DetailSection`.
4. Edit it in the dialog with a kit field calling the seed's `change...()`.
5. If it should be a column, add a `TmColumn` to the list pane's `COLUMNS` and a cell for it.

## Gotchas

- **The pending row.** While a picked record loads, `selection.pending` holds its id and the list highlights that
  row, so the click shows straight away. Pass `pendingId ?: seed.identifier` as the selected row.
- **Changing the ticket list's scope** scrolls it back to the top, since it's a different set of tickets.
- **Dialogs need a picked record for Edit.** `onEditTicket` does nothing for a new seed; the button is only there
  once something is selected.

## See also

- [Records: seeds, caches and pagers](../../client/records.md)
- [Notes in the database layer](../../server/database.md)
- [UI kit](../kit.md)
