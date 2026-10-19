# Audit log

Every change made through the API, and every login, leaves a line in the audit log: who did it, what kind of action,
on what, and a short readable note. Admins read it on the admin screen's Audit tab. This page covers what gets
logged and how to log something new.

## An entry

`AuditEntry` (in `shared`), stored in `AuditLogTable`:

| Field | Holds |
|-------|-------|
| `actorIdentifier` | The user, or null when there isn't one (a failed login, an account request) |
| `actorName` | Their username at the time, so the entry still reads right after a rename or delete |
| `action` | An `AuditAction` |
| `entityType` | What was changed: `"ticket"`, `"customer"`, `"inventory"`, `"user"`, `"invoice"`, `"payment"`, `"image"`, `"station"`, `"shop"`, `"database"`, `"restore"`, `"account request"`, or the type a note was on |
| `entityIdentifier` | The id of the record, as text, when there is one |
| `details` | A short note for people, like `Ticket 26-00000042: status DONE` or `$40.00 cash on INV-26-00000042` |
| `time` | Epoch millis |

| `AuditAction` | Used for |
|---------------|----------|
| `CREATE`, `UPDATE`, `DELETE` | Changes to records, notes, invoices, payments, users, stations |
| `LOGIN`, `LOGIN_FAILED` | Password and PIN logins, wrong passwords, wrong or locked PINs, bad setup codes |
| `PASSWORD_RESET` | An admin setting someone's password |
| `SESSION_REVOKE` | An admin ending sessions |
| `SETTINGS_UPDATE` | The shop profile, kiosk settings |
| `BACKUP` | Making a backup, staging or cancelling a restore |
| `SEED` | Test data |

Reads aren't logged.

## Writing an entry

From a route, use the helper in `routing/Helpers.kt`, which takes the caller from the token:

```kotlin
call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, "Ticket ${existing.label}: status ${received.status.status}")
```

From a service, or when there's no logged in caller, call the database directly:

```kotlin
auditLog.log(actor, AuditAction.CREATE, "ticket", saved.identifier.toString(), "Checked in ${saved.label}")
auditLog.log(null, AuditAction.LOGIN_FAILED, "user", details = "Failed login", actorName = loginRequest.username)
```

`log()` looks up the actor's username unless `actorName` is given.

Conventions:

- Log **after** the change has worked, not before, so the log doesn't claim things that didn't happen.
- In a multi step change wrapped in `transaction { }` (check-in, pickup, voiding), log **inside** it, as its last
  step. `log()` writes through `dbWrite`, so it joins that transaction: the entry is only kept if the change is,
  and an entry that can't be written undoes the change.
- Route files keep their entity type in a `val ENTITY_TYPE = "ticket"` and pass it to `call.audit` and `noteRoute`.
- Write `details` for a person reading the Audit tab. Use the record's label (`ticket.label`, `invoice.label`) and
  `Money.format` rather than raw ids and cents.
- Never put passwords, PINs, codes, tokens or secrets in `details`.

## Reading it

`POST /api/admin/audit/fetch` with a `PagedRequest`, admin only, gives entries newest first.

## Trimming

The hourly sweep deletes entries older than `AUDIT_LOG_DAYS` (730, two years, by default) with
`AuditLogDatabase.removeOlderThan`. `0` keeps them forever. See [Configuration](../configuration.md#housekeeping).

## Gotchas

- **Outside a transaction, a failed write is only logged to the console.** The change the entry was for has
  already happened and stays. Only entries written inside a `transaction { }` take the change down with them.

## See also

- [Authentication and sessions](auth.md)
- [Database layer](database.md)
