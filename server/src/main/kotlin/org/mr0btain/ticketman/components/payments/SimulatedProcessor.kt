package org.mr0btain.ticketman.components.payments

import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader

/**
 * Pretends to take cards, for a server in development mode: trying the counter out, the tests and
 * scripts/counter_e2e.py. A reader is paid [READER_DELAY_MILLIS] after it's sent, unless the amount ends in .02,
 * which is declined like Stripe's test card. A link is paid as soon as it's checked, and sends the customer
 * straight back
 */
class SimulatedProcessor(private val now: () -> Long = { System.currentTimeMillis() }) : PaymentProcessor {
    override fun readers(): ProcessorResult<List<CardReader>> =
        ProcessorResult.Ok(listOf(CardReader("sim_reader_1", "Simulated reader"), CardReader("sim_reader_2", "Simulated reader 2")))

    override fun startReader(charge: CardCharge, description: String): ProcessorResult<String> =
        ProcessorResult.Ok("sim_pi_${charge.identifier}")

    override fun createLink(charge: CardCharge, description: String, returnUrl: String): ProcessorResult<StartedLink> =
        ProcessorResult.Ok(StartedLink("sim_cs_${charge.identifier}", returnUrl))

    override fun check(charge: CardCharge): ProcessorResult<ChargeState> = ProcessorResult.Ok(
        when {
            charge.kind == CardChargeKind.LINK -> ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "sim_pay_${charge.identifier}")
            now() - charge.createdAt < READER_DELAY_MILLIS -> ChargeState(CardChargeStatus.PENDING)
            charge.amountCents % 100 == DECLINED_CENTS -> ChargeState(CardChargeStatus.FAILED, error = "Card declined (simulated, amounts ending in .02 are)")
            else -> ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "sim_pay_${charge.identifier}")
        }
    )

    override fun cancel(charge: CardCharge): ProcessorResult<Unit> = ProcessorResult.Ok(Unit)

    override fun refund(paymentReference: String, amountCents: Long, key: String): ProcessorResult<String> =
        ProcessorResult.Ok("sim_re_$key")

    override fun webhook(header: (String) -> String?, body: String, url: String): WebhookResult = WebhookResult.Refused

    companion object {
        const val READER_DELAY_MILLIS = 2_000L
        const val DECLINED_CENTS = 2L
    }
}
