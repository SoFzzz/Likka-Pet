package com.likkapet.presentation.permissions

import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.port.PermissionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real permission state as the checklist shows it (RF-A04, RF-A06), with a fake checker. */
class PermissionMonitorTest {
    private class FakePermissionChecker(
        override val requiredPermissions: List<AppPermission>,
    ) : PermissionChecker {
        val granted = mutableSetOf<AppPermission>()

        override fun isGranted(permission: AppPermission) = permission in granted
    }

    private val belowApi33 = listOf(AppPermission.OVERLAY, AppPermission.USAGE_STATS)
    private val checker = FakePermissionChecker(belowApi33)
    private val monitor = PermissionMonitor(checker)

    private fun statusOf(permission: AppPermission) =
        monitor.permissions.value
            .first { it.permission == permission }
            .status

    @Test
    fun `the checklist shows only the permissions this Android version asks for`() {
        assertEquals(belowApi33, monitor.permissions.value.map { it.permission })
        assertEquals(
            AppPermission.entries,
            PermissionMonitor(FakePermissionChecker(AppPermission.entries)).permissions.value.map { it.permission },
        )
    }

    @Test
    fun `a permission not yet asked for is pending, and denied after a failed attempt`() {
        assertEquals(PermissionStatus.PENDING, statusOf(AppPermission.OVERLAY))

        monitor.markRequested(AppPermission.OVERLAY)
        monitor.refresh()

        assertEquals(PermissionStatus.DENIED, statusOf(AppPermission.OVERLAY))
        assertEquals(PermissionStatus.PENDING, statusOf(AppPermission.USAGE_STATS))
    }

    @Test
    fun `granting in system settings shows up on the next refresh`() {
        checker.granted += belowApi33
        assertFalse(monitor.hasAllPermissions)

        monitor.refresh()

        assertTrue(monitor.hasAllPermissions)
    }

    @Test
    fun `a permission revoked from the system is missing again after a refresh`() {
        checker.granted += belowApi33
        monitor.refresh()
        checker.granted -= AppPermission.USAGE_STATS

        monitor.refresh()

        assertFalse(monitor.hasAllPermissions)
        assertEquals(PermissionStatus.PENDING, statusOf(AppPermission.USAGE_STATS))
    }

    @Test
    fun `the status mapping`() {
        assertEquals(PermissionStatus.GRANTED, permissionStatus(isGranted = true, wasRequested = true))
        assertEquals(PermissionStatus.DENIED, permissionStatus(isGranted = false, wasRequested = true))
        assertEquals(PermissionStatus.PENDING, permissionStatus(isGranted = false, wasRequested = false))
    }
}
