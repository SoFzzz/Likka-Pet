package com.likkapet.data.reaction

import com.likkapet.domain.model.LocalReaction
import com.likkapet.domain.port.ReactionPhrases
import org.json.JSONObject
import kotlin.random.Random

/**
 * The phrases of `assets/reactions.json` (RF-O12), one list per [LocalReaction.key]. No network and
 * no AI. Within a key a phrase is not repeated until all of them were used, like [com.likkapet.data.roast.FallbackRoasts].
 */
class LocalReactionPhrases(
    json: String,
    private val random: Random = Random.Default,
) : ReactionPhrases {
    private val phrases: Map<LocalReaction, List<String>> = parse(json)
    private val unused = mutableMapOf<LocalReaction, MutableList<String>>()

    @Synchronized
    override fun next(reaction: LocalReaction): String {
        val all = requireNotNull(phrases[reaction]) { "No reactions for ${reaction.key}" }
        val remaining = unused.getOrPut(reaction) { all.shuffled(random).toMutableList() }
        val phrase = remaining.removeFirst()
        if (remaining.isEmpty()) unused.remove(reaction)
        return phrase
    }

    private fun parse(json: String): Map<LocalReaction, List<String>> {
        val root = JSONObject(json)
        return LocalReaction.entries.associateWith { reaction ->
            val array = root.getJSONArray(reaction.key)
            List(array.length()) { array.getString(it) }
        }
    }

    companion object {
        const val SPANISH_ASSET = "reactions.json"
        const val ENGLISH_ASSET = "reactions_en.json"

        /** English phrases for an English UI language; Spanish, the app's own language, for every other one. */
        fun assetNameFor(language: String): String = if (language == "en") ENGLISH_ASSET else SPANISH_ASSET
    }
}
