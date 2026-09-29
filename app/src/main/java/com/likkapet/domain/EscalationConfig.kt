package com.likkapet.domain

import com.likkapet.domain.model.TargetApp

/** Single source of truth for every threshold and timing (documentación §3.4). */
object EscalationConfig {
    // Posture
    const val POSTURE_DANGER_ANGLE = 45.0 // θ < 45° = text neck
    const val POSTURE_RESET_ANGLE = 55.0 // θ > 55° to forgive (10° hysteresis)
    const val POSTURE_TRIGGER_SEC = 10 // Continuous bad posture before triggering
    const val POSTURE_RESET_SEC = 15 // Continuous good posture before forgiving

    // Usage time
    const val USAGE_THRESHOLD_MIN = 15 // Session minutes before Level 1
    const val SESSION_END_AWAY_MIN = 5 // Minutes away from watched apps that end a session

    // Escalation (per-reason track, §3.3). Minutes, like the other multi-minute
    // timers below; only LEVEL_3_AUTO_HOME_SEC needs second-level granularity.
    const val LEVEL_1_TO_2_MIN = 2
    const val LEVEL_2_TO_3_MIN = 3
    const val LEVEL_3_AUTO_HOME_SEC = 20

    // Anti-cheat rules (§3.2)
    const val GRACE_AFTER_RESET_SEC = 60 // Rule B
    const val RELAPSE_WINDOW_MIN = 10 // Rule C
    const val QUICK_RETURN_WINDOW_MIN = 5 // Rule D
    const val MAX_PAUSES_PER_DAY = 3 // Rule E

    // Not yet in documentación §3.4 (pending doc update): the farewell animation length that
    // bounds `LikkaOverlayState.isFarewell` (RF-O04), and the pause lengths offered by the
    // dashboard and the notification action (RF-S01, RF-O08).
    const val FAREWELL_SEC = 1
    val PAUSE_OPTIONS_MIN = listOf(15, 30, 60)
    const val NOTIFICATION_PAUSE_MIN = 30

    // Also pending in §3.4: after an ejection, a watched app still reported in the foreground for
    // longer than this (more than two 2 s poller cycles, so not just a stale poll) means the user
    // never left (HomeLauncher blocked, e.g. by MIUI, or reopened from recents): it counts as a
    // return and rule D applies.
    const val EJECTION_EXIT_TIMEOUT_SEC = 5

    // Table detection (§4)
    const val TABLE_MIN_Z = 9.0 // m/s²
    const val TABLE_MAX_ABS_Y = 2.0 // m/s²
    const val TABLE_MAX_STDDEV = 0.05 // m/s², std-dev of |a| over 2 s
    const val TABLE_WINDOW_SAMPLES = 10 // 2 s at 5 Hz

    // Overlay movement (§9.6, RF-O09–RF-O12)
    const val LEVEL_1_HOP_INTERVAL_SEC = 40 // Level 1: seconds between hops to a new edge position
    const val LEVEL_2_WALK_SEC = 4 // Level 2: seconds spent walking per cycle
    const val LEVEL_2_STOP_SEC = 8 // Level 2: seconds stopped over the content's center per cycle
    const val LEVEL_2_RETURN_DELAY_SEC = 5 // Level 2: seconds after a drag before walking back
    const val MOVEMENT_STEP_FPS = 10 // Movement updates per second (sprite-frame cadence, not 60fps)
    const val POKE_REACTION_TAPS = 3 // Taps within the window below that trigger a "poke" reaction
    const val POKE_REACTION_WINDOW_SEC = 5 // Window to count taps for the "poke" reaction

    // Default watched apps (user can disable them in Settings)
    val DEFAULT_TARGET_PACKAGES =
        mapOf(
            "com.zhiliaoapp.musically" to TargetApp.TIKTOK, // TikTok Global
            "com.ss.android.ugc.trill" to TargetApp.TIKTOK, // TikTok Regional
            "com.instagram.android" to TargetApp.INSTAGRAM,
            "com.google.android.youtube" to TargetApp.YOUTUBE,
            "com.facebook.katana" to TargetApp.FACEBOOK,
            "com.facebook.lite" to TargetApp.FACEBOOK,
        )
}
