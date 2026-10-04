package com.likkapet.data.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.likkapet.domain.port.ScreenStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * [ScreenStateSource] with a dynamic receiver for ACTION_SCREEN_OFF / ACTION_SCREEN_ON (§4.3;
 * these two cannot be declared in the manifest). Every collector (posture, foreground poller,
 * coordinator, stats) shares one receiver, registered while at least one of them collects and
 * released with the last one (RF-P05); a collector that arrives later still gets the current state.
 */
class BroadcastScreenStateSource(
    private val context: Context,
    scope: CoroutineScope,
) : ScreenStateSource {
    override val isScreenOn: Flow<Boolean> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        when (intent.action) {
                            Intent.ACTION_SCREEN_ON -> trySend(true)
                            Intent.ACTION_SCREEN_OFF -> trySend(false)
                        }
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            trySend(context.getSystemService(PowerManager::class.java).isInteractive)
            awaitClose { context.unregisterReceiver(receiver) }
        }.distinctUntilChanged()
            // The replay is dropped with the last collector so a new one never starts from a stale state.
            .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
}
