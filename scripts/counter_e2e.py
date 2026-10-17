"""
End to end run of the counter and kiosk flow against a real TicketMan server, over HTTP like the client.
Every step checks the response and stops at the first surprise.

Start a development server on an empty data folder first, with ADMIN_PASSWORD=admin_pass:
    PORT=8098 DATA_PATH=/tmp/tm ADMIN_PASSWORD=admin_pass ./gradlew :server:run -Pdevelopment
then:
    python3 scripts/counter_e2e.py http://localhost:8098
"""
import json
import sys
import time
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8098"
step_number = 0


def call(method, path, body=None, token=None, expect=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request) as response:
            status, text = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, text = error.code, error.read().decode()
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
call("POST", "/api/auth/request-account", {"username": "newtech", "password": "newtech_pass", "name": "nina:tech"}, expect=202)
_, pending_login = call("POST", "/api/auth", {"username": "newtech", "password": "newtech_pass"}, expect=202)
check(pending_login["status"] == "PENDING_APPROVAL", "login before approval should say pending")
step("Employee requests an account; logging in says it's waiting for approval")

_, pending = call("GET", "/api/user/pending", token=admin, expect=200)
newtech_id = next(p["user"]["identifier"] for p in pending if p["user"]["username"] == "newtech")
call("POST", f"/api/user/approve/{newtech_id}", {"accessLevel": "NORMAL"}, admin, expect=202)
step("Admin approves them as NORMAL")

_, first = call("POST", "/api/auth", {"username": "newtech", "password": "newtech_pass", "deviceId": "desk", "deviceName": "Back Desk"}, expect=202)
check(first["status"] == "NEEDS_SETUP", "first login should need setup")
call("POST", "/api/data/ticket/fetch", {"offset": 0, "itemsPerPage": 5}, first["accessToken"], expect=401)
call("POST", "/api/user/setup", {"name": "nina:tech", "email": "nina@shop.test", "pin": "246813"}, first["accessToken"], expect=202)
_, ready = call("POST", "/api/auth", {"username": "newtech", "password": "newtech_pass", "deviceId": "desk", "deviceName": "Back Desk"}, expect=202)
newtech = ready["accessToken"]
check(ready["status"] == "OK", "after setup login should be OK")
step("First login is held to setup (other routes refused) until they set details and a PIN")

# ------------------------------------------ Invited clerk ------------------------------------------
_, invite = call("POST", "/api/user/invite", {"username": "clerk", "name": "cleo:clerk", "accessLevel": "RESTRICTED"}, admin, expect=201)
_, via_code = call("POST", "/api/auth", {"username": "clerk", "password": invite["code"]}, expect=202)
check(via_code["status"] == "INVITE_SETUP", "setup code should start invite setup")
call("POST", "/api/auth/setup/invite", {"setupToken": via_code["setupToken"], "password": "clerk_password", "name": "cleo:clerk", "pin": "135792"}, expect=202)
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
call("POST", "/api/data/customer/add", {"identifier": (jane := uid()), "name": "jane:doe", "phone": "(555) 123-4567"}, pin, expect=202)
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

_, found = call("POST", "/api/counter/search", {"query": "5551234567", "limit": 10}, pin, expect=200)
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
call("POST", "/api/data/ticket/update", {**checked_in["ticket"], "status": {"status": "DONE", "time": 0}}, clerk, expect=401)
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
_, item = call("POST", "/api/data/inventory/get", screen, pin, expect=200)
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

_, audit = call("POST", "/api/admin/audit/fetch", {"offset": 0, "itemsPerPage": 100}, admin, expect=200)
by_tech = [e for e in audit if e["actorName"] == "newtech"]
check(any("Checked in" in e["details"] for e in by_tech) and any("picked up" in e["details"] for e in by_tech), "audit should credit the tech")
step(f"Audit log credits the tech's PIN session for check-in and pickup ({len(by_tech)} entries by newtech)")

print("\nAll steps passed")
