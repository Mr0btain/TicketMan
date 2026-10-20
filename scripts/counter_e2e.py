"""
End to end run of the counter and kiosk flow against a real TicketMan server, over HTTP like the client.
Every step checks the response and stops at the first surprise.

Start a development server on an empty data folder first, with ADMIN_PASSWORD=admin_pass:
    PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
then:
    python3 scripts/counter_e2e.py http://localhost:8098

Or through a reverse proxy (scripts/dev_proxy.sh), whose certificate it can't check:
    python3 scripts/counter_e2e.py https://localhost:8444 --insecure

The last steps send a big photo, download a backup and upload it to restore, since those are what proxies break.
With --restart, against a server started with RESTART_ON_EXIT=true and something to start it again (the Docker
image, scripts/docker_smoke.sh), the restore isn't cancelled: the server restarts to apply it and the data is checked.
"""
import argparse
import hashlib
import json
import os
import ssl
import struct
import time
import urllib.request
import uuid
import zlib

parser = argparse.ArgumentParser(description="Walk check-in to pickup against a fresh TicketMan server")
parser.add_argument("base", nargs="?", default="http://localhost:8098", help="the server's (or proxy's) address")
parser.add_argument("--insecure", action="store_true", help="don't check the HTTPS certificate")
parser.add_argument("--restart", action="store_true", help="finish the restore by restarting the server from the admin route")
args = parser.parse_args()

BASE = args.base.rstrip("/")
CONTEXT = ssl._create_unverified_context() if args.insecure else None
step_number = 0


def send(request):
    try:
        with urllib.request.urlopen(request, context=CONTEXT, timeout=300) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers


def call(method, path, body=None, token=None, expect=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", content_type)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    status, content, _ = send(request)
    text = content.decode(errors="replace")
    if expect is not None and status != expect:
        raise SystemExit(f"FAIL {method} {path}: expected {expect}, got {status}: {text[:300]}")
    try:
        return status, json.loads(text) if text else None
    except json.JSONDecodeError:
        return status, text


def step(text):
    global step_number
    step_number += 1
    print(f"{step_number:2}. {text}")


def check(condition, message):
    if not condition:
        raise SystemExit("FAIL " + message)


def noise_png(width, height):
    # Random pixels don't compress, so the file is about width * height * 3 bytes, like a big phone photo
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\0" + os.urandom(width * 3) for _ in range(height))
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 1)) + chunk(b"IEND", b"")


def server_up():
    try:
        status, content, _ = send(urllib.request.Request(BASE + "/api/ping"))
        return status == 200 and content.decode(errors="replace").strip() == "TicketMan"
    except (OSError, urllib.error.URLError):
        # Refused or cut off while it restarts. Behind a proxy it's a 502 instead
        return False


def wait_for(up, seconds):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if server_up() == up:
            return True
        time.sleep(1)
    return False


def uid():
    # Version 7, like the app makes: the first 48 bits are the time in milliseconds
    millis = int(time.time() * 1000)
    rand = uuid.uuid4().int
    value = (millis << 80) | (0x7 << 76) | ((rand >> 64) & 0x0FFF) << 64 | (0b10 << 62) | (rand & ((1 << 62) - 1))
    return str(uuid.UUID(int=value))


# ------------------------------------------ Admin sets the shop up ------------------------------------------
_, auth = call("POST", "/api/auth", {"username": "admin", "password": "admin_pass", "deviceId": "office", "deviceName": "Office PC"}, expect=202)
admin = auth["accessToken"]
step("Admin logs in with the password from ADMIN_PASSWORD")

call("POST", "/api/shop/update", {
    "name": "Fix It Shop", "street": "1 Main St", "city": "Springfield", "phone": "555-0100",
    "taxRate": "8.25", "repairTerms": "Not responsible for data loss.", "defaultPaper": "LETTER", "repeatWindowDays": 90
}, admin, expect=202)
call("POST", "/api/data/inventory/add", {"identifier": (screen := uid()), "name": "iPhone 14 Screen", "stock": 5, "priceCents": 12000, "costCents": 6000}, admin, expect=201)
step("Shop profile (8.25% tax, terms, letter paper) and a screen in stock (5 at $120)")

# ------------------------------------------ New employee asks for an account ------------------------------------------
call("POST", "/api/auth/request-account", {"username": "newtech", "password": "newtech_pass", "name": "Nina Tech"}, expect=202)
_, pending_login = call("POST", "/api/auth", {"username": "newtech", "password": "newtech_pass"}, expect=202)
check(pending_login["status"] == "PENDING_APPROVAL", "login before approval should say pending")
step("Employee requests an account; logging in says it's waiting for approval")

