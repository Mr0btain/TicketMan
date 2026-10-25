# Ticket and invoice numbers

Tickets and invoices get short numbers that people can read out over the phone: `26-00000042` and
`INV-26-00000042`. The server hands them out, counting from 1 each year. This page covers how they're assigned
without two counters ever getting the same one.

## The format

| | Model | Prints as | Stored as |
|-|-------|-----------|-----------|
| Ticket | `TicketNumber(year, sequence, prefix)` | `26-00000042`, `DT-26-00000042` | `number_prefix`, `number_year`, `number_sequence` on `tickets` |
| Invoice | `InvoiceNumber(year, sequence, prefix)` | `INV-26-00000042`, `INV-DT-26-00000042` | the same on `invoices` |
| Purchase order | `PurchaseNumber(year, sequence, prefix)` | `PO-26-00000042`, `PO-DT-26-00000042` | the same on `purchase_orders` |

The prefix is the code of the [location](../api/location.md) it was made at, blank at the main location, and each
location counts on its own: the unique index is on (prefix, year, sequence). The year is stored in full (`2026`) and
printed as two digits. The sequence is padded to eight digits. `TicketNumber.parse()` reads the full form, the short
`26-42`, or either with a code (`DT-26-42`). There's no parser for invoice numbers; invoices
are found through their ticket.

A ticket and its invoice usually end up with the same sequence, but nothing makes them: an invoice is only made
when one is needed (a deposit at check-in, or opening the ticket's invoice), so the counts drift apart.

## Handing out a number

```kotlin
// TicketDatabase
override fun add(data: Ticket): Boolean =
    synchronized(NUMBER_LOCK) {
        dbWrite(ADD_TAG) {
            insertTicket(data)   // reads max(number_sequence) for this year, inserts with max + 1
            true
        }
    }
```

Three things keep numbers unique:

1. **A lock.** `TicketDatabase` and `InvoiceDatabase` each have a `NUMBER_LOCK`. Picking the next number and
   inserting the row happen while holding it, and the lock wraps the whole transaction, so one number is committed
   before the next one is worked out.
2. **A unique index** on `(number_year, number_sequence)` in both tables, so even if something got past the lock
   the database would refuse the second row. For tickets the index is created in `initDatabase()` (so older
   databases get it too); for invoices it's declared on the table.
3. **The server ignores numbers from clients.** `add` and `update` never read `number` from the request, and
   check-in clears it before saving.

The year comes from a `currentYear` function passed to the database class (the server's time zone by default).
Tests swap it to check that numbers start over in a new year.

### Inside an outer transaction

The lock only works if it's held until the row is committed. Until then nobody else can see the new number, so
they'd work out the same one. `add()` commits inside the lock, but check-in calls it inside its own
`transaction { }`, which commits later. So check-in holds both locks for the whole transaction:

```kotlin
// CheckInService
tickets.whileNumbering {
    invoices.whileNumbering {
        transaction { /* add the ticket, link it, take the deposit (which makes the invoice), save the signature */ }
    }
}
```

The locks are reentrant, so `add()` and `getOrCreate()` taking them again inside is fine. Always take the ticket
lock first, then the invoice one, so two callers can't deadlock. Anything new that adds a ticket or an invoice inside
an outer transaction needs the same wrapping. `CheckInTest.checkInsAtTheSameMomentGetTheirOwnNumbers` checks it.

## Older tickets

Tickets from before numbers existed have empty number columns. On start, `backfillNumbers()` gives each one a
number in the year its id says it was made, oldest first, under the same lock.

## Looking one up

- `GET /api/data/ticket/number/{number}` finds a ticket by its full number (`TicketDatabase.getByNumber`).
  `TicketRemoteRepository.getByNumber` calls it, though no screen does yet.
- Ticket search (`searchTickets`, used by the records screen and the counter) matches numbers: `26-42` matches
  exactly, and plain digits like `42` match that sequence in any year.

## Gotchas

- **An outer transaction needs `whileNumbering`**, see [above](#inside-an-outer-transaction). Without it two
  check-ins at the same moment pick the same number, and the unique index fails the second with a `500`.
- **`Ticket.number` is null until saved.** Use `Ticket.label`, which falls back to the start of the id, anywhere a
  ticket that might be new is shown.
- **The year boundary follows the server's clock and time zone**, not the client's.

## See also

- [Database layer](database.md)
- [Migrations](migrations.md)
