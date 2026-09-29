package com.likkapet.domain.model

/** Watched apps (documentación §3.4); package names map to these in `EscalationConfig.DEFAULT_TARGET_PACKAGES`. */
enum class TargetApp(
    val displayName: String,
) {
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
}
