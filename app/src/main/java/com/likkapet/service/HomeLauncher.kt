package com.likkapet.service

import android.content.Context
import android.content.Intent

/**
 * Sends the user to the launcher (documentación §9.2). Call [launch] BEFORE removing the Level 3
 * overlay (RF-O03): the app may start the activity because the overlay is still visible, which
 * Android 15's background-activity restriction would otherwise block. Used by [OverlayController].
 */
class HomeLauncher(
    private val context: Context,
) {
    fun launch() = context.startActivity(createIntent())

    companion object {
        /** NEW_TASK is required because [launch] runs from a service context, not an activity. */
        const val ACTION = Intent.ACTION_MAIN
        const val CATEGORY = Intent.CATEGORY_HOME
        const val FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK

        fun createIntent(): Intent =
            Intent(ACTION).apply {
                addCategory(CATEGORY)
                flags = FLAGS
            }
    }
}
