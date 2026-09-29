package com.likkapet.domain.port

import com.likkapet.domain.model.ForegroundState
import kotlinx.coroutines.flow.Flow

/** The watched app on screen and the call state, polled about every 2 s (RF-A01, RF-A03). */
interface ForegroundAppSource {
    /**
     * Emits on every poll, even when nothing changed, because the poll cadence is also what
     * measures usage time. With the screen off it keeps emitting the last known app and the live
     * call state (a call must freeze everything even then); the screen itself is reported only through
     * [ScreenStateSource], never as "left the app" (documentación §3.3). Collect it once: each
     * collector starts its own poll loop.
     */
    val states: Flow<ForegroundState>
}
