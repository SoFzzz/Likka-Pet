package com.likkapet.domain.model

/**
 * What one poll of the foreground-app source saw: the watched app on screen (null when it is not
 * a watched one) and whether a call is going on. An added app (RF-S06) arrives as [TargetApp.OTHER].
 */
data class ForegroundState(
    val app: TargetApp?,
    val isInCall: Boolean,
)