_, pending = call("GET", "/api/user/pending", token=admin, expect=200)
newtech_id = next(p["user"]["identifier"] for p in pending if p["user"]["username"] == "newtech")
call("POST", f"/api/user/approve/{newtech_id}", {"accessLevel": "NORMAL"}, admin, expect=202)
step("Admin approves them as NORMAL")

_, first = call("POST", "/api/auth", {"username": "newtech", "password": "newtech_pass", "deviceId": "desk", "deviceName": "Back Desk"}, expect=202)
check(first["status"] == "NEEDS_SETUP", "first login should need setup")
call("GET", "/api/data/ticket/fetch?offset=0&limit=5", token=first["accessToken"], expect=403)
call("POST", "/api/user/setup", {"name": "Nina Tech", "email": "nina@shop.test", "pin": "246813"}, first["accessToken"], expect=202)
_, ready = call("POST", "/api/auth", {"username": "newtech", "password": "newtech_pass", "deviceId": "desk", "deviceName": "Back Desk"}, expect=202)
newtech = ready["accessToken"]
check(ready["status"] == "OK", "after setup login should be OK")
step("First login is held to setup (other routes refused) until they set details and a PIN")

# ------------------------------------------ Invited clerk ------------------------------------------
_, invite = call("POST", "/api/user/invite", {"username": "clerk", "name": "Cleo Clerk", "accessLevel": "RESTRICTED"}, admin, expect=201)
_, via_code = call("POST", "/api/auth", {"username": "clerk", "password": invite["code"]}, expect=202)
check(via_code["status"] == "INVITE_SETUP", "setup code should start invite setup")
call("POST", "/api/auth/setup/invite", {"setupToken": via_code["setupToken"], "password": "clerk_password", "name": "Cleo Clerk", "pin": "135792"}, expect=202)
step(f"Admin invites a Restricted clerk (code {invite['code']}); clerk sets password and PIN with it")

# ------------------------------------------ Counter station and kiosk ------------------------------------------
_, registration = call("POST", "/api/station/register", {"deviceId": "counter-1", "name": "Front Counter"}, newtech, expect=201)
station = registration["credentials"]
call("POST", "/api/auth/pin", {"station": station, "userIdentifier": newtech_id, "pin": "246813"}, expect=403)
call("POST", f"/api/admin/stations/{station['stationId']}/approve", token=admin, expect=202)
call("POST", f"/api/admin/stations/{station['stationId']}/kiosk", {"enabled": True, "idleTimeoutSeconds": 120}, admin, expect=202)
_, status = call("POST", "/api/auth/station/status", station, expect=200)
check(status["kioskEnabled"] and status["status"] == "APPROVED", "station should be an approved kiosk")
step("Tech registers the counter as a station; PIN refused until an admin approves it and turns on kiosk mode")

_, staff = call("POST", "/api/auth/station/staff", station, expect=200)
check({s["username"] for s in staff} >= {"newtech", "clerk"}, f"staff list was {staff}")
_, pin_auth = call("POST", "/api/auth/pin", {"station": station, "userIdentifier": newtech_id, "pin": "246813"}, expect=202)
pin = pin_auth["accessToken"]
step("Kiosk lists who can PIN in; the tech PINs in")

# ------------------------------------------ Check in at the counter ------------------------------------------
call("POST", "/api/data/customer/add", {"identifier": (jane := uid()), "name": "Jane Doe", "phone": "(555) 123-4567"}, pin, expect=201)
signature = {"strokes": [[{"x": 0.1, "y": 0.5}, {"x": 0.5, "y": 0.3}, {"x": 0.9, "y": 0.6}]], "aspectRatio": 3.0, "signedName": "Jane Doe"}
ticket_one = {"identifier": uid(), "device": {"manufacturer": "Apple", "model": "iPhone 14", "serial": "SN123"}, "problem": "Cracked screen",
              "accessories": "Case", "estimateCents": 15000, "priority": {"priority": "NORMAL", "time": 0}}
call("POST", "/api/counter/check-in", {"ticket": ticket_one, "customerIdentifier": jane}, pin, expect=400)
_, checked_in = call("POST", "/api/counter/check-in", {
    "ticket": ticket_one, "customerIdentifier": jane, "technicianIdentifier": newtech_id,
    "deposit": {"method": "CARD", "amountCents": 5000}, "signature": signature
}, pin, expect=201)
number = checked_in["ticket"]["number"]
label = f"{number['year'] % 100:02d}-{number['sequence']:08d}"
step(f"Customer added; check-in refused without the signature the terms need, then checked in as {label} with a $50 card deposit")

_, found = call("GET", "/api/counter/search?query=5551234567&limit=10", token=pin, expect=200)
check(any(t["ticket"]["identifier"] == ticket_one["identifier"] for t in found["tickets"]), "phone search should find the ticket")
_, by_number = call("GET", f"/api/data/ticket/number/{label}", token=pin, expect=200)
check(by_number["identifier"] == ticket_one["identifier"], "lookup by number")
step("Counter search by phone digits finds Jane's ticket; lookup by number works")

