package com.likkapet.data.roast

import com.likkapet.data.remote.LikkaApi
import com.likkapet.data.remote.RoastApiResult
import com.likkapet.data.remote.RoastRequest
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.RoastProjection
import com.likkapet.domain.model.AiServiceStatus
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.RoastGenerator
import com.likkapet.domain.port.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * [RoastGenerator] backed by the Cloudflare Worker (documentación §6 Módulo 4, §7).
 *
 * Roasts are requested ahead of time, with the projected values of [RoastProjection], into a pool of
 * [EscalationConfig.ROAST_POOL_SIZE] per key, so showing one never waits for the network; an empty
 * pool answers with a local phrase at once. What each Worker answer causes (RF-I05):
 * - 200: into the pool.
 * - Network failure, 5xx, 401 or anything else: local phrase now, try again at the next prefetch.
 * - 429: no requests for [EscalationConfig.RATE_LIMIT_BACKOFF_MIN] minutes.
 * - 402 no_credit: no requests until the next local midnight.
 *
 * With AI off, or when the build has no Worker configured, no request is ever made (RF-I09). The log
 * callback only ever gets the pool key, never a roast or the Worker's address.
 */
class WorkerRoastGenerator(
    private val api: LikkaApi,
    private val fallback: FallbackRoasts,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val wallClock: WallClock,
    private val zone: () -> ZoneId,
    private val isAiEnabled: suspend () -> Boolean,
    private val log: (String) -> Unit = {},
    private val currentLanguage: suspend () -> String = { "es" },
    private val fallbackForLanguage: ((String) -> FallbackRoasts)? = null,
) : RoastGenerator {
    private val lock = Any()
    private val pool = RoastPool()
    private val requestsInFlight = mutableSetOf<RoastKey>()
    private var rateLimitedUntilMs: Long? = null
    private var noCreditUntilEpochMs: Long? = null

    private val mutableStatus = MutableStateFlow(AiServiceStatus())
    override val serviceStatus: StateFlow<AiServiceStatus> = mutableStatus.asStateFlow()

    @OptIn(DelicateCoroutinesApi::class)
    override fun prefetch(
        app: TargetApp,
        level: Int,
        reason: TriggerReason,
    ) {
        val key = RoastKey(app, level, reason)
        if (!reserveRequest(key)) return
        // ATOMIC: the finally below must run even if the scope is cancelled before the body starts.
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                fetchIntoPool(key)
            } finally {
                synchronized(lock) { requestsInFlight.remove(key) }
            }
        }
    }

    override suspend fun nextRoast(
        app: TargetApp,
        level: Int,
        reason: TriggerReason,
    ): String {
        val pooled = if (isAiEnabled()) synchronized(lock) { pool.take(RoastKey(app, level, reason)) } else null
        // The pool went down by one: ask for a replacement in the background (RF-I06).
        prefetch(app, level, reason)
        val activeFallback = fallbackForLanguage?.invoke(currentLanguage()) ?: fallback
        return pooled ?: activeFallback.next(level, reason)
    }

    override fun clearPool() {
        synchronized(lock) {
            pool.clear()
        }
    }

    // One request at a time per key, and none when its pool is already full.
    private fun reserveRequest(key: RoastKey): Boolean =
        synchronized(lock) { pool.size(key) < EscalationConfig.ROAST_POOL_SIZE && requestsInFlight.add(key) }

    private suspend fun fetchIntoPool(key: RoastKey) {
        if (!isAiEnabled() || !api.isConfigured || isBlocked()) return
        log("Roast request ${key.id}")
        val result = api.requestRoast(key.toRequest())
        log("Roast request ${key.id} -> ${result.label()}")
        when (result) {
            is RoastApiResult.Success -> onRoastReceived(key, result.roast)
            RoastApiResult.NoCredit -> onNoCredit()
            RoastApiResult.RateLimited -> onRateLimited()
            RoastApiResult.NetworkError -> mutableStatus.update { it.copy(isOnline = false) }
            is RoastApiResult.Rejected -> mutableStatus.update { it.copy(isOnline = true) }
        }
    }

    // Explicit names: class names are obfuscated in release builds, which would make the log useless.
    private fun RoastApiResult.label(): String =
        when (this) {
            is RoastApiResult.Success -> "Success"
            RoastApiResult.NoCredit -> "NoCredit"
            RoastApiResult.RateLimited -> "RateLimited"
            RoastApiResult.NetworkError -> "NetworkError"
            is RoastApiResult.Rejected -> "Rejected($status)"
        }

    private suspend fun RoastKey.toRequest() =
        RoastRequest(
            app = app,
            minutes = RoastProjection.minutes(level, reason),
            angle = RoastProjection.angle(reason),
            level = level,
            reason = reason,
            lang = currentLanguage(),
        )

    private fun isBlocked(): Boolean =
        synchronized(lock) {
            val rateLimitEnd = rateLimitedUntilMs
            if (rateLimitEnd != null && clock.nowMillis() < rateLimitEnd) return true
            val noCreditEnd = noCreditUntilEpochMs
            if (noCreditEnd != null) {
                if (wallClock.nowEpochMillis() < noCreditEnd) return true
                noCreditUntilEpochMs = null
                mutableStatus.update { it.copy(hasCredit = true) }
            }
            false
        }

    // The app checks the length again even though the Worker already trims (RF-I03).
    private fun onRoastReceived(
        key: RoastKey,
        roast: String,
    ) {
        mutableStatus.value = AiServiceStatus(isOnline = true, hasCredit = true)
        val text = roast.trim()
        if (text.isEmpty() || wordCount(text) > EscalationConfig.ROAST_MAX_WORDS) return
        synchronized(lock) { pool.add(key, text) }
    }

    private fun onRateLimited() {
        synchronized(lock) {
            rateLimitedUntilMs = clock.nowMillis() + TimeUnit.MINUTES.toMillis(EscalationConfig.RATE_LIMIT_BACKOFF_MIN.toLong())
        }
        mutableStatus.update { it.copy(isOnline = true) }
    }

    private fun onNoCredit() {
        synchronized(lock) { noCreditUntilEpochMs = startOfNextDayEpochMillis() }
        mutableStatus.value = AiServiceStatus(isOnline = true, hasCredit = false)
    }

    private fun startOfNextDayEpochMillis(): Long {
        val zoneNow = zone()
        val today = Instant.ofEpochMilli(wallClock.nowEpochMillis()).atZone(zoneNow).toLocalDate()
        return today
            .plusDays(1)
            .atStartOfDay(zoneNow)
            .toInstant()
            .toEpochMilli()
    }

    private fun wordCount(text: String): Int = text.split(WHITESPACE).count { it.isNotEmpty() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
