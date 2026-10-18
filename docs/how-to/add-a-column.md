# Add a database column

A new column on a table that shops already have rows in. New databases get every column straight away; the work is
making sure **existing** databases get it too, without losing anything. [Migrations](../server/migrations.md)
explains why each step is there. To add a whole field to a record, [Add a field](add-a-field.md) covers the other
layers.

The example adds a shelf `location` to inventory items.

## Steps

1. **Declare it with a default, or nullable.** Existing rows need a value:

   ```kotlin
   object InventoryTable : UuidTable("inventory") {
       ...
       val location = text("location").default("")
   }
   ```

   Use `.nullable()` when "not set" means something different from an empty value, like `promisedAt`.
2. **Don't use a reserved word** as the column's name. H2 fails on names like `condition`, `order`, `user`,
   `value` or `key`. Keep the Kotlin name and change the column's: `text("shelf_location")`.
3. **Make sure old databases get it.** `initDatabase()` only adds missing columns to tables in `migratedTables`:
   - if the table is already listed (`TicketTable`, `TokenTable`, `InventoryTable`, `StationTable`, `ImageTable`),
     there's nothing more to do,
   - otherwise add the table to `migratedTables` in `LocalDatabase`,
   - for the `user` table, add the column to `addUserColumns()` with its SQL default instead, since H2 can't list
     that table's columns:

     ```kotlin
     UserTable.location to "DEFAULT '' NOT NULL",
     ```
4. **Map it** in the entity (`var location by InventoryTable.location`) and wherever the database class reads and
   writes it.
5. **Fill it for old rows, if the default isn't right.** Write a data fix: a method on the database class that's
   safe to run on every start (it finds nothing to do the second time), called at the end of `initDatabase()`
   after the others. List it in the [data fixes table](../server/migrations.md#data-fixes).

## Try it on an old database

The migration only matters for data folders made before the change, and the tests start empty, so check it by hand:

1. Before making the change, start a server on a scratch folder and add a few records:

   ```bash
   PORT=8098 DATA_PATH=/tmp/tm-old ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
   ```

   Seeding test data from the admin screen's Server tab is the quickest way to fill it.
2. Stop it, make the change, and start it again on the same folder.
3. Check the server starts without errors, the old records still load, and new ones save the column.

A copy of a real shop's data folder (from a backup) is an even better test, if you have one.

## Changing a column instead

Don't rename, retype or drop a column in place: Exposed won't do it, and doing it by hand risks shops' data. Add a
new column, keep the old one under a `legacy` name in Kotlin, and move the values over in a data fix, the way
inventory prices moved from text to cents. See
[Changing or replacing a column](../server/migrations.md#changing-or-replacing-a-column).

## An index

An index declared on the table is only made when the table is first created. For an existing table, add a
`CREATE INDEX IF NOT EXISTS` to `initDatabase()`, with the index name kept as a constant on the table. See
[Adding an index](../server/migrations.md#adding-an-index).

## Checklist

- [ ] Default or nullable
- [ ] Not a reserved word
- [ ] Table in `migratedTables`, or the column in `addUserColumns()` for users
- [ ] Mapped in the entity and database class
- [ ] A data fix if old rows need a real value
- [ ] Started a server on a data folder from before the change

## See also

- [Migrations](../server/migrations.md)
- [Database layer](../server/database.md)
- [Add a field](add-a-field.md)