_, repeats = call("POST", "/api/counter/repeat-check", {"customerIdentifier": jane, "device": {"serial": "sn123"}, "excludeTicket": uid()}, pin, expect=200)
check(len(repeats) == 1, f"repeat check found {len(repeats)}")
step("Repeat check flags the same serial for the same customer")

# ------------------------------------------ Clerk can look but not touch ------------------------------------------
_, clerk_auth = call("POST", "/api/auth/pin", {"station": station, "userIdentifier": invite["user"]["identifier"], "pin": "135792"}, expect=202)
clerk = clerk_auth["accessToken"]
call("GET", "/api/user/me", token=pin, expect=401)
call("GET", "/api/counter/queue", token=clerk, expect=200)
call("POST", "/api/data/ticket/update", {**checked_in["ticket"], "status": {"status": "DONE", "time": 0}}, clerk, expect=403)
step("Clerk PINs in (tech's PIN session ends); clerk can see the queue but can't change the ticket")

_, pin_auth = call("POST", "/api/auth/pin", {"station": station, "userIdentifier": newtech_id, "pin": "246813"}, expect=202)
pin = pin_auth["accessToken"]
call("POST", "/api/data/ticket/update", {**checked_in["ticket"], "status": {"status": "DONE", "time": 0}}, pin, expect=202)
_, queue = call("GET", "/api/counter/queue", token=pin, expect=200)
check(any(t["ticket"]["identifier"] == ticket_one["identifier"] for t in queue["readyForPickup"]), "ticket should be ready for pickup")
step("Tech PINs back in and marks it ready; it shows in the ready-for-pickup queue")

# ------------------------------------------ Pickup ------------------------------------------
invoice = checked_in["invoice"]
_, invoice = call("POST", f"/api/invoice/{invoice['identifier']}/update", {"charges": [
    {"identifier": uid(), "type": "PART", "description": "iPhone 14 Screen", "quantity": 1.0, "unitPriceCents": 12000, "inventoryIdentifier": screen, "taxable": True},
    {"identifier": uid(), "type": "LABOR", "description": "Install", "quantity": 0.5, "unitPriceCents": 6000, "taxable": False}
], "discountCents": 0}, pin, expect=200)
check(invoice["totalCents"] == 12000 + 990 + 3000, f"total was {invoice['totalCents']}")
status, owed = call("POST", "/api/counter/pickup", {"ticketIdentifier": ticket_one["identifier"], "payments": [{"method": "CASH", "amountCents": 1000}]}, pin)
check(status == 409 and "still owed" in owed, f"short pickup said {status} {owed}")
_, paid = call("POST", "/api/counter/pickup", {"ticketIdentifier": ticket_one["identifier"], "payments": [{"method": "CASH", "amountCents": 11000}]}, pin, expect=200)
check(paid["status"] == "PAID" and paid["totalCents"] - sum(p["amountCents"] for p in paid["payments"]) == -10, "should be paid with 10 cents change")
_, item = call("GET", f"/api/data/inventory/get/{screen}", token=pin, expect=200)
check(item["stock"] == 4, f"stock is {item['stock']}")
step("Pickup: $120 screen + half hour labor + 8.25% tax on the part = $159.90; short payment refused; paid with cash, 10c change, stock 5 -> 4")

# ------------------------------------------ Admin wraps up ------------------------------------------
_, report = call("GET", "/api/admin/report/daily", token=admin, expect=200)
check(report["takenByMethod"] == {"CARD": 5000, "CASH": 11000}, f"taken {report['takenByMethod']}")
check(report["ticketsCheckedIn"] == 1 and report["ticketsPickedUp"] == 1 and report["invoicesPaid"] == 1, f"report {report}")
step(f"End of day: card $50.00, cash $110.00, sales ${report['salesCents'] / 100:.2f}, 1 in, 1 out")

_, sessions = call("GET", "/api/admin/sessions", token=admin, expect=200)
kinds = {(s["username"], s["deviceName"], s["kind"]) for s in sessions}
check(("newtech", "Front Counter", "PIN") in kinds and ("newtech", "Back Desk", "PASSWORD") in kinds, f"sessions {kinds}")
step("Sessions show the tech on the Back Desk (password) and the Front Counter (PIN)")

_, audit = call("GET", "/api/admin/audit/fetch?offset=0&limit=100", token=admin, expect=200)
by_tech = [e for e in audit if e["actorName"] == "newtech"]
check(any("Checked in" in e["details"] for e in by_tech) and any("picked up" in e["details"] for e in by_tech), "audit should credit the tech")
step(f"Audit log credits the tech's PIN session for check-in and pickup ({len(by_tech)} entries by newtech)")

