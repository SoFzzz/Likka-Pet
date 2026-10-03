package com.likkapet.data.usage

import android.media.AudioManager

/** Whether a call (cellular, or VoIP such as WhatsApp or Meet) is ringing or going on. */
fun interface CallStateReader {
    fun isInCall(): Boolean
}

/** Call detection with `AudioManager.getMode()` (documentación §4.2); it needs no permission. */
class AudioManagerCallStateReader(
    private val audioManager: AudioManager,
) : CallStateReader {
    override fun isInCall(): Boolean = audioManager.mode in CALL_MODES

    private companion object {
        val CALL_MODES = setOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)
    }
}
