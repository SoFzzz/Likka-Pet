package com.likkapet.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.likkapet.domain.EscalationConfig

/**
 * The vibration of each level (RF-O02): one pulse at Level 2, two at Level 3, played once when the
 * level first shows; coming back from hidden at the same level does not vibrate again.
 */
class OverlayAlerts(
    context: Context,
    private val log: (String) -> Unit,
) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    private var alertedLevel = NO_LEVEL

    /** A level is on screen: vibrates if it is higher than the last one alerted. Returns whether it did. */
    fun onLevelShown(level: Int): Boolean {
        val pattern = patternFor(level).takeIf { level > alertedLevel }
        alertedLevel = level
        pattern?.let(::vibrate)
        return pattern != null
    }

    /** The overlay is gone (resolved, ejected): the next level vibrates again. */
    fun reset() {
        alertedLevel = NO_LEVEL
    }

    private fun vibrate(timings: LongArray) {
        log("Vibration: ${timings.joinToString(prefix = "[", postfix = "]")} ms")
        vibrator.vibrate(VibrationEffect.createWaveform(timings, NO_REPEAT))
    }

    // Waveform timings: wait, vibrate, wait, vibrate...
    private fun patternFor(level: Int): LongArray? =
        when (level) {
            2 -> {
                longArrayOf(0L, EscalationConfig.LEVEL_2_VIBRATION_MS)
            }

            3 -> {
                longArrayOf(
                    0L,
                    EscalationConfig.LEVEL_3_VIBRATION_PULSE_MS,
                    EscalationConfig.LEVEL_3_VIBRATION_GAP_MS,
                    EscalationConfig.LEVEL_3_VIBRATION_PULSE_MS,
                )
            }

            else -> {
                null
            }
        }

    private companion object {
        const val NO_LEVEL = 0
        const val NO_REPEAT = -1
    }
}
