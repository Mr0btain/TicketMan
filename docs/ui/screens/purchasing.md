# Purchasing screen

`Destination.PURCHASING` ([`PurchasingScreen`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/purchasing/PurchasingScreen.kt)),
in the nav for everyone; Restricted users can look but every button that changes something is hidden, and
`PurchasingHandler` refuses anyway. What the server does is in [Purchasing](../../server/purchasing.md).

Two tabs:

- **Orders**: the list (Waiting, Drafts or All, `OrderFilter`) beside the open order on wide windows, taking turns
  under 900 dp. `OrderDetail` edits a draft (supplier, lines added from an inventory search with a count and cost,
  a note) and places it; a placed one shows each line's received count, and **Receive** opens a dialog starting at
  everything still to come.
- **Suppliers**: `SuppliersPane`, the list with an add and edit dialog.

State lives in `viewModel.purchasing`
([`PurchasingHandler`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/purchasing/components/PurchasingHandler.kt)),
loaded at login. Other screens use it too:

- The counter's ticket card has a **Parts** panel (`PartsPanel`) listing what's on order for the ticket, loaded with
  the ticket (`TicketDetail.purchases`), and **Order a part**, which puts the part on its supplier's draft.
- The admin screen's Low stock tab has **Draft orders**.
- The inventory dialog picks the item's usual supplier.

The order list and suppliers reload on the Settings update interval (`CheckForUpdates`); an open order keeps
what's typed unless someone else changed it.

Render tests: `ScreenRenderTest.purchaseOrders()` draws a part received order, a draft and the suppliers, wide and
narrow.
