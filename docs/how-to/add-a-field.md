# Add a field to a record

A field on a customer, ticket, inventory item or user is mirrored on every layer, so adding one touches about eight
files. This guide adds a `company` to `Customer` as the example; the same steps work for the other record types.
[Entity layers](../architecture/entity-layers.md) explains the layers themselves.

Before you start, decide whether the field needs its own column. A field inside a value class that's already
stored as JSON (like `Device` on a ticket, or `Address` on a customer) needs no database change at all: add it to
the class with a default and skip to step 5.

## 1. The shared model

Add it to the `@Serializable` class in `shared/.../data/customer/Customer.kt`, **with a default**:

```kotlin
data class Customer(
    override val identifier: Uuid = Uuid.generateV7(),
    val name: String = "",
    val address: Address = Address(),
    val email: String = "",
    val phone: String = "",
    val company: String = ""
): MainItem()
```

The default lets an older client's JSON, which won't have the field, still be read. The server refuses fields it
doesn't know, so **update the server before the clients**.

## 2. The column

Add a column to `CustomerTable` in `server/.../database/tables/customer/`, with a default or nullable so it can be
added to databases that already have rows:

```kotlin
val company = text("company").default("")
```

Then make sure existing databases get it. Only tables in `LocalDatabase.migratedTables` are checked for missing
columns, and `CustomerTable` isn't one of them yet, so add it:

```kotlin
private val migratedTables = arrayOf(
    TicketTable,
    TokenTable,
    InventoryTable,
    StationTable,
    ImageTable,
    CustomerTable
)
```

Columns on the `user` table go in `addUserColumns()` instead. Avoid SQL reserved words as column names. See
[Add a column](add-a-column.md) and [Migrations](../server/migrations.md).

## 3. The entity and database class

Map it in `CustomerEntity` (the property and `toCustomer()`):

```kotlin
var company by CustomerTable.company

fun toCustomer(): Customer = Customer(
    identifier = id.value,
    ...
    company = company
)
```

and write it in `CustomerDatabase`, in both `insertCustomer` and `update`:

```kotlin
CustomerEntity.new(data.identifier) {
    ...
    company = data.company.trim()
}
```

If only the server should set the field (like a ticket's `number`), leave it out of `update` so a client can't
overwrite it.

## 4. Search, if it should be searchable

Add it to `CustomerDatabase.searchCustomers` (and the counter search uses that too). Skip this otherwise.

## 5. The client seed

`CustomerSeed` holds the record being viewed or edited. Give it the field, a setter, and add it everywhere the seed
copies fields:

```kotlin
var company by mutableStateOf("")
    private set

fun changeCompany(newCompany: String) {
    company = newCompany
}
```

- `customer`: pass `company` into the `Customer` it builds,
- `setCustomer(...)`: `company = customer.company`, before `markClean()`,
- `clear()`: `company = ""`,
- `fields()`: `ChangedField.COMPANY to company`, adding `COMPANY` to the `ChangedField` enum.

**`fields()` is the one that's easy to miss.** Without it, editing the field doesn't make the seed dirty, so the
dialog's Save stays off and the change is never sent.

## 6. The screen

- **Show it** in `CustomerInfoPane`, as a `TmKeyValueRow` in the right `DetailSection`.
- **Edit it** in `CustomerDialog`, with a kit field calling the seed's setter:

  ```kotlin
  TmTextField(modifier = Modifier.fillMaxWidth(), value = seed.company, onValueChange = { seed.changeCompany(it) }, label = "Company")
  ```

- **A list column**, if it earns one: a `TmColumn` in the list pane's `COLUMNS` and a cell for it.
- Anywhere else that shows customers and should have it: the counter's `CustomerCard` and the kit's `TmCustomerForm` (new customers at the counter, at check-in and from the ticket dialog), and the
  print templates (`PrintTemplates.customerFields`).

## 7. Tests

- **Server**: a test that adds a customer with the field, reads it back over `/get`, updates it and reads it again.
- **Seed**: in `RecordSeedTest`, that changing it makes the seed dirty and changing it back makes it clean.
- Run `ScreenRenderTest` and look at `records-customers-*.png`.

## 8. Docs

Add the field to the model's row in [Domain models](../shared/domain-models.md) if it's listed there.

## Checklist

- [ ] Shared model, with a default
- [ ] Column with a default or nullable; table in `migratedTables` (or `addUserColumns()` for users)
- [ ] Entity property and `to<Model>()`
- [ ] Database `insert` and `update` (unless the server owns it)
- [ ] Seed: property, setter, model getter, `set<Model>`, `clear()`, `fields()` and a `ChangedField`
- [ ] Info pane and dialog
- [ ] Tests, and a look at the render

## See also

- [Entity layers](../architecture/entity-layers.md)
- [Records: seeds, caches and pagers](../client/records.md)
- [Records screen](../ui/screens/records.md#adding-a-field-to-a-record)
