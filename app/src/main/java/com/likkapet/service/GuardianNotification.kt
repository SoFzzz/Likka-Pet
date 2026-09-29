package com.likkapet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.likkapet.R
import com.likkapet.domain.EscalationConfig
import com.likkapet.presentation.MainActivity

/** Persistent notification of the foreground service (design system §1.8, RF-O08, RF-A06). */
object GuardianNotification {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "likka_guardian_channel"
    private const val OPEN_APP_REQUEST_CODE = 0
    private const val PAUSE_REQUEST_CODE = 1

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

    fun build(
        context: Context,
        state: GuardianNotificationState,
    ): Notification {
        val openApp = openAppIntent(context)
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_antlers)
                .setContentTitle(context.getString(titleFor(state.mode)))
                .setContentIntent(openApp)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                // API 31+ may otherwise delay showing a foreground-service notification by up to 10 s.
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
        textFor(state.mode)?.let { builder.setContentText(context.getString(it)) }
        if (state.canPause) builder.addAction(0, pauseLabel(context), pauseIntent(context))
        builder.addAction(0, context.getString(R.string.notification_action_open), openApp)
        return builder.build()
    }

    private fun titleFor(mode: GuardianMode): Int =
        when (mode) {
            GuardianMode.PROTECTING -> R.string.notification_title
            GuardianMode.PAUSED -> R.string.notification_title_paused
            GuardianMode.NEEDS_PERMISSION -> R.string.notification_title_needs_permission
        }

    private fun textFor(mode: GuardianMode): Int? =
        when (mode) {
            GuardianMode.PROTECTING -> null
            GuardianMode.PAUSED -> R.string.notification_text_paused
            GuardianMode.NEEDS_PERMISSION -> R.string.notification_text_needs_permission
        }

    private fun pauseLabel(context: Context) =
        context.getString(R.string.notification_action_pause, EscalationConfig.NOTIFICATION_PAUSE_MIN)

    // MainActivity is singleTask, so this brings the existing instance forward instead of stacking another.
    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    // The tap reaches the service that is already in the foreground, so it needs no activity and no background start.
    private fun pauseIntent(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            PAUSE_REQUEST_CODE,
            Intent(context, LikkaService::class.java).setAction(LikkaService.ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
