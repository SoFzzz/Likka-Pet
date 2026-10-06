package com.likkapet.domain

/**
 * Whether the foreground service should be (re)started, by the app opening or by the phone booting
 * (documentación §9.3): only once onboarding is done, Likka is switched on and every required
 * permission is granted.
 */
fun shouldStartMonitoring(
    isOnboardingCompleted: Boolean,
    isLikkaEnabled: Boolean,
    hasAllPermissions: Boolean,
): Boolean = isOnboardingCompleted && isLikkaEnabled && hasAllPermissions
