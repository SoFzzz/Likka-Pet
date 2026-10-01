package com.likkapet.presentation.permissions

import androidx.compose.runtime.Immutable
import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.port.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How a permission card looks (design system §1.8): the system only says granted or not. */
enum class PermissionStatus { PENDING, GRANTED, DENIED }

@Immutable
data class PermissionUi(
    val permission: AppPermission,
    val status: PermissionStatus,
)

/** Not granted after the user already tried from this screen means "No concedido"; before that, "Pendiente". */
fun permissionStatus(
    isGranted: Boolean,
    wasRequested: Boolean,
): PermissionStatus =
    when {
        isGranted -> PermissionStatus.GRANTED
        wasRequested -> PermissionStatus.DENIED
        else -> PermissionStatus.PENDING
    }

/**
 * App-wide view of the required permissions (RF-A04, RF-A06), shared by onboarding, "Revisar
 * permisos" and the dashboard. MainActivity calls [refresh] on every resume, so coming back from a
 * system settings screen, or revoking a permission there, shows up at once. Main thread only.
 */
class PermissionMonitor(
    private val checker: PermissionChecker,
) {
    private var requested = emptySet<AppPermission>()
    private val mutablePermissions = MutableStateFlow(read())
    val permissions: StateFlow<List<PermissionUi>> = mutablePermissions.asStateFlow()

    val hasAllPermissions: Boolean get() = permissions.value.all { it.status == PermissionStatus.GRANTED }

    fun refresh() {
        mutablePermissions.value = read()
    }

    /** Remembers the attempt; the card only turns "No concedido" after the next [refresh]. */
    fun markRequested(permission: AppPermission) {
        requested = requested + permission
    }

    private fun read(): List<PermissionUi> =
        checker.requiredPermissions.map { permission ->
            PermissionUi(permission, permissionStatus(checker.isGranted(permission), permission in requested))
        }
}
