package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentOptions
import org.mr0btain.ticketman.data.notify.NotificationChannel
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.server.request.PaymentLinkRequest
import org.mr0btain.ticketman.server.request.ReaderChargeRequest
import kotlin.uuid.Uuid

/**
 * Cards through the shop's processor: an amount sent to a reader and waited on, and payment links to show the
 * customer. The server asks the processor each time a charge is looked at, so waiting is polling
 */
class CounterCards(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport
) {
    private val payments get() = remoteHandler.paymentRepo

    private val _options = MutableStateFlow(PaymentOptions())
    /** What the shop has set up, no readers or links until it has */
    val options = _options.asStateFlow()

    private val _active = MutableStateFlow<CardCharge?>(null)
    /** The reader charge on screen: waiting for the card, or how it ended until it's closed */
    val active = _active.asStateFlow()

    private val _link = MutableStateFlow<CardCharge?>(null)
    /** A payment link being shown */
    val link = _link.asStateFlow()

    private var waiting: Job? = null

    fun refreshOptions() {
        support.scope.launch { payments.options().valueOr { null }?.let { _options.value = it } }
    }

    /**
     * Sends [amountCents] with [tipCents] on top to [reader] and waits for the card (the server adds any surcharge)
     * @param invoiceIdentifier The open invoice it pays, where the server puts it. Null for a sale, whose payment
     * then names the charge
     * @param onPaid Run once the card went through
     */
    fun charge(reader: CardReader, amountCents: Long, tipCents: Long, invoiceIdentifier: Uuid?, onPaid: (CardCharge) -> Unit) {
        if (!support.canEdit() || waiting?.isActive == true) return

        waiting = support.scope.launch {
            val started = payments.startReader(ReaderChargeRequest(reader.identifier, amountCents, invoiceIdentifier, tipCents)).valueOr { error ->
                support.notify("${reader.name} didn't start: ${error.message}")
                return@launch
            }
            _active.value = started

            var charge = started
            while (charge.status == CardChargeStatus.PENDING) {
                delay(POLL_MILLIS)
                // A blip while waiting doesn't stop it, the server keeps following the charge
                charge = payments.charge(charge.identifier).valueOr { charge }
                if (_active.value?.identifier == charge.identifier) _active.value = charge else return@launch
            }

            if (charge.status == CardChargeStatus.SUCCEEDED) {
                onPaid(charge)
                _active.value = null
            }
        }
    }

    /** Stops the reader waiting. The card may still have gone through, then it's kept */
    fun cancel() {
        val charge = _active.value ?: return

        support.scope.launch {
            when (val result = payments.cancel(charge.identifier)) {
                is TmResult.Success -> _active.value = result.data
                // Most likely paid just before, which the wait picks up
                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** Closes a reader charge that ended without being paid */
    fun dismiss() {
        if (_active.value?.status == CardChargeStatus.PENDING) return
        _active.value = null
    }

    /** Makes, or hands out again, a payment link for what's owed on [invoiceIdentifier] */
    fun showLink(invoiceIdentifier: Uuid) {
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = payments.link(PaymentLinkRequest(invoiceIdentifier))) {
                is TmResult.Success -> _link.value = result.data
                is TmResult.Error -> support.notify("No payment link: ${result.error.message}")
            }
        }
    }

    /** Emails or texts the payment link being shown to the customer */
    fun sendLink(channel: NotificationChannel) {
        val charge = _link.value ?: return
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = payments.sendLink(charge.identifier, setOf(channel))) {
                is TmResult.Success -> support.notify("Payment link sent to ${result.data.joinToString(" and ") { it.recipient }}")
                is TmResult.Error -> support.notify("Not sent: ${result.error.message}")
            }
        }
    }

    fun closeLink() {
        _link.value = null
    }

    private companion object {
        const val POLL_MILLIS = 1_500L
    }
}
