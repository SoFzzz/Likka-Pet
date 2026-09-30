package com.likkapet.domain

import com.likkapet.domain.model.TargetApp

/** Single source of truth for every threshold and timing (documentación §3.4). */
object EscalationConfig {
    // Posture
    const val POSTURE_DANGER_ANGLE = 45.0 // θ < 45° = text neck
    const val POSTURE_RESET_ANGLE = 55.0 // θ > 55° to forgive (10° hysteresis)
    const val POSTURE_TRIGGER_SEC = 10 // Continuous bad posture before triggering
    const val POSTURE_RESET_SEC = 15 // Continuous good posture before forgiving

    // Owner decision (2026-10-05): straightening the phone does not make an active Likka leave; the
    // POSTURE track ends only with the session or an ejection, so a landscape game or video keeps
    // being interrupted. POSTURE_RESET_ANGLE/SEC (and rules B and C) apply only when this is true.
    const val POSTURE_FORGIVES_GOOD_POSTURE = false

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

    // The farewell animation length that bounds `LikkaOverlayState.isFarewell` (RF-O04), and the
    // pause lengths offered by the dashboard and the notification action (RF-S01, RF-O08).
    const val FAREWELL_SEC = 1
    val PAUSE_OPTIONS_MIN = listOf(15, 30, 60)
    const val NOTIFICATION_PAUSE_MIN = 30

    // How often the usage time kept in memory is written to DataStore (RF-D02), and how often the
    // dashboard re-checks whether paused_until has passed (RF-S01).
    const val USAGE_FLUSH_SEC = 60
    const val PAUSE_EXPIRY_CHECK_SEC = 15

    // Service cadences (phase 3, pending in §3.4): the coordinator is ticked once per second (its
    // documented contract, §3.3); the service looks for a day change at least this often, so a clock
    // or zone change is noticed too; the notification re-reads the permissions this often (RF-A06).
    const val COORDINATOR_TICK_SEC = 1
    const val DAY_CHANGE_CHECK_SEC = 60
    const val PERMISSION_CHECK_SEC = 30

    // Diagnostic heartbeat of the service: readings rate after decimation and CPU-sleep gaps, kept
    // for the on-device checks (Data 2 rates, M1 screen-off survival).
    const val HEARTBEAT_SEC = 60

    // Also pending in §3.4: after an ejection, a watched app still reported in the foreground for
    // longer than this (more than two 2 s poller cycles, so not just a stale poll) means the user
    // never left (HomeLauncher blocked, e.g. by MIUI, or reopened from recents): it counts as a
    // return and rule D applies.
    const val EJECTION_EXIT_TIMEOUT_SEC = 5

    // Phase 4a (owner-approved): after a call ends, the call screen or the VoIP app (WhatsApp) may
    // stay in front for a few polls. For this long, being away from the watched app is still part
    // of the call: nothing runs and Level 3 does not count it as an exit; if the watched app comes
    // back, everything resumes where it was; if the time runs out away, it is a normal exit.
    const val CALL_END_GRACE_SEC = 5

    // Sensor and poller cadences of Data 2.
    // Posture sensors (RF-P01): the period asked from Android is only a hint, so the source decimates
    // by sample timestamp to this period; an event up to the jitter early still counts as on time.
    const val POSTURE_SAMPLE_PERIOD_MS = 200 // 5 Hz
    const val POSTURE_SAMPLE_JITTER_MS = 20
    const val ACCELEROMETER_EMA_ALPHA = 0.15 // Fallback orientation filter without TYPE_GRAVITY (RF-P01)

    // Foreground app poller (RF-A01, RF-A03): the first query also looks back this far to know the
    // app that is already open when the service starts.
    const val FOREGROUND_POLL_SEC = 2
    const val FOREGROUND_INITIAL_LOOKBACK_MIN = 10

    // AI roasts (RF-I03, RF-I05, RF-I06): pool size per key and the Worker's answers' back-offs.
    const val ROAST_POOL_SIZE = 3
    const val ROAST_MAX_WORDS = 25
    const val RATE_LIMIT_BACKOFF_MIN = 10 // After a 429 the app makes no request for this long
    const val ROAST_REQUEST_TIMEOUT_SEC = 8 // The Worker gives DeepSeek 6 s; this covers the extra hop

    // Table detection (§4)
    const val TABLE_MIN_Z = 9.0 // m/s²
    const val TABLE_MAX_ABS_Y = 2.0 // m/s²
    const val TABLE_MAX_STDDEV = 0.05 // m/s², std-dev of |a| over 2 s
    const val TABLE_WINDOW_SAMPLES = 10 // 2 s at 5 Hz

    // Overlay movement (§9.6, RF-O09–RF-O12)
    const val LEVEL_1_HOP_INTERVAL_SEC = 40 // Level 1: seconds between hops to a new edge position
    const val LEVEL_2_WALK_MIN_SEC = 3 // Level 2: minimum seconds per walking segment when space allows
    const val LEVEL_2_STOP_SEC = 5 // Level 2: seconds stopped between walking segments
    const val LEVEL_2_RETURN_DELAY_SEC = 5 // Level 2: seconds after a drag before walking back
    const val MOVEMENT_STEP_FPS = 10 // Movement updates per second (sprite-frame cadence, not 60fps)
    const val POKE_REACTION_TAPS = 3 // Taps within the window below that trigger a "poke" reaction
    const val POKE_REACTION_WINDOW_SEC = 5 // Window to count taps for the "poke" reaction

    // Also pending in §3.4: the Level 2 "central zone" of the content, as the fraction of the safe
    // area (per axis) where the window's center may stop, and the walking step in sprite pixels,
    // kept here so scenario M8 can tune Likka's speed without touching code.
    const val LEVEL_2_CENTER_ZONE_FRACTION = 0.5
    const val LEVEL_2_WALK_STEP_SPRITE_PX = 1

    // Overlay feedback (phase 4a, RF-O02): how long a tap keeps the Level 1 roast unfolded, and the
    // vibration of Level 2 (one pulse) and Level 3 (two pulses with a gap).
    const val LEVEL_1_ROAST_EXPANDED_SEC = 5
    const val LEVEL_2_VIBRATION_MS = 250L
    const val LEVEL_3_VIBRATION_PULSE_MS = 300L
    const val LEVEL_3_VIBRATION_GAP_MS = 150L

    // Phase 4b (RF-O12): how long a local reaction stays in the bubble; a drag one counts from the drop.
    const val LOCAL_REACTION_SEC = 4

    // Default watched apps (user can disable them in Settings). Any package the user adds
    // (RF-S06) is not in this map and is treated as TargetApp.OTHER.
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
