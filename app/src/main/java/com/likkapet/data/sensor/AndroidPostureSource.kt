package com.likkapet.data.sensor

import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.port.PostureSource
import com.likkapet.domain.port.ScreenStateSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * [PostureSource] over the device sensors (documentación §4, RF-P01, RF-P05). While the screen is
 * off the sensors are not registered at all: leaving the screen-on collection cancels the feed, which
 * unregisters its listeners, and turning the screen on starts a fresh pipeline (a window from before
 * the pause would be stale).
 */
class AndroidPostureSource(
    private val feed: MotionSensorFeed,
    private val screenState: ScreenStateSource,
) : PostureSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val readings: Flow<PostureReading> =
        screenState.isScreenOn
            .distinctUntilChanged()
            .flatMapLatest { isScreenOn -> if (isScreenOn) readingsWhileScreenOn() else emptyFlow() }

    private fun readingsWhileScreenOn(): Flow<PostureReading> =
        flow {
            val pipeline = PostureSamplePipeline(feed.hasGravity)
            emitAll(feed.samples().mapNotNull(pipeline::onSample))
        }
}
