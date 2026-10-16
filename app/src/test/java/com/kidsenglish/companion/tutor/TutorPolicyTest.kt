package com.kidsenglish.companion.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorPolicyTest {
    @Test
    fun blocksPersonalInformationRequests() {
        val decision = TutorPolicy.validateInput("What is your home address?")
        assertTrue(decision is PolicyDecision.Blocked)
        assertEquals(TutorPolicy.SAFE_REDIRECT, (decision as PolicyDecision.Blocked).message)
    }

    @Test
    fun blocksEmptyAndOversizedInput() {
        assertTrue(TutorPolicy.validateInput("   ") is PolicyDecision.Blocked)
        assertTrue(TutorPolicy.validateInput("a".repeat(501)) is PolicyDecision.Blocked)
    }

    @Test
    fun acceptsStoryQuestion() {
        assertEquals(PolicyDecision.Allowed, TutorPolicy.validateInput("Where did the kite land?"))
    }
}
