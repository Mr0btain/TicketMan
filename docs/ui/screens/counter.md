# Counter screen

The counter (`Destination.COUNTER`) is the front desk: one search box for tickets and customers, the queue on the
side, and the check-in and pickup flows. It's built for a touch screen and a keyboard, with large controls and
function key shortcuts. Touch this when changing how a device is checked in or handed back. What the server does
with a check-in or a pickup is in [Counter: check-in, invoices and pickup](../../server/counter.md).

![The counter with a ticket open and the queue on the right](../../images/counter.png)

## How it works

[`CounterScreen`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/counter/CounterScreen.kt)
is a top bar (search, Check in, New customer, Price check), a main area, and the `QueuePanel` (360 dp) on the
right, laid out by `CounterLayout`. Under 900 dp wide the buttons become icons and the queue moves into a dialog
opened from its own button (`QueueDialog`); the main area stays put when the window crosses that width, so a half
done check-in isn't lost. The ticket card, pickup and check-in wrap and stack their panels to fit a phone. What
the main area shows is `CounterHandler.view`, one `CounterView` at a time:

```mermaid
stateDiagram-v2
    [*] --> HOME
    HOME --> TICKET: pick a ticket
    HOME --> CUSTOMER: pick a customer
    HOME --> PRICE_CHECK: F4
    HOME --> CHECK_IN: F3
    HOME --> NEW_CUSTOMER: New customer
    CUSTOMER --> TICKET
    CUSTOMER --> CHECK_IN: Check in a device
    CHECK_IN --> CHECKED_IN: submitted
    CHECKED_IN --> TICKET
    CHECKED_IN --> CHECK_IN: check in another
    NEW_CUSTOMER --> CUSTOMER: saved
    TICKET --> PICKUP: F5 / Pick up
    PICKUP --> TICKET: back
    PICKUP --> PICKED_UP: finished
```

Every view can go back to `HOME` (Escape, or its back button), and typing in the search bar always goes back to
`HOME` to show the results. All of it lives in
[`CounterHandler`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/counter/components/CounterHandler.kt)
on `MainViewModel`, so leaving the counter and coming back keeps the open ticket. `CounterHandler` holds the
search, queue, shop profile, ticket and customer cards and price check itself, and a part for each bigger flow:
`counter.checkIn` (`CounterCheckIn`, the wizard and new customer screen), `counter.pickup` (`CounterPickup`),
`counter.invoices` (`CounterInvoices`, the invoice list the admin screen shows) and `counter.printer`
(`CounterPrinter`). They share a `CounterSupport` (scope, snackbar, `canEdit`, switching views, reloading the
queue) and load tickets through `RemoteHandler.loadTicketDetail` (`TicketDetail.kt`).

### Keyboard

`CounterScreen` catches keys with `onPreviewKeyEvent`, so they work wherever focus is:

| Key | Does |
|-----|------|
| F2 | Back to home and focus the search |
| F3 | Start a check-in |
| F4 | Price check |
| F5 | Pick up the open ticket (only on the ticket view) |
| Esc | Back to home, or clear the search if already there |

The shortcuts are listed on the home view before anything is searched, and in the button labels. The search field
is focused when the counter opens.

### Search and queue

- **Search** waits 300 ms after the last key, then asks `/api/counter/search` for up to 20 tickets and customers.
  `SearchResults` lists both.
- **Queue** loads when the counter opens, after a check-in or pickup, and on the Settings interval (`CheckForUpdates`)
  while the counter shows home, a ticket or a customer; an open ticket card reloads with it (`reloadTicket(quiet =
  true)`). Not during a check-in or pickup. Its
  four sections are Ready for pickup, Overdue, Due today and Just checked in, each in its status tone.

### Ticket

`TicketCard` shows a ticket with its customer, notes, signature, invoice and status history. Staff who can edit get
a quick status switch (`TmSegmented` over `TicketStatusType.selectable`, so never Picked up), Print, and Pick up.
A text field under the notes adds one, saved straight away (`addTicketNote`), text only.

### Check-in

[`CheckInWizard`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/counter/CheckInWizard.kt)
walks through steps with a `TmStepper`:

| Step | Asks for | Can't go on until |
|------|----------|-------------------|
| Customer | Search, or add one inline | A customer is picked |
| Device | Make, model, serial, condition, what was left with it | Make or model is filled in |
| Repair | Problem, priority, estimate, promised day (today to a week), deposit and how it was paid, technician | The problem is described |
| Sign | The shop's repair terms and a signature pad, with the signer's name | Signed and named. Only shown when the shop has repair terms |
| Review | Everything, to check | - |

![Check-in on the device step](../../images/check-in.png)

