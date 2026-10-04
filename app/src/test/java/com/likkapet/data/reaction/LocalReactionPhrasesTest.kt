package com.likkapet.data.reaction

import com.likkapet.domain.model.LocalReaction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * RF-O12 and §12.1: the shipped `reactions.json` has the keys `drag` and `poke`, at least 7 phrases
 * each, all of them 12 words or fewer, and phrases do not repeat within a round. Like
 * `FallbackRoastsTest` for `roasts_fallback.json`. Spanish only: `reactions_en.json` comes with RF-S09.
 */
class LocalReactionPhrasesTest {
    private val json = File("src/main/assets/${LocalReactionPhrases.SPANISH_ASSET}").readText()
    private val root = JSONObject(json)

    private fun phrases(key: String): List<String> {
        val array = root.getJSONArray(key)
        return List(array.length()) { array.getString(it) }
    }

    private val all get() = LocalReaction.entries.flatMap { phrases(it.key) }

    @Test
    fun `it has the keys drag and poke and nothing else`() {
        assertEquals(setOf("drag", "poke"), root.keys().asSequence().toSet())
        assertEquals(setOf("drag", "poke"), LocalReaction.entries.map { it.key }.toSet())
    }

    @Test
    fun `every key has at least seven phrases`() {
        LocalReaction.entries.forEach { reaction ->
            assertTrue("${reaction.key} has ${phrases(reaction.key).size}", phrases(reaction.key).size >= 7)
        }
    }

    @Test
    fun `no phrase is over 12 words or empty`() {
        all.forEach { phrase ->
            val words = phrase.trim().split(Regex("\\s+")).size
            assertTrue("'$phrase' has $words words", phrase.isNotBlank() && words <= 12)
        }
    }

    @Test
    fun `no phrase repeats inside a key or across them`() {
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `phrases never name an app`() {
        val appNames = listOf("TikTok", "Instagram", "YouTube", "Facebook")
        all.forEach { phrase -> assertTrue("'$phrase' names an app", appNames.none { phrase.contains(it, ignoreCase = true) }) }
    }

    // Design system §1.9: the person's gender is never marked ("encorvado", "pegado", "campeón"...).
    @Test
    fun `phrases do not mark the person's gender with the usual words`() {
        val gendered = Regex("\\b(encorvad[oa]s?|pegad[oa]s?|campe[oó]na?|tranquil[oa]|cansad[oa]|amig[oa]|humano|humana|list[oa])\\b")
        all.forEach { phrase -> assertTrue("'$phrase' marks a gender", !gendered.containsMatchIn(phrase.lowercase())) }
    }

    @Test
    fun `a round serves every phrase of a key once before repeating`() {
        val reactions = LocalReactionPhrases(json, Random(7))
        val round = phrases(LocalReaction.POKE.key).size

        val served = List(round) { reactions.next(LocalReaction.POKE) }

        assertEquals(phrases(LocalReaction.POKE.key).toSet(), served.toSet())
    }

    @Test
    fun `each key draws from its own phrases`() {
        val reactions = LocalReactionPhrases(json, Random(3))

        assertTrue(reactions.next(LocalReaction.DRAG) in phrases("drag"))
        assertTrue(reactions.next(LocalReaction.POKE) in phrases("poke"))
    }

    @Test
    fun `the English file has the same structure and limits`() {
        val englishJson = File("src/main/assets/${LocalReactionPhrases.ENGLISH_ASSET}").readText()
        val englishRoot = JSONObject(englishJson)
        assertEquals(setOf("drag", "poke"), englishRoot.keys().asSequence().toSet())
        LocalReaction.entries.forEach { reaction ->
            val array = englishRoot.getJSONArray(reaction.key)
            assertTrue("${reaction.key} has ${array.length()}", array.length() >= 7)
            repeat(array.length()) { index ->
                val phrase = array.getString(index)
                val words = phrase.trim().split(Regex("\\s+")).size
                assertTrue("'$phrase' has $words words", phrase.isNotBlank() && words <= 12)
            }
        }
        val englishReactions = LocalReactionPhrases(englishJson, Random(1))
        assertTrue(englishReactions.next(LocalReaction.DRAG).isNotBlank())
        assertTrue(englishReactions.next(LocalReaction.POKE).isNotBlank())
    }

    @Test
    fun `the file follows the resolved UI language`() {
        assertEquals(LocalReactionPhrases.ENGLISH_ASSET, LocalReactionPhrases.assetNameFor("en"))
        assertEquals(LocalReactionPhrases.SPANISH_ASSET, LocalReactionPhrases.assetNameFor("es"))
        assertEquals(LocalReactionPhrases.SPANISH_ASSET, LocalReactionPhrases.assetNameFor("fr"))
    }
}
