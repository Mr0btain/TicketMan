# Migrations

There's no migration framework. Every time the server starts, `LocalDatabase.initDatabase()` brings whatever
database it finds up to date: it creates missing tables, adds missing columns, adds indexes, then runs a few data
fixes. Each step is safe to run again on a database that's already current. This page covers how to change the
schema without breaking shops that already have data.

## What runs on start

[`initDatabase()`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/database/LocalDatabase.kt):

```kotlin
transaction {
    SchemaUtils.create(*tables)                                                  // 1. new tables
    SchemaUtils.addMissingColumnsStatements(*migratedTables).forEach { exec(it) } // 2. new columns
    addUserColumns()                                                             // 3. new user columns
    exec("CREATE UNIQUE INDEX IF NOT EXISTS ...")                                // 4. indexes
}

ticketDatabase.backfillNumbers()       // 5. data fixes
userDatabase.migrateDeactivatedUsers()
tokenDatabase.clearLegacySessions()
tokenDatabase.backfillExpiry()
inventoryDatabase.migrateLegacyPrices()
runOnce("legacy-names") { ... }         // 6. data fixes that only run once
removeDetachedNotes()
```

Steps 1 to 4 run in one transaction. The data fixes run after, each in its own.

## Adding a table

1. Make the table object under `tables/<area>/`.
2. Add it to `LocalDatabase.tables`, **after** any table it references. `SchemaUtils.create` makes them in that
   order and a foreign key can't point at a table that doesn't exist yet.

That's all. `SchemaUtils.create` skips tables that already exist.

## Adding a column

A new column has to work on databases that already have rows, so it **must have a default or be nullable**:

```kotlin
val promisedAt = long("promised_at").nullable()
val problem = text("problem").default("")
```

Then make sure it gets added to existing databases:

- **Most tables:** add the table to `migratedTables` if it isn't there already. Only tables listed there are checked
  for missing columns. At the moment that's `TicketTable`, `TokenTable`, `InventoryTable`, `StationTable` and
  `ImageTable`.
- **The `user` table:** add the column to `addUserColumns()` instead, with its SQL default:

  ```kotlin
  UserTable.pinLocked to "DEFAULT FALSE NOT NULL",
  ```

  H2 can't list the columns of a table called `user` (it's a reserved word), which `addMissingColumnsStatements`
  needs, so these are added by hand with `ADD COLUMN IF NOT EXISTS`.

`SchemaUtils.create` makes new databases with every column already, so both paths only matter for old ones.

### Column names

Avoid SQL reserved words as column names. H2 fails on them. That's why `Ticket.condition` is stored in a column
called `device_condition`:

```kotlin
val condition = text("device_condition").default("")
```

The Kotlin property can keep the natural name.

## Adding an index

`SchemaUtils.create` adds indexes declared on a table only when it creates the table. For an index on an existing
table, add a `CREATE INDEX IF NOT EXISTS` (or `CREATE UNIQUE INDEX IF NOT EXISTS`) to `initDatabase()`, like the
ticket number index:

```kotlin
exec(
    "CREATE UNIQUE INDEX IF NOT EXISTS ${TicketTable.NUMBER_INDEX} " +
        "ON ${TicketTable.tableName} (${TicketTable.numberYear.name}, ${TicketTable.numberSequence.name})"
)
```

Keep the index name as a constant on the table so the two can't drift apart.

## Changing or replacing a column

Exposed won't rename, retype or drop columns for you, and doing it by hand risks shops' data. Instead:

1. Add a new column (with a default) for the new shape.
2. Keep the old column, renamed in Kotlin to `legacy<Name>` but with the same column name in the database.
3. Add a data fix that copies old values into the new column and blanks the old one.

`InventoryTable` shows the pattern. Prices used to be text; now they're cents:

```kotlin
val legacyCost = varchar("cost", 100).default("")    // old column, same name in the database
val costCents = long("cost_cents").default(0)         // new column
```

and `InventoryDatabase.migrateLegacyPrices()` moves anything left in `cost` into `cost_cents` with `Money.parse`.

## Data fixes

Data fixes are plain database methods called at the end of `initDatabase()`. They must be idempotent: run on an
already fixed database, they should find nothing to do.

| Fix | What it does |
|-----|--------------|
| `TicketDatabase.backfillNumbers()` | Gives tickets from before ticket numbers a number, in creation order, in the year they were made |
| `UserDatabase.migrateDeactivatedUsers()` | Users deactivated the old way (`AccessLevel.NONE`) become `DEACTIVATED` with `RESTRICTED` access |
| `TokenDatabase.clearLegacySessions()` | Drops sessions from before sessions had ids. Those users just log in again |
| `TokenDatabase.backfillExpiry()` | Fills in `expiresAt` for sessions saved before it was kept, from their refresh token |
| `InventoryDatabase.migrateLegacyPrices()` | Text prices into cents, as above |
| `LocalDatabase.removeDetachedNotes()` | Deletes notes no record links to, left by deleting customers, tickets and items before `remove` took their notes. The next sweep marks their photos deleted |

Add a new one by writing the method on the right database class and calling it after the others.

### Fixes that run once

Some old data can't be told apart from data typed later, so fixing it on every start would change what people
typed. Those go in `runOnce(name) { ... }`, which runs the block only if `name` isn't in the `data_fix` table
(`DataFixTable`), and adds it in the same transaction, so a fix that fails runs again next start. The database
methods it calls use `dbWrite` as usual; inside the outer transaction their throws roll the whole fix back. A new
database runs them too, on nothing.

| Name | What it does |
|------|--------------|
| `legacy-names` | `CustomerDatabase.migrateLegacyNames()` and `UserDatabase.migrateLegacyNames()`: names older versions stored as `first:last` or lowercased (`isLegacyName`) are rewritten as they were shown, `jane:doe` as `Jane Doe` (`legacyName` in `LegacyName.kt`) |

## Checklist

- [ ] New column has a default or is nullable
- [ ] Its table is in `migratedTables`, or it's in `addUserColumns()` for `user`
- [ ] Column name isn't a reserved word
- [ ] New table is in `tables`, after what it references
- [ ] Indexes on existing tables are created with `IF NOT EXISTS` in `initDatabase()`
- [ ] Data fixes can run twice safely, or go in `runOnce`
- [ ] There's a test that starts on an old shaped database, if the change is risky. `RestoreTest` shows how to start
      the server twice on one data folder

## See also

- [Database layer](database.md)
- [Numbering](numbering.md)
- [Add a database column](../how-to/add-a-column.md)