Everything typed is kept in one `CheckInForm`, so going back a step loses nothing. The stepper only opens a step
when every step before it is filled in. The form makes the ticket's id up front, so the **repeat check** can leave
it out: half a second after the device stops changing, it asks the server for this customer's earlier tickets for
the same device and warns if it's been in recently.

Check in sends one `CheckInRequest` (ticket, customer, technician, deposit, signature) to
`POST /api/counter/check-in`, which saves it all together. The result shows on `CheckedInView`, with the new number
in `numericHero` to read out to the customer and a button to print the claim slip.

### Pickup

[`PickupScreen`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/counter/PickupScreen.kt)
opens with the ticket's invoice, made if it doesn't have one yet.

![Pickup: invoice lines on the left, payment on the right](../../images/pickup.png)

- **Lines** are edited in place: labor, parts (searched from inventory) and other charges, each with a quantity,
  price and whether it's taxed, plus a discount. Quantities are kept as text while typing so "1." isn't thrown
  away.
- **Totals** are worked out on the screen with the shared `InvoiceMath`, so they match what the server will
  charge. See [Money](../../shared/money.md).
- **Payments** already taken (like the deposit) are listed and can be removed. New ones are added with an amount
  and method; leaving the amount at 0.00 takes whatever is still owed. They're only sent when the pickup is
  finished.
- **Balance due** turns into **Change due** once enough has been taken.
- **Finish pickup** is on once the lines are valid and nothing is owed. It saves any changed lines first, then posts
  `/api/counter/pickup` with the new payments. The server pays the invoice, takes the parts from stock and closes
  the ticket in one go. `PickedUpView` then shows what was paid and the change, with a button to print the receipt.

If the pickup is refused (short on stock, someone else picked it up), the screen reloads the invoice and shows the
server's message.

### Printing

**Save PDF** sits next to Print on a ticket with an invoice and after pickup: `CounterPrinter.savePdf` has the
server make the invoice as a PDF (`GET /api/invoice/{id}/pdf`) and saves it, to email to the customer.

`PrintButton` asks which slip (claim slip, device tag, receipt) and which paper, starting on the shop's default
paper, then calls `counter.printer.print`, which loads the ticket fresh so the printout matches the server. See
[Printing](../../client/printing.md).

### View only

Restricted users can search, open tickets and customers, use price check and print. The Check in and New customer
buttons, the ticket's status switch and Pick up are hidden, a "View only" badge shows instead, and
the handler and its parts refuse those actions anyway.

## Where it lives

| What | Where |
|------|-------|
| Screen, shortcuts, view switching | `ui/screens/counter/CounterScreen.kt` |
| Top bar and queue placement, wide and narrow | `ui/screens/counter/CounterLayout.kt` |
| State and every action | `ui/screens/counter/components/CounterHandler.kt`, and its parts `CounterCheckIn.kt`, `CounterPickup.kt`, `CounterInvoices.kt`, `CounterPrinter.kt`, `CounterSupport.kt` |
| `TicketDetail`, `CustomerDetail` and loading a ticket's card | `ui/screens/counter/components/TicketDetail.kt` |
| Ticket and customer rows, `isOverdue`, `isClosed` | `ui/screens/counter/components/CounterRows.kt` |
| Each view | `TicketCard.kt`, `CustomerCard.kt`, `SearchResults.kt`, `QueuePanel.kt` (with `QueueDialog`), `PriceCheck.kt`, `CheckInWizard.kt`, `CheckedInView.kt`, `NewCustomerScreen.kt` (new customer, the kit's `TmCustomerForm` under a header), `PickupScreen.kt` (with `PickedUpView`) |
| Print button | `ui/screens/counter/PrintButton.kt` |
| Render tests | `ScreenRenderTest.counterTicket()`, `pickup()`, `checkIn()`, and `counterTicketNarrow()`, `pickupNarrow()`, `checkInNarrow()` at phone width |
| Handler tests | `composeApp/src/commonTest/.../ui/screens/counter/CounterHandlerTest.kt` |

## Gotchas

- **Keys go to the counter first.** `onPreviewKeyEvent` sees F-keys and Escape before any field does, and consumes
  them. A field on the counter can't use those keys for anything else.
- **Pickup edits are local until saved.** Lines are rebuilt from the invoice whenever the server sends new lines,
  but not when only the payments change, so removing a deposit doesn't throw away edits. Save invoice sends them
  without finishing.
- **The ticket card's notes save at once**, the records screen's when the record is saved. The counter's are text
  only; photos and editing are on the records screen.

## Known gaps

- On a phone the check-in stepper scrolls sideways rather than fitting, and the F-key hints stay in the search
  box's placeholder.

## See also

- [Counter: check-in, invoices and pickup](../../server/counter.md)
- [Kiosk](kiosk.md), which shows this screen full screen with a PIN lock
- [Printing](../../client/printing.md)
