# Admin screen

The admin screen (`Destination.ADMIN`) is where an admin looks after the shop: numbers for the day, staff accounts,
logged in devices, counter stations, low stock, invoices, the shop profile, backups and the audit log. It's only in the nav
for admins. Touch this when adding something admins manage. Most tabs are thin screens over server features
documented in [the server pages](#see-also).

![The Overview tab](../../images/admin-overview.png)

## How it works

[`AdminScreen`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/admin/AdminScreen.kt)
checks `session.isAdmin`. Anyone else who gets here sees "Admin access required" (with a Log in button if they're
logged out); the server refuses their requests anyway.

For an admin it shows `TmTabs` and a **Refresh** button. Opening a tab calls `AdminHandler.refresh(tab)`, which
loads only what that tab shows, plus the accounts waiting on someone: the Users tab shows a badge with their number
from any tab. The Users tab also reloads the users list, and the Invoices tab loads its own list through the
counter. Refresh does the same for the open tab.

On the Settings interval, while no dialog is open and nothing is loading, `CheckForUpdates` calls
`AdminHandler.checkForUpdates(tab)`: the same reloads without the spinner, leaving out what would lose someone's place
(the audit log and invoices, which reload from the top, the end of day report, which may be on another day, the
reports, whose commission rates may be half edited, and the shop profile, which may be half edited). Refresh still does all of it.

All the state is in
[`AdminHandler`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/admin/components/AdminHandler.kt),
which holds the overview, low stock and audit log itself and one part per group of tabs: `admin.accounts`
(`AdminAccounts`, the Users tab), `admin.devices` (`AdminDevices`, Stations and Sessions) and `admin.server`
(`AdminServer`, the Server tab), plus `admin.messages` (`AdminMessages`) and `admin.reports` (`AdminReports`, the
Reports tab, whose ranges are the invoice export's `ExportRange`) and `admin.timesheets` (`AdminTimesheets`). The parts share an `AdminSupport`: every change goes through its `report(...)`,
which shows a snackbar and reloads the audit log. The users list itself comes from the shared `UserRecords` pager
and seed, like the record lists.

### Tabs

| Tab | Shows | Does | More |
|-----|-------|------|------|
| **Overview** | Stat tiles (open tickets, new this week, customers, low stock, users), the end of day report, tickets by status and priority, users by access, the oldest open ticket, recent tickets | Step the report a day back or forward (never past today) and print it. Clicking a ticket opens it on the records screen | `GET /api/admin/stats`, `/report/daily` |
| **Users** | Accounts waiting on someone (requests and invites), the user list with each user's avatar, the picked user's details, photo, whether they have an ID card, and staff notes | Invite, approve with an access level, reject, renew an invite's code; add, edit, reset password, deactivate or reactivate, log out everywhere, unlock or reset PIN, delete; add, change or take off their photo; **Print ID card** (makes the code first if needed) and **New card code** (after a confirm, the old card stops working). Staff notes and photos save straight away | [Users and accounts](../../server/users-and-accounts.md) |
| **Sessions** | Logged in devices grouped by user, with password or PIN, when they logged in, were last active and expire; **Blocked logins**: usernames and device addresses locked out for wrong passwords, until when, and a warning during a server wide surge | Log out one device (not this device's own session); let a blocked username or address try again | [Authentication and sessions](../../server/auth.md#sessions), [Wrong password limits](../../server/auth.md#wrong-password-limits) |
| **Stations** | Devices that asked to be counter stations | Approve, remove, turn kiosk mode on and set its idle timeout | [PINs and stations](../../server/pins-and-stations.md) |
| **Low stock** | Tracked items at or below a threshold (5 by default, saved on this device) | Change the threshold; click an item to open it on the records screen | |
| **Invoices** | Every invoice, newest first, with its status, total, paid (and refunded) and balance; one kept after its ticket was deleted says "Ticket 26-... deleted" under its number, a sale says "Counter sale" | Click one to open its ticket at the counter (not a kept one or a sale); save one as a PDF; **Refund** a paid one (`RefundDialog`: amount, how it goes back, a reason, and how many of each part come back to stock); void any that isn't void yet; delete a kept one, after a confirm saying its payments leave the books; load older ones; **Export CSV** for a month, a year or chosen days, as invoices for a spreadsheet, QuickBooks or Xero's import, or the payments (`InvoiceExportDialog`); under it, **Gift cards** (`GiftCardsPanel`) lists every card with what was loaded and what's left, with **Adjust** | `GET /api/invoice/fetch`, `/{id}/pdf`, `/{id}/refund`, `/export`, `/api/giftcard/fetch`, [Invoices API](../../api/invoice.md) |
| **Reports** | Pick this month, last month, this year, last year or chosen days: stat tiles (sales, net sales, gross profit with the parts cost, tax, money taken, refunded), a warning when part lines have no cost, sales by type, money taken by method, tax by rate, turnaround (median and average to ready and to pickup), techs with tickets, hours, labor, parts and commission, sales by day, and the refunds | Print it; **Commission rates** (`CommissionDialog`) sets each user's percent of the labor they bill | `GET /api/report/period`, `/commissions`, [Reports API](../../api/report.md) |
| **Timesheets** | Who's on the clock now, then each person's time on the clock over this month, last month, this year, last year or chosen days, with their total hours, each stretch's day, in, out, hours, station, note and who changed it | **Add time** for a forgotten clock in, change or delete a stretch (`TimeEntryDialog`: day, in, out (blank is still on, earlier than in runs past midnight), note) | `GET /api/timeclock/timesheet`, `/now`, `/entry/*`, [Time clock API](../../api/timeclock.md) |
| **Server and data** | Server address (badged **Not secure** over plain HTTP), name, version, uptime, database and photo sizes, Java and OS, mode, time zone, "Handled by reverse proxy", this device's address as the server sees it with whether it was forwarded (warned when not) and the trusted proxies; the shop profile; backup and restore, the daily backup's folder and last run, and the last restore (warned when it had no photos); mock data on development servers | Edit the shop profile (name, contact, tax rate, currency symbol, invoice footer, repair terms, repeat window, default paper, and the accounting export's names, `AccountingSettingsEditor`), download a backup, upload one to restore on next start (warned when the file looks database only), cancel a waiting restore or restart the server to apply it (when `canRestart`), import customers or inventory from a CSV (`ImportCard`, `ImportDialog`, `AdminImport`), seed test data | [Backup and restore](../../server/backup-restore.md), [Running a server](../../operations.md) |
| **Audit log** | Who did what, newest first, colored by action | Load more | [Audit log](../../server/audit-log.md) |

![The Users tab](../../images/admin-users.png)

### Dialogs

`AdminScreen` owns the users tab's dialogs (`UserDialog`, `ResetPasswordDialog`, `InviteDialog`, `ApproveDialog`,
`InviteCodeDialog` and the delete confirmation), the same way the records screen owns its own. `UserDialog` only
asks for a password when adding a user; existing users get a new one through Reset password, which also ends
their sessions. A new invite's setup code is shown once in `InviteCodeDialog` and can't be looked up again, only
replaced by renewing.

The backup and restore card streams both ways: the backup downloads through `saveDownload` and the restore uploads
through `fileBody`, so neither is held in memory (except on web, which reads a restore whole and refuses one over
512 MB). See [Transfers and photos](../../client/transfers-and-photos.md).

**Import** reads the picked CSV on the device (`parseCsv`, `ImportSheet` in `components/data/importing/`: quotes,
semicolon or tab separators, column matching by header name). `AdminImport` sends the rows it could read as a dry
run (`?dryRun=true`) and lines the answer up with the file's lines, again each time a column is matched differently,
and the dialog shows the first 100 rows with the counts for all. **Import** sends the same rows for real, then the
lists reload.

**Restart now** (next to a waiting restore, only when the server says `canRestart`) asks for confirmation, then
`AdminServer.restartServer()` calls `POST /api/admin/restart` and waits: first for `/api/ping` to stop answering
(up to 15 seconds), then for it to answer again (up to 3 minutes, since unpacking a big restore takes a while),
showing "Restarting the server" in the card meanwhile. Then it reloads the tab, which shows the restore.

## Where it lives

| What | Where |
|------|-------|
| Screen, tabs, users dialogs | `ui/screens/admin/AdminScreen.kt` |
| State and actions | `ui/screens/admin/components/AdminHandler.kt`, and its parts `AdminAccounts.kt`, `AdminDevices.kt`, `AdminServer.kt`, `AdminSupport.kt` |
| The tabs, in order | `ui/screens/admin/components/AdminTab.kt` |
| Shared bits (user state chip, access level dropdown) | `ui/screens/admin/components/AdminCommon.kt` |
| One folder per tab | `ui/screens/admin/<overview|users|sessions|stations|inventory|invoices|reports|timesheets|messages|server|audit>/` |
| Render tests | `ScreenRenderTest.adminOverview()`, `adminUsers()` |
| Handler tests | `composeApp/src/commonTest/.../ui/screens/admin/AdminHandlerTest.kt` |

## Adding a tab

1. Add an entry to the `AdminTab` enum (`components/AdminTab.kt`) with its label. The enum order is the tab order.
2. Add its state and loading to the part of `AdminHandler` it belongs with (or a new part, made in
   `AdminHandler`'s constructor, if it's its own area), and a branch for it in `refresh(tab)`.
3. Put the tab's composable in its own folder under `ui/screens/admin/` and add a branch for it in
   `AdminContent`'s `when`.
4. Wrap the server routes it uses in `authenticate("admin")`.

## Gotchas

- **Links to records go through the main tab index.** Clicking a ticket or a low stock item selects it in its
  handler, sets `changeMainTabIndex` (1 for tickets, 2 for inventory) and navigates to Records with `navigateTo`, so
  the back stack doesn't grow. An invoice opens its ticket on the counter the same way (`onOpenTicketAtCounter`).
- **The Invoices tab loads through the counter**, whose `counter.invoices` (`CounterInvoices`) owns the invoice
  list (`load`, `void`).
  The screen's Refresh reloads it when that tab is open.
- **The Users tab is the only place users are edited.** The records screen has no users tab.
- **After a restart to restore, the device may have to log in again.** The sessions table comes from the backup, so
  this device's session is only there if it was when the backup was made.
- **The mock data card only shows on development servers** (`ServerInfoResponse.developmentMode`), and the server
  refuses the request on any other.

## Known gaps

- A tab reloads every time it's opened, even if it was loaded a moment ago.

## See also

- [Users and accounts](../../server/users-and-accounts.md)
- [Authentication and sessions](../../server/auth.md)
- [PINs and stations](../../server/pins-and-stations.md)
- [Backup and restore](../../server/backup-restore.md)
- [Audit log](../../server/audit-log.md)
- [Running a server](../../operations.md)
