package com.likkapet.data.roast

import com.likkapet.data.remote.LikkaApi
import com.likkapet.data.remote.RoastApiResult
import com.likkapet.data.remote.RoastRequest
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.AiServiceStatus
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.WallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** RF-I05, RF-I06, RF-I09, RF-I10 and RF-I11 with a scripted Worker and virtual clocks. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkerRoastGeneratorTest {
    private class ScriptedApi : LikkaApi {
        var configured = true
        val requests = mutableListOf<RoastRequest>()
        private val answers = ArrayDeque<RoastApiResult>()
        var default: RoastApiResult = RoastApiResult.Success("roast de prueba")
        private var counter = 0

        override val isConfigured: Boolean get() = configured

        fun answerNext(result: RoastApiResult) = answers.addLast(result)

        /** Every call gets a different roast, like the real model with temperature 1.3. */
        var freshRoasts = false

        override suspend fun requestRoast(request: RoastRequest): RoastApiResult {
            requests += request
            counter++
            return answers.removeFirstOrNull() ?: if (freshRoasts) RoastApiResult.Success("roast $counter") else default
        }
    }

    private val api = ScriptedApi()
    private var monotonicMs = 0L
    private var wallMs =
        LocalDate
            .of(2026, 10, 5)
            .atTime(15, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    private var aiEnabled = true
    private val logged = mutableListOf<String>()

    private val fallback = FallbackRoasts(FALLBACK_JSON, Random(1))

    private fun TestScope.generator() =
        WorkerRoastGenerator(
            api = api,
            fallback = fallback,
            scope = backgroundScope,
            clock = Clock { monotonicMs },
            wallClock = WallClock { wallMs },
            zone = { ZoneOffset.UTC },
            isAiEnabled = { aiEnabled },
            log = { logged += it },
        )

    private fun TestScope.prefetch(
        generator: WorkerRoastGenerator,
        app: TargetApp = TargetApp.TIKTOK,
        level: Int = 1,
        reason: TriggerReason = TriggerReason.POSTURE,
    ) {
        generator.prefetch(app, level, reason)
        runCurrent()
    }

    private fun isFallback(roast: String) = roast.startsWith("local ")

    @Test
    fun `a 200 goes to the pool and is served once`() =
        runTest {
            val generator = generator()
            prefetch(generator)

            val first = generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)
            val second = generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)

            assertEquals("roast de prueba", first)
            assertTrue("the pooled roast is not served again: $second", isFallback(second))
        }

    @Test
    fun `an empty pool answers with a local roast at once and never waits for the network`() =
        runTest {
            val generator = generator()

            val roast = generator.nextRoast(TargetApp.TIKTOK, 2, TriggerReason.USAGE_TIME)

            assertTrue(isFallback(roast))
        }

    @Test
    fun `the pool holds at most three roasts per key`() =
        runTest {
            api.freshRoasts = true
            val generator = generator()

            repeat(6) { prefetch(generator) }

            assertEquals(EscalationConfig.ROAST_POOL_SIZE, api.requests.size)
        }

    @Test
    fun `using a pooled roast asks for a replacement in the background`() =
        runTest {
            api.freshRoasts = true
            val generator = generator()
            repeat(3) { prefetch(generator) }
            assertEquals(3, api.requests.size)

            generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)
            runCurrent()

            assertEquals(4, api.requests.size)
        }

    @Test
    fun `a roast of the pool never shows twice for a key`() =
        runTest {
            api.freshRoasts = true
            val generator = generator()
            repeat(3) { prefetch(generator) }

            val served = List(3) { generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE) }

            assertEquals(3, served.toSet().size)
            assertTrue(served.none(::isFallback))
        }

    @Test
    fun `the key includes the reason, so each track gets its own roasts`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.Success("de postura"))
            prefetch(generator, reason = TriggerReason.POSTURE)
            api.answerNext(RoastApiResult.Success("de uso"))
            prefetch(generator, reason = TriggerReason.USAGE_TIME)

            assertEquals("de uso", generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.USAGE_TIME))
            assertEquals("de postura", generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE))
        }

    @Test
    fun `opening a watched app prefetches level 1 for both reasons`() =
        runTest {
            val generator = generator()

            TriggerReason.entries.forEach { generator.prefetch(TargetApp.TIKTOK, 1, it) }
            runCurrent()

            assertEquals(
                setOf("TIKTOK_L1_POSTURE", "TIKTOK_L1_USAGE_TIME"),
                api.requests.map { "${it.app}_L${it.level}_${it.reason}" }.toSet(),
            )
        }

    @Test
    fun `added apps share the OTHER keys and send app OTHER`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.Success("para otra app"))
            prefetch(generator, app = TargetApp.OTHER)

            assertEquals(TargetApp.OTHER, api.requests.single().app)
            assertEquals("para otra app", generator.nextRoast(TargetApp.OTHER, 1, TriggerReason.POSTURE))
            assertTrue(logged.any { it.contains("OTHER_L1_POSTURE") })
        }

    @Test
    fun `a prefetch carries the projected values, never a live reading`() =
        runTest {
            val generator = generator()

            prefetch(generator, level = 1, reason = TriggerReason.USAGE_TIME)
            prefetch(generator, level = 2, reason = TriggerReason.USAGE_TIME)
            prefetch(generator, level = 3, reason = TriggerReason.USAGE_TIME)
            prefetch(generator, level = 1, reason = TriggerReason.POSTURE)

            val usage = api.requests.filter { it.reason == TriggerReason.USAGE_TIME }
            assertEquals(
                listOf(
                    EscalationConfig.USAGE_THRESHOLD_MIN,
                    EscalationConfig.USAGE_THRESHOLD_MIN + EscalationConfig.LEVEL_1_TO_2_MIN,
                    EscalationConfig.USAGE_THRESHOLD_MIN + EscalationConfig.LEVEL_1_TO_2_MIN + EscalationConfig.LEVEL_2_TO_3_MIN,
                ),
                usage.map { it.minutes },
            )
            val posture = api.requests.single { it.reason == TriggerReason.POSTURE }
            assertEquals(EscalationConfig.POSTURE_DANGER_ANGLE.toInt(), posture.angle)
        }

    @Test
    fun `with AI off there is zero network and only local roasts, even if the pool has some`() =
        runTest {
            val generator = generator()
            prefetch(generator)
            assertEquals(1, api.requests.size)

            aiEnabled = false
            repeat(5) { prefetch(generator, level = 2) }
            val roast = generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)
            runCurrent()

            assertEquals(1, api.requests.size)
            assertTrue("pooled roast ignored with AI off: $roast", isFallback(roast))
        }

    @Test
    fun `a build without a Worker makes no request`() =
        runTest {
            api.configured = false
            val generator = generator()

            prefetch(generator)

            assertTrue(api.requests.isEmpty())
            assertTrue(isFallback(generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)))
        }

    @Test
    fun `a network error falls back, marks the phone offline, and the next prefetch retries`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.NetworkError)
            prefetch(generator)

            assertEquals(AiServiceStatus(isOnline = false, hasCredit = true), generator.serviceStatus.value)
            assertTrue(isFallback(generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)))

            prefetch(generator)
            assertEquals(AiServiceStatus(isOnline = true, hasCredit = true), generator.serviceStatus.value)
            assertEquals("roast de prueba", generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE))
        }

    @Test
    fun `a 5xx falls back but does not mark the phone offline, and the next prefetch retries`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.Rejected(502))
            prefetch(generator)

            assertTrue(generator.serviceStatus.value.isOnline)
            prefetch(generator)
            assertEquals(2, api.requests.size)
        }

    @Test
    fun `a 401 falls back and does not block the day`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.Rejected(401))
            prefetch(generator)

            prefetch(generator)

            assertEquals(2, api.requests.size)
            assertEquals(AiServiceStatus(), generator.serviceStatus.value)
        }

    @Test
    fun `a 429 stops requests for ten minutes`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.RateLimited)
            prefetch(generator)

            monotonicMs += TimeUnit.MINUTES.toMillis(EscalationConfig.RATE_LIMIT_BACKOFF_MIN.toLong()) - 1
            prefetch(generator)
            assertEquals("still inside the back-off", 1, api.requests.size)

            monotonicMs += 1
            prefetch(generator)
            assertEquals(2, api.requests.size)
        }

    @Test
    fun `no credit stops requests until the next day and shows no credit meanwhile`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.NoCredit)
            prefetch(generator)
            assertEquals(AiServiceStatus(isOnline = true, hasCredit = false), generator.serviceStatus.value)

            wallMs =
                LocalDate
                    .of(2026, 10, 5)
                    .atTime(23, 59)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            prefetch(generator)
            assertEquals("still the same day", 1, api.requests.size)

            wallMs =
                LocalDate
                    .of(2026, 10, 6)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            prefetch(generator)
            assertEquals(2, api.requests.size)
            assertEquals(AiServiceStatus(), generator.serviceStatus.value)
        }

    @Test
    fun `no credit uses local roasts, never an error`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.NoCredit)
            prefetch(generator)

            assertTrue(isFallback(generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)))
        }

    @Test
    fun `a roast over 25 words or an empty one is not pooled, one of exactly 25 is`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.Success(List(26) { "palabra" }.joinToString(" ")))
            prefetch(generator)
            api.answerNext(RoastApiResult.Success("   "))
            prefetch(generator)

            assertTrue(isFallback(generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)))

            api.answerNext(RoastApiResult.Success(List(25) { "palabra" }.joinToString(" ")))
            prefetch(generator)
            assertFalse(isFallback(generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)))
        }

    @Test
    fun `one request at a time per key`() =
        runTest {
            val generator = generator()

            // Three prefetches before the first one has had a chance to answer.
            repeat(3) { generator.prefetch(TargetApp.TIKTOK, 1, TriggerReason.POSTURE) }
            runCurrent()

            assertEquals(1, api.requests.size)
        }

    @Test
    fun `the log never carries a roast`() =
        runTest {
            val generator = generator()
            api.answerNext(RoastApiResult.Success("contenido secreto del roast"))
            prefetch(generator)
            generator.nextRoast(TargetApp.TIKTOK, 1, TriggerReason.POSTURE)

            assertTrue(logged.isNotEmpty())
            assertTrue(logged.none { it.contains("secreto") })
        }

    @Test
    fun `local roasts do not repeat until all of a key were used`() =
        runTest {
            val generator = generator()

            val served = List(3) { generator.nextRoast(TargetApp.TIKTOK, 3, TriggerReason.POSTURE) }

            assertEquals(3, served.toSet().size)
            assertNotEquals(served[0], served[1])
            // The fake file has 3 phrases for every key, so the fourth starts a new round.
            assertTrue(isFallback(generator.nextRoast(TargetApp.TIKTOK, 3, TriggerReason.POSTURE)))
        }

    private companion object {
        val FALLBACK_JSON =
            buildString {
                append("{")
                append(
                    (1..3).joinToString(",") { level ->
                        val byReason =
                            TriggerReason.entries.joinToString(",") { reason ->
                                "\"${reason.name}\":[" + (1..3).joinToString(",") { "\"local L$level ${reason.name} $it\"" } + "]"
                            }
                        "\"L$level\":{$byReason}"
                    },
                )
                append("}")
            }
    }
}
