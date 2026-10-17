package com.kidsenglish.companion.speech

internal class UtteranceCallbackRouter {
    private data class Pending(
        val id: String,
        val onDone: () -> Unit,
        val onError: (String) -> Unit,
    )

    private var nextId = 0L
    private var pending: Pending? = null

    @Synchronized
    fun register(onDone: () -> Unit, onError: (String) -> Unit): String {
        val id = "speech-${++nextId}"
        pending = Pending(id, onDone, onError)
        return id
    }

    fun onDone(utteranceId: String?) {
        take(utteranceId)?.onDone?.invoke()
    }

    fun onError(utteranceId: String?, message: String) {
        take(utteranceId)?.onError?.invoke(message)
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    @Synchronized
    private fun take(utteranceId: String?): Pending? {
        val current = pending ?: return null
        if (current.id != utteranceId) return null
        pending = null
        return current
    }
}