# ------------------------------------------ Big transfers, what proxies break ------------------------------------------
photo = noise_png(2400, 1800)
boundary = uuid.uuid4().hex
multipart = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"bench.png\"\r\n"
             f"Content-Type: image/png\r\n\r\n").encode() + photo + f"\r\n--{boundary}--\r\n".encode()
_, image = call("POST", "/api/image/upload", token=pin, raw=multipart, content_type=f"multipart/form-data; boundary={boundary}", expect=201)
check(image["width"] == 2400 and image["height"] == 1800, f"photo came back {image['width']}x{image['height']}")
call("POST", f"/api/data/ticket/add/note/{ticket_one['identifier']}", {"identifier": uid(), "message": "Before photo", "images": [image]}, pin, expect=201)
status, display, _ = send(urllib.request.Request(f"{BASE}/api/image/{image['identifier']}/display", headers={"Authorization": "Bearer " + pin}))
check(status == 200 and display[:2] == b"\xff\xd8", f"display size answered {status}")
step(f"A {len(photo) / 1_000_000:.1f} MB photo uploads, goes on the ticket's note and comes back as a {len(display) // 1000} KB JPEG")

# Started in the background and asked after, like the client, so a proxy never waits on the build
_, job = call("POST", "/api/admin/backup/start", {"includeImages": True}, admin, expect=202)
for _ in range(300):
    if job["state"] != "RUNNING":
        break
    time.sleep(1)
    _, job = call("GET", f"/api/admin/backup/status/{job['id']}", token=admin, expect=200)
check(job["state"] == "DONE", f"backup ended {job}")
backup = job["ticket"]
status, archive, headers = send(urllib.request.Request(BASE + backup["downloadPath"]))
check(status == 200 and len(archive) == backup["sizeBytes"], f"backup download answered {status} with {len(archive)} of {backup['sizeBytes']} bytes")
check(hashlib.sha256(archive).hexdigest() == backup["sha256"], "backup download doesn't match its sha256")
status, part, _ = send(urllib.request.Request(BASE + backup["downloadPath"], headers={"Range": "bytes=100-199"}))
check(status == 206 and part == archive[100:200], f"a range of the backup answered {status}")
step(f"Backup with photos built in the background ({len(archive) / 1_000_000:.1f} MB) downloads whole and matches its sha256; a range of it works too")

# Made after the backup, so the restore takes it away
call("POST", "/api/data/customer/add", {"identifier": (late := uid()), "name": "After Backup"}, admin, expect=201)
_, pending = call("POST", "/api/admin/restore", token=admin, raw=archive, content_type="application/zip", expect=202)
check(pending["sizeBytes"] == len(archive) and pending["manifest"]["includesImages"], f"restore staged {pending}")

if not args.restart:
    call("DELETE", "/api/admin/restore", token=admin, expect=202)
    status, _ = call("GET", "/api/admin/restore", token=admin)
    check(status == 204, f"restore still waiting after cancelling ({status})")
    step("The backup uploads to restore and is waiting for a restart; cancelled so the server starts as it is")
    print("\nAll steps passed")
    raise SystemExit(0)

step("The backup uploads to restore and is waiting for a restart")

# ------------------------------------------ Restart to restore ------------------------------------------
_, info = call("GET", "/api/admin/server", token=admin, expect=200)
check(info.get("canRestart"), "server says it can't restart itself; is RESTART_ON_EXIT=true set?")
started_at = info["startedAt"]
call("POST", "/api/admin/restart", token=admin, expect=202)
check(wait_for(False, 30), "server didn't stop after being asked to restart")
check(wait_for(True, 180), "server didn't come back after restarting")
step("Admin restarts the server; it goes away and comes back")

_, auth = call("POST", "/api/auth", {"username": "admin", "password": "admin_pass", "deviceId": "office", "deviceName": "Office PC"}, expect=202)
admin = auth["accessToken"]
_, info = call("GET", "/api/admin/server", token=admin, expect=200)
check(info["startedAt"] > started_at, "server reports the same start time, so it didn't restart")
check(info.get("lastRestore") and info["lastRestore"]["manifest"]["createdAt"] == pending["manifest"]["createdAt"], f"last restore {info.get('lastRestore')}")
status, _ = call("GET", "/api/admin/restore", token=admin)
check(status == 204, f"restore still waiting after the restart ({status})")
call("GET", f"/api/data/customer/get/{jane}", token=admin, expect=200)
call("GET", f"/api/data/customer/get/{late}", token=admin, expect=404)
status, restored_photo, _ = send(urllib.request.Request(f"{BASE}/api/image/{image['identifier']}/display", headers={"Authorization": "Bearer " + admin}))
check(status == 200 and restored_photo == display, f"photo after the restore answered {status}")
step("Restored: Jane and the photo are back, the customer added after the backup is gone, and the Server tab shows the restore")

print("\nAll steps passed")
