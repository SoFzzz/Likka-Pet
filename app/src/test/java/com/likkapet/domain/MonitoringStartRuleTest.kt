package com.likkapet.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When opening the app, or the phone booting, restarts the service (documentación §9.3, RNF-F02). */
class MonitoringStartRuleTest {
    @Test
    fun `the service restarts only when everything is in place`() {
        assertTrue(shouldStartMonitoring(isOnboardingCompleted = true, isLikkaEnabled = true, hasAllPermissions = true))
        assertFalse(shouldStartMonitoring(isOnboardingCompleted = false, isLikkaEnabled = true, hasAllPermissions = true))
        assertFalse(shouldStartMonitoring(isOnboardingCompleted = true, isLikkaEnabled = false, hasAllPermissions = true))
        assertFalse(shouldStartMonitoring(isOnboardingCompleted = true, isLikkaEnabled = true, hasAllPermissions = false))
    }
}
