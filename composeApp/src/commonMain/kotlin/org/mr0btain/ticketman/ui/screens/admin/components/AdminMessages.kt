package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.notify.NotificationChannel
import org.mr0btain.ticketman.data.notify.NotificationSettings
import org.mr0btain.ticketman.data.notify.OutboxMessage
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.server.request.TestMessageRequest

/**
 * The Messages tab: how the shop sends messages to customers, and every message sent
 */
class AdminMessages(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport
) {
    private val notify get() = remoteHandler.notifyRepo

    private val _settings = MutableStateFlow<NotificationSettings?>(null)
    /** Null until loaded, or when it couldn't be */
    val settings = _settings.asStateFlow()

    private val _outbox = MutableStateFlow<List<OutboxMessage>>(emptyList())
    val outbox = _outbox.asStateFlow()

    private val _outboxEnd = MutableStateFlow(false)
    val outboxEnd = _outboxEnd.asStateFlow()

    suspend fun reload() {
        _settings.value = notify.settings().valueOr { error ->
            support.notify("Couldn't load the message settings: ${error.message}")
            null
        }
        loadOutbox(reset = true)
    }

    /** The outbox only, for the periodic check, so settings being edited aren't replaced */
    suspend fun reloadOutbox() = loadOutbox(reset = true)

    fun loadMore() {
        support.scope.launch { loadOutbox(reset = false) }
    }

    private suspend fun loadOutbox(reset: Boolean) {
        val offset = if (reset) 0L else _outbox.value.size.toLong()
        val page = notify.outbox(offset, PAGE_SIZE).valueOr { error ->
            support.notify("Couldn't load the messages: ${error.message}")
            return
        }

        _outbox.value = if (reset) page else _outbox.value + page
        _outboxEnd.value = page.size < PAGE_SIZE
    }

    fun save(settings: NotificationSettings) {
        support.scope.launch {
            support.loading {
                when (val result = notify.saveSettings(settings)) {
                    is TmResult.Success -> {
                        _settings.value = result.data
                        support.notify("Message settings saved")
                        loadOutbox(reset = true)
                    }

                    is TmResult.Error -> support.notify(result.error.message)
                }
                support.auditChanged()
            }
        }
    }

    /** Sends a test with the settings as they are on screen, saved or not */
    fun test(settings: NotificationSettings, channel: NotificationChannel, recipient: String) {
        support.scope.launch {
            support.loading {
                when (val result = notify.test(TestMessageRequest(settings, channel, recipient.trim()))) {
                    is TmResult.Success -> support.notify("Test sent to ${recipient.trim()}")
                    is TmResult.Error -> support.notify("Test didn't go: ${result.error.message}")
                }
            }
        }
    }

    fun retry(message: OutboxMessage) = change(message) { notify.retry(it.identifier) }

    fun cancel(message: OutboxMessage) = change(message) { notify.cancel(it.identifier) }

    private fun change(message: OutboxMessage, request: suspend (OutboxMessage) -> TmResult<OutboxMessage, *>) {
        support.scope.launch {
            when (val result = request(message)) {
                is TmResult.Success -> _outbox.value = _outbox.value.map { if (it.identifier == message.identifier) result.data else it }
                is TmResult.Error -> support.notify(result.error.message)
            }
            support.auditChanged()
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
