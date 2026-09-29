package com.likkapet.domain.model

/**
 * Watched apps (documentación §3.4); package names map to these in `EscalationConfig.DEFAULT_TARGET_PACKAGES`.
 * [OTHER] stands for every app the user adds (RF-S06): it is the only value ever sent to the Worker
 * for those apps, never their name or package (§8). The UI never shows its [displayName]: an added
 * app is listed with its own launcher label.
 */
enum class TargetApp(
    val displayName: String,
) {
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    OTHER("Otra app"),
    ;

    companion object {
        /** The four apps watched by default, in Settings order (RF-A02). */
        val DEFAULTS: List<TargetApp> = entries.filter { it != OTHER }
    }
}
