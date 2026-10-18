# Notes API

From [`NoteRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/NoteRoute.kt). Every
record type mounts the same four note routes under its own prefix with `noteRoute(...)`, so customers, tickets,
inventory and users all handle notes the same way. Only who can use them differs: staff notes on users are admin
only. How notes are stored and checked is in [Database layer](../server/database.md).

## How they work

| Route | Body | Answers |
|-------|------|---------|
| `POST <prefix>/get/notes/{record id}` | The record's id again (the path is what's used) | **`200`** a list of `Note`, each with its `images`. **`404`** no such record |
| `POST <prefix>/add/note/{record id}` | `Note` | **`201`** the saved `Note`. **`400`** not a UUID. **`404`** no such record |
| `POST <prefix>/update/note/{note id}` | `Note`, whose `identifier` matches the path | **`202`** saved. **`400`** the ids don't match. **`404`** no such note on this record type |
| `DELETE <prefix>/remove/note/{note id}` | - | **`202`** deleted. **`404`** no such note on this record type |

- **Adding** signs the note with the caller's username when `author` is blank.
- **Update and remove take the note's id**, not the record's, so the server checks the note is on this record type
  (`noteLinkColumn`) before touching it. A ticket's note can't be changed through the customer routes.
- **Photos** go in the note's `images`, uploaded first with [`POST /api/image/upload`](image.md). Saving the note
  links the ones that are pending or already on it; photos it had that aren't listed any more are marked deleted.
  Removing a note marks all its photos deleted.
- Every add, update and remove is written to the [audit log](../server/audit-log.md) as `<type> note`.

## Customers

Notes on a customer. See the [Customers API](customer.md).

### `POST /api/data/customer/get/notes/{identifier}`

**Auth:** restricted

The record's notes.

### `POST /api/data/customer/add/note/{identifier}`

**Auth:** normal

Adds a note to the record.

### `POST /api/data/customer/update/note/{identifier}`

**Auth:** normal

Replaces a note's text and photos. The id is the note's.

### `DELETE /api/data/customer/remove/note/{identifier}`

**Auth:** normal

Deletes a note. The id is the note's.

## Tickets

Notes on a ticket. See the [Tickets API](ticket.md).

### `POST /api/data/ticket/get/notes/{identifier}`

**Auth:** restricted

The record's notes.

### `POST /api/data/ticket/add/note/{identifier}`

**Auth:** normal

Adds a note to the record.

### `POST /api/data/ticket/update/note/{identifier}`

**Auth:** normal

Replaces a note's text and photos. The id is the note's.

### `DELETE /api/data/ticket/remove/note/{identifier}`

**Auth:** normal

Deletes a note. The id is the note's.

## Inventory

Notes on a item. See the [Inventory API](inventory.md).

### `POST /api/data/inventory/get/notes/{identifier}`

**Auth:** restricted

The record's notes.

### `POST /api/data/inventory/add/note/{identifier}`

**Auth:** normal

Adds a note to the record.

### `POST /api/data/inventory/update/note/{identifier}`

**Auth:** normal

Replaces a note's text and photos. The id is the note's.

### `DELETE /api/data/inventory/remove/note/{identifier}`

**Auth:** normal

Deletes a note. The id is the note's.

## Users

Notes on a user (staff notes, like training or when they work). See the [Users API](user.md).

### `POST /api/user/get/notes/{identifier}`

**Auth:** admin

The record's notes.

### `POST /api/user/add/note/{identifier}`

**Auth:** admin

Adds a note to the record.

### `POST /api/user/update/note/{identifier}`

**Auth:** admin

Replaces a note's text and photos. The id is the note's.

### `DELETE /api/user/remove/note/{identifier}`

**Auth:** admin

Deletes a note. The id is the note's.

## Gotchas

- **Reading notes is a `POST`** with the id as the body too, to match the client. The path's id is the one used.
- **Records save notes when they're saved.** The client's dialogs only call these when the record itself is saved
  (`syncNotes`), except staff notes, saved straight away. See [Records](../client/records.md#saving).

## See also

- [Database layer](../server/database.md)
- [Photos API](image.md)
