package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.utils.formatDate
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.StatusTone
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.label
import org.mr0btain.ticketman.data.ticket.tone
import kotlin.uuid.ExperimentalUuidApi

/**
 * The customer status page's HTML. No scripts and nothing from elsewhere, so the page works on any phone and the
 * Content-Security-Policy can refuse everything else. Links are relative, so it works under any path a proxy puts
 * it at: from `/status/{token}`, `../status` is the lookup form and `{token}/photo/...` a photo
 */
@OptIn(ExperimentalUuidApi::class)
object StatusPage {
    /** A ticket's page */
    fun ticket(view: StatusService.View): String {
        val shop = view.shop
        val ticket = view.ticket
        val statuses = shop.statuses
        val token = ticket.statusToken.orEmpty()
        val device = ticket.device.toString().trim().ifBlank { "Your device" }

        val headline = when (view.current.status) {
            TicketStatusType.DONE -> "It's ready to pick up."
            TicketStatusType.PICKED_UP -> ticket.pickedUpAt?.let { "Picked up ${formatDate(it)}. Thanks for coming in." } ?: "Picked up. Thanks for coming in."
            else -> "We're working on it."
        }

        val body = buildString {
            append("""<section class="hero">""")
            append("""<p class="muted">Ticket <span class="num">${ticket.label.esc()}</span></p>""")
            append("<h1>${device.esc()}</h1>")
            if (view.firstName.isNotBlank()) append("<p>Hi ${view.firstName.esc()}. ${headline.esc()}</p>") else append("<p>${headline.esc()}</p>")
            append(pill(statuses.label(view.current), statuses.tone(view.current), large = true))
            if (ticket.promisedAt != null && view.current.status.isOpen) {
                append("""<p class="muted">Expected by ${formatDate(ticket.promisedAt!!).esc()}</p>""")
            }
            append("</section>")

            append(estimate(shop, view, token))

            append("""<section><h2>Progress</h2><ol class="timeline">""")
            view.timeline.reversed().forEach { step ->
                append("""<li class="${toneClass(statuses.tone(step.choice))}"><span class="label">${statuses.label(step.choice).esc()}</span>""")
                append("""<span class="muted">${formatDateTime(step.time).esc()}</span></li>""")
            }
            append("</ol></section>")

            if (view.updates.isNotEmpty()) {
                append("<section><h2>Updates</h2>")
                view.updates.forEach { update ->
                    append("""<article><p class="muted">${formatDateTime(update.time).esc()}</p>""")
                    if (update.message.isNotBlank()) append("""<p class="message">${update.message.esc()}</p>""")
                    if (update.photos.isNotEmpty()) {
                        append("""<div class="photos">""")
                        update.photos.forEach { photo ->
                            val path = "${token.esc()}/photo/${photo.identifier}"
                            val alt = photo.description.ifBlank { "Photo" }.esc()
                            append("""<a href="$path/${ImageVariant.DISPLAY.path}"><img src="$path/${ImageVariant.THUMB.path}" alt="$alt" loading="lazy"></a>""")
                        }
                        append("</div>")
                    }
                    append("</article>")
                }
                append("</section>")
            }

            val money = moneyRows(shop, view)
            if (money.isNotEmpty()) {
                append("""<section><h2>Cost</h2><dl class="rows">""")
                money.forEach { (label, amount, strong) ->
                    val cls = if (strong) """ class="strong"""" else ""
                    append("""<div$cls><dt>${label.esc()}</dt><dd class="num">${amount.esc()}</dd></div>""")
                }
                append("</dl></section>")
            }

            append(contact(shop))
        }

        return page(shop, "${ticket.label} at ${shop.name.ifBlank { "the shop" }}", body, formHref = "../status")
    }

    /**
     * The lookup form at `/status`
     * @param problem Why the last try didn't work, shown above the form
     */
    fun lookupForm(shop: ShopProfile, problem: String? = null, number: String = ""): String {
        val body = buildString {
            append("""<section class="hero"><h1>Check on your repair</h1>""")
            append("<p>Your ticket number is on your claim slip. Scanning its QR code brings you straight to your repair.</p></section>")
            append("<section>")
            if (problem != null) append("""<p class="problem" role="alert">${problem.esc()}</p>""")
            append("""<form method="post" action="status">""")
            append("""<label>Ticket number<input name="number" value="${number.esc()}" placeholder="26-00000042" required autocomplete="off" inputmode="numeric"></label>""")
            append("""<label>Last 4 digits of your phone number<input name="phone" required maxlength="4" pattern="[0-9]{4}" inputmode="numeric" autocomplete="off"></label>""")
            append("""<button type="submit">Look it up</button></form></section>""")
            append(contact(shop))
        }

        return page(shop, "Check on your repair", body, formHref = null)
    }

