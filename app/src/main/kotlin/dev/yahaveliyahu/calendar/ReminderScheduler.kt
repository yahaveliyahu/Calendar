package dev.yahaveliyahu.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.yahaveliyahu.calendar_core.CalendarEvent

/**
 * Schedules/cancels reminder notifications for a CalendarEvent's reminderMinutesBefore list.
 * Uses AlarmManager.set() (inexact) rather than setExactAndAllowWhileIdle() -- exact alarms
 * require the special SCHEDULE_EXACT_ALARM permission on Android 12+, which needs its own
 * "send the user to Settings" flow. A reminder firing within a few minutes of the requested
 * time is an acceptable trade for not needing that extra permission dance.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "calendar_event_reminders"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for calendar events you've added"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun scheduleAll(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        event.reminderMinutesBefore.forEach { minutesBefore ->
            val triggerAtMillis = event.start.toEpochMilli() - minutesBefore * 60_000L
            if (triggerAtMillis <= System.currentTimeMillis()) return@forEach

            val pendingIntent = reminderPendingIntent(context, event, minutesBefore)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: SecurityException) {
                // Some OEMs restrict even inexact alarms without additional battery-optimization
                // exemptions; failing silently here is preferable to crashing the whole app.
            }
        }
    }

    fun cancelAll(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        event.reminderMinutesBefore.forEach { minutesBefore ->
            alarmManager.cancel(reminderPendingIntent(context, event, minutesBefore))
        }
    }

    private fun reminderPendingIntent(context: Context, event: CalendarEvent, minutesBefore: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ReminderReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(ReminderReceiver.EXTRA_MINUTES_BEFORE, minutesBefore)
        }
        val requestCode = "${event.id}_$minutesBefore".hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}