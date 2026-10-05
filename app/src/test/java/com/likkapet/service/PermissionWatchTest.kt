package com.likkapet.service

import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.port.PermissionChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A permission revoked while the service runs reaches the notification (RF-A06). */
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionWatchTest {
    private class FakeChecker : PermissionChecker {
        val granted = AppPermission.entries.toMutableSet()
        override val requiredPermissions = listOf(AppPermission.OVERLAY, AppPermission.USAGE_STATS)

        override fun isGranted(permission: AppPermission) = permission in granted
    }

    @Test
    fun `a revoked permission is reported at the next check and only changes are emitted`() =
        runTest {
            val checker = FakeChecker()
            val seen = mutableListOf<Boolean>()
            backgroundScope.launch { checker.watchAllGranted(periodMs = 30_000).collect { seen += it } }
            runCurrent()

            advanceTimeBy(31_000)
            checker.granted -= AppPermission.USAGE_STATS
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(listOf(true, false), seen)
        }
}
