package dev.yahaveliyahu.calendar

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"
        const val ACTION_DISMISS = "dev.yahaveliyahu.calendar.action.DISMISS_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 0)
        val notificationId = eventId.hashCode() + minutesBefore

        if (intent.action == ACTION_DISMISS) {
            NotificationManagerCompat.from(context).cancel(notificationId)
            return
        }

        val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val subtitle = when {
            minutesBefore == 0 -> "Starting now"
            minutesBefore < 60 -> "In $minutesBefore minutes"
            minutesBefore < 1440 -> "In ${minutesBefore / 60} hour(s)"
            else -> "In ${minutesBefore / 1440} day(s)"
        }

        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, notificationId, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "כבה", dismissPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}