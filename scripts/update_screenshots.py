#!/usr/bin/env python3
"""
Copies the screen renders into docs/images/screens/ and writes docs/screenshots.md, the gallery the README links to.

The renders come from ScreenRenderTest (composeApp/build/screen-renders/, see docs/testing.md). The dark ones are
used: wide ones scaled down to 1080 px, both kinds cut to 256 colors so the repo stays small.

Usage:
    python3 scripts/update_screenshots.py            # render first, then copy (needs the JDK)
    python3 scripts/update_screenshots.py --no-render  # use the renders already in the build folder

Needs Pillow (pip install pillow).
"""

import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RENDERS = ROOT / "composeApp/build/screen-renders"
OUT = ROOT / "docs/images/screens"
PAGE = ROOT / "docs/screenshots.md"
WIDE_WIDTH = 1080

# The gallery: sections of (render name, caption). Each shows the wide render and, beside it, the phone one
SECTIONS = [
    ("Counter", [
        ("counter-home", "The counter: search across tickets and customers, with the queue beside it (behind its button on a phone)"),
        ("counter-ticket", "A ticket at the counter: status, device, notes, messages, estimate and work"),
        ("check-in", "Checking a device in, a step at a time"),
        ("pickup", "Pickup: the invoice, payments and what's still owed"),
        ("pickup-tax-classes", "An invoice with more than one tax rate"),
        ("counter-sale", "A sale straight from stock, no ticket"),
        ("counter-customer", "A customer's card: tickets, devices, store credit and invoices"),
        ("counter-estimate-dialog", "Sending an estimate for the customer to approve"),
    ]),
    ("Records", [
        ("records-tickets", "Tickets: the list and the picked one's details"),
        ("records-customers", "Customers"),
        ("records-inventory", "Inventory, with SKUs, categories and stock"),
        ("records-ticket-work", "A ticket's work log"),
        ("records-ticket-work-dialog-parts", "Logging time and parts on a ticket"),
        ("records-ticket-dialog", "Editing a ticket"),
        ("records-customer-dialog", "Editing a customer"),
        ("records-inventory-dialog", "Editing an inventory item"),
        ("records-view-only", "What a Restricted user sees: no add, edit or delete"),
    ]),
    ("Purchasing", [
        ("purchasing-order", "A purchase order partly received"),
        ("purchasing-draft", "A draft order"),
        ("purchasing-suppliers", "Suppliers"),
    ]),
    ("Appointments", [
        ("appointments", "The week's calendar"),
        ("appointment-dialog", "Booking an appointment"),
    ]),
    ("Admin", [
        ("admin-overview", "Overview and the end of day report"),
        ("admin-reports", "Reports: sales, profit, tax, techs and turnaround"),
        ("admin-commission-dialog", "Commission rates"),
        ("admin-invoices", "Invoices and gift cards"),
        ("admin-invoice-export", "Exporting for a spreadsheet, QuickBooks or Xero"),
        ("admin-refund-dialog", "Refunding an invoice"),
        ("admin-timesheets", "Timesheets and who's on the clock"),
        ("admin-time-entry-dialog", "Fixing time on the clock"),
        ("admin-users", "Users, account requests and invites"),
        ("admin-sessions", "Logged in devices and blocked logins"),
        ("admin-stations", "Counter stations and kiosk mode"),
        ("admin-low-stock", "Low stock"),
        ("admin-messages", "Messages to customers: email, texts and their wording"),
        ("admin-server", "Server, backup and restore, and the shop profile"),
        ("admin-import", "Importing customers from a CSV"),
        ("admin-audit", "The audit log"),
    ]),
    ("Settings, kiosk and connecting", [
        ("settings", "Settings"),
        ("settings-account", "Account, and a connection problem"),
        ("kiosk-lock", "A kiosk's lock screen: sign in or clock in by PIN"),
        ("server-trust", "Trusting a server with a private certificate"),
    ]),
]


def save(source: Path, target: Path, width: int | None):
    image = Image.open(source).convert("RGB")
    if width and image.width > width:
        image = image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)
    image.quantize(colors=256, method=Image.Quantize.MEDIANCUT).save(target, optimize=True)


def main():
    if "--no-render" not in sys.argv:
        gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
        subprocess.run([str(gradlew), ":composeApp:desktopTest", "--tests", "*ScreenRenderTest", "-q"], cwd=ROOT, check=True)

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()

    missing = []
    lines = [
        "# Screenshots",
        "",
        "Every screen, drawn by `ScreenRenderTest` with sample data, in the dark Bench theme: wide on the left, a phone",
        "on the right. Written by [`scripts/update_screenshots.py`](../scripts/update_screenshots.py); run it again after",
        "changing a screen. [Testing](testing.md#render-tests) has more on the renders.",
        "",
    ]
    lines += ["- " + ", ".join(f"[{title}](#{title.lower().replace(',', '').replace(' ', '-')})" for title, _ in SECTIONS), ""]

    for title, screens in SECTIONS:
        lines += [f"## {title}", "", "| | Wide | Phone |", "|---|---|---|"]
        for name, caption in screens:
            wide = RENDERS / f"{name}-dark.png"
            narrow = RENDERS / f"{name}-narrow-dark.png"
            if not wide.exists():
                missing.append(wide.name)
                continue
            save(wide, OUT / f"{name}.png", WIDE_WIDTH)
            cells = [caption, f'<img src="images/screens/{name}.png" width="560" alt="{name}">']
            if narrow.exists():
                save(narrow, OUT / f"{name}-narrow.png", None)
                cells.append(f'<img src="images/screens/{name}-narrow.png" width="180" alt="{name} on a phone">')
            else:
                cells.append("")
            lines.append("| " + " | ".join(cells) + " |")
        lines.append("")

    PAGE.write_text("\n".join(lines))
    size = sum(f.stat().st_size for f in OUT.glob("*.png")) / 1_000_000
    print(f"{len(list(OUT.glob('*.png')))} images, {size:.1f} MB, in {OUT.relative_to(ROOT)}; wrote {PAGE.relative_to(ROOT)}")
    if missing:
        print("Not rendered, left out: " + ", ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main()
