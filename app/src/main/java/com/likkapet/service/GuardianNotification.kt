package com.likkapet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.likkapet.R
import com.likkapet.presentation.MainActivity

/** Persistent notification of the foreground service (design system §1.8). */
object GuardianNotification {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "likka_guardian_channel"
    private const val OPEN_APP_REQUEST_CODE = 0

    // Temporary system icon until the monochrome antlers vector exists (design system §1.5).
    private const val SMALL_ICON = android.R.drawable.ic_dialog_info

    /** Low importance and no sound (documentación §9.3); creating an existing channel is a no-op. */
    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(null, null)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // API 31+ may otherwise delay showing a foreground-service notification by up to 10 s.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            // Reuses the open MainActivity instead of stacking a second one.
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
