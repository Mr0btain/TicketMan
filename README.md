# TicketMan

A ticket tracking app for shop that run their work through tickets. Staff check devices in at
the counter, track the repair, take payment at pickup and keep customer, inventory and photo records
along the way.

It's a Kotlin Multiplatform project: a Compose Multiplatform client with a Ktor server with an H2 database.

There will most likely be breaking changes often!

![The counter screen showing a ticket, its invoice and the queue](docs/images/counter.png)

**[See every screen](docs/screenshots.md)**, wide and on a phone.

## What it does

- **The counter**: check devices in (several at once, with an intake checklist, the passcode and a signature),
  quick search, the queue, pickup with payments, sales from stock, refunds, store credit and gift cards
- **Repairs**: custom statuses, a work log that bills time and parts, estimates the customer approves, warranties,
  storage fees for devices left too long, and each customer's devices with their history
- **Customers**: a status page they can check without logging in, and email and text messages when their device
  is ready
- **Stock and purchasing**: SKUs, barcodes and labels, scanner input, low stock, suppliers and purchase orders
- **Running the shop**: reports (sales, profit, tax, techs and commission, turnaround), an appointment calendar, a
  time clock with timesheets, and exports for a spreadsheet, QuickBooks or Xero
- **Staff and devices**: access levels, account requests and invites, PIN sign in at counter stations, kiosk mode,
  an audit log, and backups with restore

## Quick start

You need JDK 17 or newer (21 is what it's developed on). Gradle comes with the repo.

```bash
# Start a server on port 8098 that keeps its data in /tmp/tm, with admin / admin_pass as the first login
PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment

# In another terminal, start the desktop client and connect it to http://localhost:8098
./gradlew :composeApp:run
```

## Running the server in Docker

With the web client
```bash
docker run -d --name ticketman -p 8080:8080 -e ADMIN_PASSWORD=change_me -v ticketman-data:/data mr0btain/ticketman:latest
```

Without the web client
```bash
docker run -d --name ticketman -p 8080:8080 -e ADMIN_PASSWORD=change_me -v ticketman-data:/data mr0btain/ticketman-server:latest
```

Then open `http://localhost:8080` and log in as `admin` with whatever you set the password to be.

- [Run TicketMan in Docker](docs/how-to/run-in-docker.md): the published image, trying it from the source with
  made up records (`scripts/docker_local.sh`), and a shop's server behind Caddy with HTTPS
- [Running a server](docs/operations.md): first start, the data folder, backups and restore, stations and kiosks,
  and putting the customer status page on the internet
- [The Docker Hub page](docker/dockerhub.md): every setting, volume and port of the image

## Platforms

| Platform | Status |
|----------|--------|
| Linux | Main target, Done |
| Windows | Done |
| macOS | Builds arm64 and x64 DMGs, not tested yet |
| Web (Wasm) | Served by the server when built with `-PwithWeb`, Done |
| Server | Done |
| Android | Done |
| iOS | Builds but not tested yet (needs a Mac) |

## Roadmap / TODOs

([the full roadmap](docs/roadmap.md)):

## Caveats

If there has to be Any breaking changes they should be made between Major revisions only. Ex: 0.8.0->0.9.0.
At the moment there will be breaking changes all throughout, but when we hit stable a 1.0.0 release then we can
stick to this break between majors.

On high DPI monitors the app can show small GUI. This is intermittent for me.

## Documentation

Developer docs are in [`docs/`](docs/README.md): how the code is organized, how a request travels from the UI to the
database, configuration, running a server and more. Pretty much all of this is AI generated. I haven't looked through 
it yet so it may or may not be accurate.

## License

TicketMan is released under the [MIT License](LICENSE). The fonts it bundles (Barlow, Atkinson Hyperlegible and
JetBrains Mono) are under the SIL Open Font License, with their licenses in [`composeApp/licenses/`](composeApp/licenses).
