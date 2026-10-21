# TicketMan

A ticket tracking app for repair shops, or any shop that runs its work through tickets. Staff check devices in at
the counter, track the repair, take payment at pickup and keep customer, inventory and photo records along the way.

It's a Kotlin Multiplatform project: a Compose Multiplatform client talking over HTTP to a Ktor server that keeps
everything in an H2 database. I started it to learn Kotlin and Compose, so some older parts are rougher than newer
ones and get tidied as I go.

![The counter screen showing a ticket, its invoice and the queue](docs/images/counter.png)

## Quick start

You need JDK 17 or newer (21 is what it's developed on). Gradle comes with the repo.

```bash
# Start a server on port 8098 that keeps its data in /tmp/tm, with admin / admin_pass as the first login
PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment

# In another terminal, start the desktop client and connect it to http://localhost:8098
./gradlew :composeApp:run
```

On Windows use `.\gradlew.bat` in PowerShell, and set the variables with `$env:PORT = "8098"` and so on first.

[Getting started](docs/getting-started.md) covers the other targets, tests and hot reload.

## What works

| Area | Status |
|------|--------|
| Customers, tickets, inventory and users, with notes and photos | Done |
| Counter: check-in with signature and deposit, queue, pickup and payment, invoices, printed claim slips, receipts, device tags and daily reports | Done |
| Kiosk mode: PIN login on approved stations, idle lock | Done |
| Admin: overview, users and account requests, sessions, stations, audit log, low stock, backup and restore | Done |
| Themes (Bench, Copper, Mono), fonts, light and dark, reduce transparency | Done |
| Platform | Status |
|----------|--------|
| Desktop (Linux, Windows, macOS) | Main target, most complete |
| Web (Wasm) | Served by the server when built with `-PwithWeb` ([details](docs/operations.md#web-client)). |
| Android | Compiles, not yet tried on a device ([details](docs/client/platforms.md#known-gaps)) |
| iOS | Not wired up, the targets are commented out |

## Documentation

Developer docs are in [`docs/`](docs/README.md): how the code is organized, how a request travels from the UI to the
database, configuration, running a server and more.
