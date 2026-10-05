package com.likkapet.data.roast

import com.likkapet.domain.model.TriggerReason
import org.json.JSONObject
import kotlin.random.Random

/**
 * Local roasts from `assets/roasts_fallback.json` (RF-I08), used when AI is off, the Worker fails or
 * the pool is empty. They are never tied to an app or to real numbers, so any of them fits any
 * moment of its level and reason. Within a key a phrase is not repeated until all of them were used.
 */
class FallbackRoasts(
    json: String,
    private val random: Random = Random.Default,
) {
    private val phrases: Map<Pair<Int, TriggerReason>, List<String>> = parse(json)
    private val unused = mutableMapOf<Pair<Int, TriggerReason>, MutableList<String>>()

    @Synchronized
    fun next(
        level: Int,
        reason: TriggerReason,
    ): String {
        val key = level to reason
        val all = requireNotNull(phrases[key]) { "No fallback roasts for L$level $reason" }
        val remaining = unused.getOrPut(key) { all.shuffled(random).toMutableList() }
        val roast = remaining.removeFirst()
        if (remaining.isEmpty()) unused.remove(key)
        return roast
    }

    private fun parse(json: String): Map<Pair<Int, TriggerReason>, List<String>> {
        val root = JSONObject(json)
        return buildMap {
            for (level in LEVELS) {
                val byReason = root.getJSONObject("L$level")
                for (reason in TriggerReason.entries) {
                    val array = byReason.getJSONArray(reason.name)
                    put(level to reason, List(array.length()) { array.getString(it) })
                }
            }
        }
    }

    companion object {
        val LEVELS = 1..3
        const val SPANISH_ASSET = "roasts_fallback.json"
        const val ENGLISH_ASSET = "roasts_fallback_en.json"

        /** English phrases for an English UI language; Spanish, the app's own language, for every other one. */
        fun assetNameFor(language: String): String = if (language == "en") ENGLISH_ASSET else SPANISH_ASSET
    }
}