    /** A link that doesn't work, a lookup that's been tried too often, and the like */
    fun message(shop: ShopProfile, title: String, text: String, formHref: String?): String {
        val body = """<section class="hero"><h1>${title.esc()}</h1><p>${text.esc()}</p></section>""" + contact(shop)
        return page(shop, title, body, formHref)
    }

    /** The estimate waiting for an answer, with the form to give it, or what they said to the last one */
    private fun estimate(shop: ShopProfile, view: StatusService.View, token: String): String {
        fun money(cents: Long) = Money.format(cents, shop.currencySymbol)

        val waiting = view.estimate
        if (waiting == null) {
            val decision = view.answered?.decision ?: return ""
            val what = if (decision.approved) "approved" else "declined"
            return """<section><h2>Estimate</h2><p>You $what the estimate of <span class="num">${money(decision.amountCents).esc()}</span> on ${formatDate(decision.decidedAt).esc()}.</p></section>"""
        }

        val totals = waiting.totals
        return buildString {
            append("""<section id="estimate"><h2>Estimate: please approve or decline</h2>""")
            if (waiting.note.isNotBlank()) append("""<p class="message">${waiting.note.esc()}</p>""")
            append("""<dl class="rows">""")
            waiting.lines.forEach { line ->
                val quantity = if (line.quantity == 1.0) "" else "${formatQuantity(line.quantity)} x "
                append("""<div><dt>${(quantity + line.description).esc()}</dt><dd class="num">${money(line.totalCents).esc()}</dd></div>""")
            }
            // One row per rate when the shop has more than one, named after its class
            totals.taxLines.filter { it.taxCents > 0 }.forEach { tax ->
                val name = if (totals.taxLines.size > 1) tax.name else "Tax"
                append("""<div><dt>${name.esc()}</dt><dd class="num">${money(tax.taxCents).esc()}</dd></div>""")
            }
            append("""<div class="strong"><dt>Total</dt><dd class="num">${money(totals.totalCents).esc()}</dd></div></dl>""")

            if (view.problem != null) append("""<p class="problem" role="alert">${view.problem.esc()}</p>""")
            append("""<form method="post" action="${token.esc()}/estimate">""")
            append("""<label>Your full name<input name="name" required minlength="${StatusService.MIN_NAME}" maxlength="${StatusService.MAX_NAME}" autocomplete="name"></label>""")
            append("""<p class="muted">Typing your name and pressing Approve is your signature, agreeing to this work and price.</p>""")
            append("""<div class="buttons"><button type="submit" name="decision" value="approve">Approve</button>""")
            append("""<button type="submit" name="decision" value="decline" class="secondary">Decline</button></div></form></section>""")
        }
    }

    private fun moneyRows(shop: ShopProfile, view: StatusService.View): List<Triple<String, String, Boolean>> {
        fun money(cents: Long) = Money.format(cents, shop.currencySymbol)
        val invoice = view.invoice

        return listOfNotNull(
            view.ticket.estimateCents?.let { Triple("Estimate", money(it), false) },
            invoice?.takeIf { it.totalCents > 0 }?.let { Triple("Total so far", money(it.totalCents), false) },
            invoice?.paidCents?.takeIf { it > 0 }?.let { Triple("Paid", money(it), false) },
            invoice?.takeIf { it.totalCents > 0 }?.let { Triple("Balance due", money(it.balanceCents.coerceAtLeast(0)), true) }
        )
    }

    private fun contact(shop: ShopProfile): String {
        val address = listOf(shop.street, listOf(shop.city, shop.state, shop.zip).filter { it.isNotBlank() }.joinToString(" "))
            .filter { it.isNotBlank() }

        val rows = listOfNotNull(
            shop.phone.takeIf { it.isNotBlank() }?.let { """<a href="tel:${it.filter { c -> c.isDigit() || c == '+' }.esc()}">${it.esc()}</a>""" },
            shop.email.takeIf { it.isNotBlank() }?.let { """<a href="mailto:${it.esc()}">${it.esc()}</a>""" },
            shop.website.takeIf { it.isNotBlank() }?.let { site ->
                val href = if (site.startsWith("http://") || site.startsWith("https://")) site else "https://$site"
                """<a href="${href.esc()}" rel="noopener noreferrer">${site.esc()}</a>"""
            },
            address.takeIf { it.isNotEmpty() }?.joinToString("<br>") { it.esc() }
        )

        if (rows.isEmpty()) return ""
        return """<section><h2>Questions? Get in touch</h2><address>${rows.joinToString("") { "<p>$it</p>" }}</address></section>"""
    }

