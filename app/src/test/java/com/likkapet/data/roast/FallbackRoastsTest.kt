package com.likkapet.data.roast

import com.likkapet.domain.model.TriggerReason
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** RF-I08: the shipped `roasts_fallback.json` has the right shape, and phrases do not repeat within a round. */
class FallbackRoastsTest {
    private val json = File("src/main/assets/${FallbackRoasts.SPANISH_ASSET}").readText()
    private val root = JSONObject(json)

    private fun phrases(
        level: Int,
        reason: TriggerReason,
    ): List<String> {
        val array = root.getJSONObject("L$level").getJSONArray(reason.name)
        return List(array.length()) { array.getString(it) }
    }

    private val combinations = (1..3).flatMap { level -> TriggerReason.entries.map { level to it } }

    @Test
    fun `it has the six keys L1 to L3 by POSTURE and USAGE_TIME and nothing else`() {
        assertEquals(setOf("L1", "L2", "L3"), root.keys().asSequence().toSet())
        (1..3).forEach { level ->
            assertEquals(
                setOf("POSTURE", "USAGE_TIME"),
                root
                    .getJSONObject("L$level")
                    .keys()
                    .asSequence()
                    .toSet(),
            )
        }
    }

    @Test
    fun `every combination has at least seven phrases`() {
        combinations.forEach { (level, reason) ->
            assertTrue("L$level $reason has ${phrases(level, reason).size}", phrases(level, reason).size >= 7)
        }
    }

    @Test
    fun `no phrase is over 25 words or empty`() {
        combinations.forEach { (level, reason) ->
            phrases(level, reason).forEach { phrase ->
                val words = phrase.trim().split(Regex("\\s+")).size
                assertTrue("'$phrase' has $words words", phrase.isNotBlank() && words <= 25)
            }
        }
    }

    @Test
    fun `no phrase repeats inside a combination or across them`() {
        val all = combinations.flatMap { (level, reason) -> phrases(level, reason) }

        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `phrases never name an app or carry live numbers they cannot know`() {
        val appNames = listOf("TikTok", "Instagram", "YouTube", "Facebook")
        combinations.flatMap { (level, reason) -> phrases(level, reason) }.forEach { phrase ->
            assertTrue("'$phrase' names an app", appNames.none { phrase.contains(it, ignoreCase = true) })
        }
    }

    @Test
    fun `a round serves every phrase of a key once before repeating`() {
        val fallback = FallbackRoasts(json, Random(7))
        val round = phrases(2, TriggerReason.USAGE_TIME).size

        val served = List(round) { fallback.next(2, TriggerReason.USAGE_TIME) }

        assertEquals(phrases(2, TriggerReason.USAGE_TIME).toSet(), served.toSet())
        assertTrue(fallback.next(2, TriggerReason.USAGE_TIME) in served)
    }

    @Test
    fun `keys are independent`() {
        val fallback = FallbackRoasts(json, Random(7))

        val posture = fallback.next(1, TriggerReason.POSTURE)
        val usage = fallback.next(1, TriggerReason.USAGE_TIME)

        assertTrue(posture in phrases(1, TriggerReason.POSTURE))
        assertTrue(usage in phrases(1, TriggerReason.USAGE_TIME))
    }

    @Test
    fun `the English file has the same structure and limits`() {
        val english = JSONObject(File("src/main/assets/${FallbackRoasts.ENGLISH_ASSET}").readText())

        assertEquals(root.keys().asSequence().toSet(), english.keys().asSequence().toSet())
        combinations.forEach { (level, reason) ->
            val array = english.getJSONObject("L$level").getJSONArray(reason.name)
            assertEquals("L$level $reason", phrases(level, reason).size, array.length())
            repeat(array.length()) {
                val words =
                    array
                        .getString(it)
                        .trim()
                        .split(Regex("""\s+"""))
                        .size
                assertTrue("'${array.getString(it)}' has $words words", words <= 25)
            }
        }
        // It also loads and serves like the Spanish one.
        assertTrue(FallbackRoasts(english.toString(), Random(1)).next(1, TriggerReason.POSTURE).isNotBlank())
    }

    @Test
    fun `the file follows the resolved UI language`() {
        assertEquals(FallbackRoasts.ENGLISH_ASSET, FallbackRoasts.assetNameFor("en"))
        assertEquals(FallbackRoasts.SPANISH_ASSET, FallbackRoasts.assetNameFor("es"))
        assertEquals(FallbackRoasts.SPANISH_ASSET, FallbackRoasts.assetNameFor("fr"))
    }
}
