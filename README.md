# TicketMan

A ticket tracking app for repair shops, or any shop that runs its work through tickets. Staff check devices in at
the counter, track the repair, take payment at pickup and keep customer, inventory and photo records along the way.

It's a Kotlin Multiplatform project: a Compose Multiplatform client with a Ktor server with an H2 database.

There will most likely be breaking changes often so this is nowhere near production ready. But it does work!

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

## Platforms

| Platform | Status |
|----------|--------|
| Linux | Main target, Done |
| Windows | Done |
| MacOs | Not Tested |
| Web (Wasm) | Served by the server when built with `-PwithWeb`, Done |
| Server | Done |
| Android | Done |
| iOS | Not Tested |

## Caveats

If there has to be Any breaking changes they should be made between Major revisions only. Ex: 0.8.0->0.9.0

## Documentation

Developer docs are in [`docs/`](docs/README.md): how the code is organized, how a request travels from the UI to the
database, configuration, running a server and more. Pretty much all of this is AI generated. I haven't looked through 
it yet so it may or may not be accurate.
