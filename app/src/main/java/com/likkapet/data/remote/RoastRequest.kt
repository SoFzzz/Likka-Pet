package com.likkapet.data.remote

import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import org.json.JSONObject

/**
 * The body of `POST /roast`, and the only data that ever leaves the phone for the AI (documentación
 * §8, RF-I02): exactly these five fields. [app] is a [TargetApp] on purpose, so an added app can only
 * be sent as `OTHER`: there is no field that could carry its name or package. Adding a field here
 * breaks `RoastRequestPrivacyTest` by design.
 */
data class RoastRequest(
    val app: TargetApp,
    val minutes: Int,
    val angle: Int,
    val level: Int,
    val reason: TriggerReason,
    val lang: String = "es",
) {
    fun toJson(): String =
        JSONObject()
            .put(FIELD_APP, app.name)
            .put(FIELD_MINUTES, minutes)
            .put(FIELD_ANGLE, angle)
            .put(FIELD_LEVEL, level)
            .put(FIELD_REASON, reason.name)
            .put(FIELD_LANG, lang)
            .toString()

    companion object {
        const val FIELD_APP = "app"
        const val FIELD_MINUTES = "minutes"
        const val FIELD_ANGLE = "angle"
        const val FIELD_LEVEL = "level"
        const val FIELD_REASON = "reason"
        const val FIELD_LANG = "lang"
    }
}
