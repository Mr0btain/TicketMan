package org.mr0btain.ticketman.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.*
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceMath
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.uikit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.invoices.RefundDialog
import org.mr0btain.ticketman.ui.screens.counter.PaymentPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A refund of a card payment moves real money, so it has to name the payment, and only admins can take one off */
@OptIn(ExperimentalTestApi::class)
class CardRefundUiTest {
    private val onCard = Payment(method = PaymentMethod.CARD, amountCents = 5_000, processor = PaymentProvider.STRIPE, processorReference = "pi_1")
    private val inCash = Payment(method = PaymentMethod.CASH, amountCents = 1_000)

    private fun ComposeUiTest.refund(invoice: Invoice): RefundRequest? {
        var sent: RefundRequest? = null
        setContent {
            TmTheme {
                RefundDialog(invoice = invoice, onRefund = { request, _ -> sent = request }, onDismissRequest = {})
            }
        }
        // The amount, then the reason
        onAllNodes(hasSetTextAction())[1].performTextInput("Faulty")
        onNode(hasClickAction() and hasText("Refund", substring = true)).performClick()
        waitForIdle()
        return sent
    }

    @Test
    fun aCardRefundGoesBackOntoThePayment() = runComposeUiTest {
        val request = refund(Invoice(status = InvoiceStatus.PAID, payments = listOf(onCard)))

        assertEquals(onCard.identifier, request?.paymentIdentifier)
        assertEquals(PaymentMethod.CARD, request?.method)
        assertEquals(5_000, request?.amountCents)
    }

    @Test
    fun aVoidInvoiceRefundsTheSameWay() = runComposeUiTest {
        assertEquals(onCard.identifier, refund(Invoice(status = InvoiceStatus.VOID, payments = listOf(onCard)))?.paymentIdentifier)
    }

    @Test
    fun cashIsntSentToTheProcessor() = runComposeUiTest {
        assertNull(refund(Invoice(status = InvoiceStatus.PAID, payments = listOf(inCash)))?.paymentIdentifier)
    }

    private fun ComposeUiTest.panel(canRefundCards: Boolean) = setContent {
        TmTheme {
            PaymentPanel(
                totals = InvoiceMath.totals(emptyList(), 0, 0),
                taxLabel = "Tax",
                taken = listOf(onCard, inCash),
                onRemoveTaken = {},
                canRemoveTaken = { canRefundCards || it.processor == null },
                pending = mutableStateListOf<PaymentRequest>(),
                problem = null,
                finishText = "Finish",
                finishEnabled = true,
                busy = false,
                onFinish = {}
            )
        }
    }

    @Test
    fun onlyCashCanBeTakenOffWithoutAdmin() = runComposeUiTest {
        panel(canRefundCards = false)
        onAllNodesWithContentDescription("Remove payment").assertCountEquals(1)
    }

    @Test
    fun anAdminCanTakeEitherOff() = runComposeUiTest {
        panel(canRefundCards = true)
        onAllNodesWithContentDescription("Remove payment").assertCountEquals(2)
    }
}
