package com.kidsenglish.companion.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun releaseEndpointRequiresHttps() {
        assertFalse(EndpointPolicy.isAllowed("http://example.com/v1", isDebug = false, allowDebugHttp = false))
        assertTrue(EndpointPolicy.isAllowed("https://example.com/v1", isDebug = false, allowDebugHttp = false))
    }

    @Test
    fun debugEndpointAllowsPrivateLanOnlyWhenEnabled() {
        assertTrue(EndpointPolicy.isAllowed("http://192.168.1.20:11434/v1", true, true))
        assertTrue(EndpointPolicy.isAllowed("http://10.0.2.2:11434/v1", true, true))
        assertFalse(EndpointPolicy.isAllowed("http://example.com/v1", true, true))
        assertFalse(EndpointPolicy.isAllowed("http://192.168.1.20:11434/v1", true, false))
    }

    @Test
    fun rejectsCredentialsFragmentsAndOtherSchemes() {
        assertFalse(EndpointPolicy.isAllowed("https://name:pass@example.com/v1", false, false))
        assertFalse(EndpointPolicy.isAllowed("https://example.com/v1#secret", false, false))
        assertFalse(EndpointPolicy.isAllowed("ftp://example.com/v1", false, false))
    }
}
