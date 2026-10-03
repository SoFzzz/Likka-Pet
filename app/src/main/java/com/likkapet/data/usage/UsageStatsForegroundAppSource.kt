package com.likkapet.data.usage

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.WatchedApps
import com.likkapet.domain.model.ForegroundState
import com.likkapet.domain.port.ForegroundAppSource
import com.likkapet.domain.port.ScreenStateSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

/**
 * [ForegroundAppSource] polling UsageStats every [EscalationConfig.FOREGROUND_POLL_SEC] seconds
 * (RF-A01) and the call state with AudioManager (RF-A03).
 *
 * The foreground package becomes a [com.likkapet.domain.model.TargetApp] through [WatchedApps]: a
 * default app keeps its own value and any package the user added (RF-S06) is `OTHER`; a package that
 * is not watched is null. [watchedPackages] is read live, so switching an app off takes effect on
 * the next emission. With the screen off UsageStats is not queried (the last known app stays) but the
 * call state still is: a call with the screen off must freeze the coordinator all the same (§3.3).
 */
class UsageStatsForegroundAppSource(
    private val tracker: ForegroundPackageTracker,
    private val callStateReader: CallStateReader,
    private val screenState: ScreenStateSource,
    private val watchedPackages: Flow<Set<String>>,
    private val pollDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ForegroundAppSource {
    override val states: Flow<ForegroundState> =
        combine(observations(), watchedPackages) { observation, watched ->
            ForegroundState(
                app = observation.packageName?.let { WatchedApps.targetAppFor(it, watched) },
                isInCall = observation.isInCall,
            )
        }

    private class Observation(
        val packageName: String?,
        val isInCall: Boolean,
    )

    // A screen change restarts the loop, so the first poll after turning the screen on is immediate.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observations(): Flow<Observation> =
        screenState.isScreenOn
            .distinctUntilChanged()
            .flatMapLatest { isScreenOn -> pollLoop(isScreenOn) }
            .flowOn(pollDispatcher)

    private fun pollLoop(isScreenOn: Boolean): Flow<Observation> =
        flow {
            while (true) {
                emit(observe(isScreenOn))
                delay(POLL_INTERVAL_MS)
            }
        }

    private fun observe(isScreenOn: Boolean): Observation {
        val packageName = if (isScreenOn) tracker.poll() else tracker.currentPackage
        // The call screen does not always produce a usage event, so a known dialer also counts (§4.2).
        val isInCall = callStateReader.isInCall() || packageName in DIALER_PACKAGES
        return Observation(packageName, isInCall)
    }

    private companion object {
        val POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(EscalationConfig.FOREGROUND_POLL_SEC.toLong())
        val DIALER_PACKAGES = setOf("com.android.incallui", "com.google.android.dialer", "com.android.dialer")
    }
}
