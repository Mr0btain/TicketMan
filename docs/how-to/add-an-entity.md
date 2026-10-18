# Add a record type

A new kind of record, like customers, tickets and inventory, with its own list, details, notes and API. It's the
biggest change in the codebase: every layer gets a new class, and several places that list the record types by
hand have to learn about it. This guide uses a `Supplier` as the example and copies the customer classes, the
simplest full record type.

Read [Entity layers](../architecture/entity-layers.md) first; it explains each layer with Ticket.

## Shared

1. **Model**: `shared/.../data/supplier/Supplier.kt`, a `@Serializable data class` extending `MainItem()`, with
   `override val identifier: Uuid = Uuid.generateV7()` and a default on every field.
2. **Errors**: `shared/.../error/SupplierError.kt`, an enum implementing `Error`, with at least `NOT_FOUND`. The
   database class logs it.

## Server

3. **Tables** in `server/.../database/tables/supplier/`:

   ```kotlin
   object SupplierTable : UuidTable("supplier") {
       val name = text("name").default("")
       ...
   }

   // Its notes, in the shared NoteTable
   object SupplierNoteTable : Table("supplier_note") {
       val supplier = reference("supplier_id", SupplierTable, onDelete = ReferenceOption.CASCADE)
       val note = reference("note_id", NoteTable, onDelete = ReferenceOption.CASCADE)
       override val primaryKey = PrimaryKey(supplier, note)
   }
   ```

   and `SupplierEntity` with a property per column, `var notes by NoteEntity via SupplierNoteTable`, and a
   `toSupplier()` that's the only place a row becomes a model. Copy `tables/customer/` for the exact shape.
4. **Register the tables** in `LocalDatabase.tables`. New tables are created on the next start; they don't need
   `migratedTables` until they gain a column later.
5. **Database class**: `SupplierDatabase` extending `Database<Supplier>("SupplierLocalDatabase")`. Implement each
   abstract member (`add`, `addAll`, `update`, `remove`, `getById`, `getPagedItems`, `count`, `search`, `addNote`,
   `getNotes`, `noteLinkColumn`) with `dbQuery` for reads and `dbWrite` for writes, copying `CustomerDatabase`:
   - `noteLinkColumn = SupplierNoteTable.note`, so note updates and removes are checked against this type,
   - `addNote` through the base class's `attachNote(...)`,
   - `remove` takes the notes first, deletes the record, then calls `deleteNotes(notes)`. Clear any join table
     without a cascade before deleting.

   Add `val supplierDatabase = SupplierDatabase()` to `LocalDatabase`.
