package com.kidsenglish.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigurationTest {
    @Test
    fun supportedAgeBandsAreStable() {
        assertEquals(listOf("6-8", "9-10", "11-12"), BuildContract.AGE_BANDS)
    }
}
