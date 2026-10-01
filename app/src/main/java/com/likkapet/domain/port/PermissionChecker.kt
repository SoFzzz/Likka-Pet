package com.likkapet.domain.port

import com.likkapet.domain.model.AppPermission

/** Reads the real permission state from the system (RF-A04); implemented in data/. */
interface PermissionChecker {
    /** The permissions this Android version asks for: notifications only on API 33+. */
    val requiredPermissions: List<AppPermission>

    fun isGranted(permission: AppPermission): Boolean
}