6. **Tell the note and photo clean ups about it.** Two places list every note join table by hand, and treat notes
   missing from them as left behind:
   - `ImageDatabase.markUnreferencedDeleted` (the hourly sweep): add `SupplierNoteTable` to `attachedNotes`. Miss it
     and photos on supplier notes are deleted a day after they're saved.
   - `LocalDatabase.removeDetachedNotes` (on every start): add it to `attached` there too. Miss it and every
     supplier note is deleted the next time the server starts.

   If the record has photos of its own (like inventory's `InventoryImageTable`), add that join table to
   `referenced` in `markUnreferencedDeleted` as well.
7. **Routes**: `routing/SupplierRoute.kt` with `fun Route.supplierRoute(database, userDatabase, auditLog)`. Copy
   `CustomerRoute.kt`: reads under `authenticate("restricted")`, writes under `authenticate { }`, a `call.audit(...)`
   on every write with an entity type like `"supplier"`, and `noteRoute(database, userDatabase, auditLog, "supplier")`
   at the end. Mount it in `Routing.kt`:

   ```kotlin
   route("/api/data/supplier") {
       supplierRoute(localDatabase.supplierDatabase, localDatabase.userDatabase, localDatabase.auditLogDatabase)
   }
   ```

   Keep the shared paths (`/add`, `/fetch`, `/get`, `/update`, `/remove/{identifier}`, `/search`) exactly as they
   are: the client's `RemoteRepository` calls them by name.

## Client

8. **Repository**: `components/network/database/SupplierRemoteRepository.kt`:

   ```kotlin
   class SupplierRemoteRepository(client: HttpClient) : RemoteRepository<Supplier>(
       tag = "SupplierRemoteRepository",
       client = client,
       route = "api/data/supplier",
       types = itemTypes<Supplier>()
   )
   ```

   It gets every shared call from the base class.
9. **Register it in `RemoteHandler`**: `val supplierRepo = SupplierRemoteRepository(httpClient)`, and set
   `supplierRepo.serverUrl = url` in `setUrl()` with the others. **That list is by hand**: a repository left out
   keeps a blank URL and quietly does nothing.
10. **Cache and seed** in `components/data/supplier/`: `SupplierCache` (copy `CustomerCache`) and `SupplierSeed`
    extending `DataSeed`, with each field, a `change...()` per field, a `supplier` getter, `setSupplier(...)` ending
    in `markClean()`, `clear()`, and `fields()` listing every editable field. See
    [Add a field](add-a-field.md#5-the-client-seed).
11. **Records handler**: `ui/screens/records/components/supplier/SupplierRecords.kt`, copied from
    `CustomerRecords`: the cache, an `OffsetPager` over `supplierRepo`, the seed, `RecordSelection`, and `select`,
    `save`, `remove`, `search` and `clear`. Keep the `canEdit()` checks in `save` and `remove`.
12. **Wire it into `MainViewModel`**:
    - `val suppliers = SupplierRecords(remoteHandler, viewModelScope, snackBarHost, { session.canEditNow() }, pagerConfig, cacheSize, searchLimit)`,
    - `suppliers.pager.refresh()` in `refreshPagers()`,
    - `suppliers.pager.setPageSize(size)` in `changePageSize()`,
    - `suppliers.clear()` in the session's `onLoggedOut`, so the next person doesn't see them.

## Screen

13. **Panel** in `ui/screens/records/components/supplier/`: `SupplierPanel` as a `RecordsLayout` with a
    `TmPanelTopBar`, a `SupplierListPane` (`RecordsList` with its `COLUMNS`), and a `SupplierInfoPane`
    (`RecordDetail` with `DetailSection`s), plus a `SupplierDialog` that edits the seed. Copy the customer ones.
14. **A tab** on the records screen: add "Suppliers" to the `TmTabs` list and a branch in the `when` in
    `RecordsScreen`, owning the dialog's visibility there like the others. Then:
    - bump `SettingsHandler.MAIN_TAB_COUNT`, and add the name to `tabNames` in `SettingsScreen`, so the Start tab
      setting can pick it,
    - add `SUPPLIER` to the `DataType` enum and a branch in the delete dialog's `when`.

    Or give it its own screen instead; see [Adding a screen](../ui/screens/navigation.md#adding-a-screen).

## Tests and docs

15. **Server test**: add, get, fetch, search, update, remove, a note round trip, and that a Restricted user can read
    but not write. `RecordsAccessTest` shows the client side of the last one.
16. **Run `python3 scripts/check_api_docs.py`**. It will list the new endpoints; add a `docs/api/supplier.md` page
    for them (copy `customer.md`) and a row in the API overview, and put the note endpoints on
    [`notes.md`](../api/notes.md).
17. Add it to `ScreenRenderTest.records()` and look at the render.
18. Add the model to [Domain models](../shared/domain-models.md).

## Checklist

- [ ] Model and error enum in `shared`
- [ ] Table, note join table, entity; listed in `LocalDatabase.tables`
- [ ] Database class; instance on `LocalDatabase`
- [ ] Note join table in `markUnreferencedDeleted` **and** `removeDetachedNotes`
- [ ] Route file, mounted in `Routing.kt`, with `noteRoute`
- [ ] Repository; registered in `RemoteHandler` **and** its `setUrl()`
- [ ] Cache, seed with `fields()`
- [ ] Records handler; in `MainViewModel`'s refresh, page size and logout
- [ ] Panel, panes, dialog; a tab (and `MAIN_TAB_COUNT`, `tabNames`, `DataType`) or its own screen
- [ ] Tests, `check_api_docs.py`, API page, render

## See also

- [Entity layers](../architecture/entity-layers.md)
- [Database layer](../server/database.md)
- [Records: seeds, caches and pagers](../client/records.md)
- [Records screen](../ui/screens/records.md)