    private fun pill(text: String, tone: StatusTone, large: Boolean = false): String =
        """<p class="pill ${toneClass(tone)}${if (large) " large" else ""}">${text.esc()}</p>"""

    private fun toneClass(tone: StatusTone): String = "tone-${tone.name.lowercase()}"

    private fun page(shop: ShopProfile, title: String, body: String, formHref: String?): String {
        val name = shop.name.ifBlank { "Repair status" }
        val lookup = formHref?.let { """<p><a href="$it">Look up another repair</a></p>""" }.orEmpty()

        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="no-referrer">
<title>${title.esc()}</title>
<style>$STYLE</style>
</head>
<body>
<header><p class="shop">${name.esc()}</p></header>
<main>
$body
</main>
<footer>$lookup<p>This page is only for you. Anyone with the link can see it, so keep it to yourself.</p></footer>
</body>
</html>
"""
    }

    // The app's Bench palette, roughly. Light and dark follow the phone
    private val STYLE = """
:root{--bg:#f6f5f2;--surface:#fff;--text:#1c1d1f;--muted:#6b6e74;--line:#e2e0db;--accent:#2f6fde;
--success:#1f8a4c;--warning:#b86e00;--danger:#c4372e;--neutral:#6b6e74;color-scheme:light dark}
@media (prefers-color-scheme:dark){:root{--bg:#141517;--surface:#1d1f22;--text:#ecedef;--muted:#9a9ea6;--line:#2c2f33;
--accent:#6c9cf0;--success:#4cc27d;--warning:#e9a23b;--danger:#ef6b61;--neutral:#9a9ea6}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
header,main,footer{max-width:640px;margin:0 auto;padding:0 16px}
header{padding-top:20px}.shop{font-weight:700;margin:0}
section{background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:16px 18px;margin:14px 0}
h1{font-size:1.6rem;line-height:1.2;margin:.2em 0 .3em}h2{font-size:1rem;margin:0 0 .6em}
p{margin:.3em 0}.muted{color:var(--muted);font-size:.9rem}
.num{font-family:ui-monospace,"JetBrains Mono",Menlo,Consolas,monospace}
.pill{display:inline-block;border-radius:999px;padding:.15em .8em;font-weight:600;color:var(--tone);
background:color-mix(in srgb,var(--tone) 14%,transparent);margin:.4em 0}
.pill.large{font-size:1.1rem;padding:.3em 1em}
.tone-accent{--tone:var(--accent)}.tone-success{--tone:var(--success)}.tone-warning{--tone:var(--warning)}
.tone-danger{--tone:var(--danger)}.tone-neutral{--tone:var(--neutral)}
.timeline{list-style:none;margin:0;padding:0}
.timeline li{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;padding:.45em 0 .45em 18px;position:relative;border-top:1px solid var(--line)}
.timeline li:first-child{border-top:0}.timeline .label{font-weight:600}
.timeline li::before{content:"";position:absolute;left:0;top:1em;width:9px;height:9px;border-radius:50%;background:var(--tone)}
article{border-top:1px solid var(--line);padding:.6em 0}article:first-of-type{border-top:0;padding-top:0}
.message{white-space:pre-wrap;overflow-wrap:anywhere}
.photos{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
.photos img{width:96px;height:96px;object-fit:cover;border-radius:10px;border:1px solid var(--line);display:block}
.rows{margin:0}.rows div{display:flex;justify-content:space-between;padding:.25em 0}.rows dd{margin:0}
.rows .strong{font-weight:700;border-top:1px solid var(--line);margin-top:.3em;padding-top:.5em}
address{font-style:normal}a{color:var(--accent)}
form{display:grid;gap:14px}label{display:grid;gap:4px;font-weight:600}
input{font:inherit;padding:.6em .7em;border-radius:10px;border:1px solid var(--line);background:var(--bg);color:var(--text)}
button{font:inherit;font-weight:600;padding:.7em;border-radius:10px;border:0;background:var(--accent);color:#fff;cursor:pointer}
.problem{color:var(--danger);font-weight:600}
.buttons{display:flex;gap:10px}.buttons button{flex:1}
button.secondary{background:transparent;color:var(--text);border:1px solid var(--line)}
footer{color:var(--muted);font-size:.85rem;padding-bottom:28px}
""".trimIndent().replace("\n", "")

    /** Escaped for HTML text and quoted attributes */
    private fun String.esc(): String = buildString(length) {
        this@esc.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
