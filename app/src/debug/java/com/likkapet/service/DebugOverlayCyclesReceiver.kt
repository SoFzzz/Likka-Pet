package com.likkapet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.likkapet.LikkaApplication
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayFeed
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import leakcanary.AppWatcher
import leakcanary.LeakCanary

/**
 * Debug builds only: scenario M6 (RF-O05, RNF-F04). Creates and removes the overlay window
 * [EXTRA_CYCLES] times through a real [OverlayController] fed with a fake Level 1 state, then lets
 * LeakCanary report what is still retained:
 *
 * `adb shell am broadcast -n com.likkapet/.service.DebugOverlayCyclesReceiver --ei cycles 50`
 *
 * Run it with the service running (Likka on), so the process is not frozen as a cached app during
 * the loop.
 */
class DebugOverlayCyclesReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as LikkaApplication
        val cycles = intent.getIntExtra(EXTRA_CYCLES, DEFAULT_CYCLES)
        app.launchInBackground { runCycles(app, cycles) }
    }

    private suspend fun runCycles(
        app: LikkaApplication,
        cycles: Int,
    ) = coroutineScope {
        val feeds = MutableStateFlow(OverlayFeed.INITIAL)
        val controller =
            withContext(Dispatchers.Main) {
                OverlayController(
                    context = OverlayWindow.overlayContext(app),
                    feeds = feeds,
                    planner = app.createOverlayMotionPlanner(),
                    reactions = app.createLocalReactionDisplay(),
                    onSurrenderClick = {},
                    openApp = {},
                    log = { Log.i(TAG, it) },
                )
            }
        val rendering = launch(Dispatchers.Main) { controller.run() }
        repeat(cycles) { cycle ->
            feeds.value = LEVEL_1
            delay(SHOWN_MS)
            feeds.value = OverlayFeed.INITIAL.copy(likkaState = LikkaState.WATCHING)
            delay(GONE_MS)
            Log.i(TAG, "Overlay cycle ${cycle + 1}/$cycles done")
        }
        withContext(Dispatchers.Main) { controller.stop() }
        rendering.cancel()
        // Give the removed views LeakCanary's watch delay, then count and dump what is still retained.
        delay(WATCH_SETTLE_MS)
        Runtime.getRuntime().gc()
        delay(GC_SETTLE_MS)
        Log.i(TAG, "Overlay cycles finished: retained=${AppWatcher.objectWatcher.retainedObjectCount}")
        LeakCanary.dumpHeap()
    }

    private companion object {
        const val TAG = "LikkaOverlayCycles"
        const val EXTRA_CYCLES = "cycles"
        const val DEFAULT_CYCLES = 50
        const val SHOWN_MS = 600L
        const val GONE_MS = 300L
        const val WATCH_SETTLE_MS = 6_000L
        const val GC_SETTLE_MS = 1_000L
        val LEVEL_1 =
            OverlayFeed(
                likkaState = LikkaState.WATCHING,
                overlay =
                    LikkaOverlayState.NOTHING_TO_SHOW.copy(
                        level = 1,
                        reason = TriggerReason.POSTURE,
                        app = TargetApp.TIKTOK,
                    ),
            )
    }
}
