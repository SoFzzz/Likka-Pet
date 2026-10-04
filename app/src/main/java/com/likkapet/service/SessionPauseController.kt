package com.likkapet.service

import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.port.PauseController
import com.likkapet.domain.port.StatsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [PauseController] that sends every request to the running [MonitoringSession], whose coordinator
 * applies rule E. With no session (service off) the stored state decides, through the same
 * `PauseRules` inside the store.
 */
class SessionPauseController(
    private val store: StatsStore,
) : PauseController {
    private val session = MutableStateFlow<MonitoringSession?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val isLevel3Active: Flow<Boolean> =
        session
            .flatMapLatest { it?.overlayState?.map { overlay -> overlay.level == ReasonLevel.LEVEL_3.value } ?: flowOf(false) }
            .distinctUntilChanged()

    /** The service attaches its session when it starts and detaches it when it ends. */
    fun attach(running: MonitoringSession) {
        session.value = running
    }

    fun detach(ended: MonitoringSession) {
        session.compareAndSet(ended, null)
    }

    override suspend fun requestPause(minutes: Int): PauseResult =
        session.value?.requestPause(minutes) ?: store.requestPause(minutes, isLevel3Active = false)

    override suspend fun resume() {
        val running = session.value
        if (running != null) running.resume() else store.resume()
    }
}
