package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr

/**
 * The Payments tab: how the shop takes cards through a processor, its readers, and every charge sent
 * @param onSaved Run after the settings are saved, so the counter offers the readers straight away
 */
class AdminPayments(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val onSaved: () -> Unit
) {
    private val payments get() = remoteHandler.paymentRepo

    private val _settings = MutableStateFlow<PaymentSettings?>(null)
    /** Null until loaded, or when it couldn't be */
    val settings = _settings.asStateFlow()

    private val _accountReaders = MutableStateFlow<List<CardReader>?>(null)
    /** The readers on the processor account, once asked for */
    val accountReaders = _accountReaders.asStateFlow()

    private val _charges = MutableStateFlow<List<CardCharge>>(emptyList())
    val charges = _charges.asStateFlow()

    private val _chargesEnd = MutableStateFlow(false)
    val chargesEnd = _chargesEnd.asStateFlow()

    suspend fun reload() {
        _settings.value = payments.settings().valueOr { error ->
            support.notify("Couldn't load the payment settings: ${error.message}")
            null
        }
        loadCharges(reset = true)
    }

    /** The charges only, for the periodic check, so settings being edited aren't replaced */
    suspend fun reloadCharges() = loadCharges(reset = true)

    fun loadMore() {
        support.scope.launch { loadCharges(reset = false) }
    }

    private suspend fun loadCharges(reset: Boolean) {
        val offset = if (reset) 0L else _charges.value.size.toLong()
        val page = payments.charges(offset, PAGE_SIZE).valueOr { error ->
            support.notify("Couldn't load the card charges: ${error.message}")
            return
        }

        _charges.value = if (reset) page else _charges.value + page
        _chargesEnd.value = page.size < PAGE_SIZE
    }

    fun save(settings: PaymentSettings) {
        support.scope.launch {
            support.loading {
                when (val result = payments.saveSettings(settings)) {
                    is TmResult.Success -> {
                        _settings.value = result.data
                        support.notify("Payment settings saved")
                        onSaved()
                    }

                    is TmResult.Error -> support.notify(result.error.message)
                }
                support.auditChanged()
            }
        }
    }

    /** Asks the processor for the account's readers, with the saved keys */
    fun findReaders() {
        support.scope.launch {
            support.loading {
                when (val result = payments.accountReaders()) {
                    is TmResult.Success -> {
                        _accountReaders.value = result.data
                        if (result.data.isEmpty()) support.notify("There are no readers on the account yet")
                    }

                    is TmResult.Error -> support.notify("Couldn't list the readers: ${result.error.message}")
                }
            }
        }
    }

    /** Gives back a charge that was paid but never put on an invoice */
    fun refund(charge: CardCharge, reason: String) {
        support.scope.launch {
            when (val result = payments.refund(charge.identifier, reason)) {
                is TmResult.Success -> {
                    _charges.value = _charges.value.map { if (it.identifier == charge.identifier) result.data else it }
                    support.notify("Refunded onto the card")
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
            support.auditChanged()
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
