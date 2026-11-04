package com.kidsenglish.companion.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceCallbackRouterTest {
    @Test
    fun lateCompletionFromReplacedUtteranceCannotInvokeCurrentCallbacks() {
        val router = UtteranceCallbackRouter()
        val events = mutableListOf<String>()
        val first = router.register({ events += "A done" }, { events += "A error: $it" })
        val second = router.register({ events += "B done" }, { events += "B error: $it" })

        router.onDone(first)
        assertTrue(events.isEmpty())
        router.onDone(second)
        assertEquals(listOf("B done"), events)
    }

    @Test
    fun lateErrorFromReplacedUtteranceCannotInvokeCurrentCallbacks() {
        val router = UtteranceCallbackRouter()
        val events = mutableListOf<String>()
        val first = router.register({ events += "A done" }, { events += "A error: $it" })
        val second = router.register({ events += "B done" }, { events += "B error: $it" })

        router.onError(first, "old engine error")
        assertTrue(events.isEmpty())
        router.onError(second, "current engine error")
        assertEquals(listOf("B error: current engine error"), events)
    }

    @Test
    fun clearInvalidatesOldCallbacksAndNeverReusesTheirIdentity() {
        val router = UtteranceCallbackRouter()
        val events = mutableListOf<String>()
        val first = router.register({ events += "A done" }, { events += "A error" })
        router.clear()
        router.onDone(first)
        router.onError(first, "stopped")
        assertTrue(events.isEmpty())

        val second = router.register({ events += "B done" }, { events += "B error" })
        assertNotEquals(first, second)
        router.onDone(first)
        router.onError(first, "late stop")
        assertTrue(events.isEmpty())
        router.onDone(second)
        assertEquals(listOf("B done"), events)
    }

    @Test
    fun unknownAndMissingEventIdsCannotConsumeCurrentUtterance() {
        val router = UtteranceCallbackRouter()
        val events = mutableListOf<String>()
        val id = router.register({ events += "done" }, { events += "error" })
        router.onDone(null)
        router.onError(null, "missing identity")
        router.onDone("not-a-registered-utterance")
        router.onError("not-a-registered-utterance", "unknown identity")
        assertTrue(events.isEmpty())
        router.onDone(id)
        assertEquals(listOf("done"), events)
    }

    @Test
    fun terminalEventIsConsumedBeforeCallbackCanRegisterNextUtterance() {
        val router = UtteranceCallbackRouter()
        val events = mutableListOf<String>()
        var second: String? = null
        val first = router.register(
            onDone = {
                events += "A done"
                second = router.register({ events += "B done" }, { events += "B error" })
            },
            onError = { events += "A error" },
        )
        router.onDone(first)
        router.onDone(first)
        router.onError(first, "late duplicate")
        assertEquals(listOf("A done"), events)
        router.onDone(second)
        assertEquals(listOf("A done", "B done"), events)
    }

    @Test
    fun duplicateErrorAndCompletionEventsDeliverOnlyFirstTerminalResult() {
        val router = UtteranceCallbackRouter()
        val events = mutableListOf<String>()
        val id = router.register({ events += "done" }, { events += it })
        router.onError(id, "first error")
        router.onError(id, "second error")
        router.onDone(id)
        assertEquals(listOf("first error"), events)
    }
}
