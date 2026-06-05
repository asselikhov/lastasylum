package com.lastasylum.alliance.ui.chat.usecase

/**
 * Send policy constants extracted from [com.lastasylum.alliance.ui.chat.ChatViewModel]
 * and [com.lastasylum.alliance.data.chat.ChatRestRepository].
 */
internal object ChatSendUseCase {
    const val MAX_SEND_ATTEMPTS = 3
    const val SEND_RETRY_BACKOFF_MS = 350L

    fun shouldRetrySend(attempt: Int, error: Throwable?): Boolean {
        if (attempt >= MAX_SEND_ATTEMPTS) return false
        if (error == null) return false
        return error !is kotlinx.coroutines.CancellationException
    }
}
